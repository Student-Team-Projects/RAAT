/*
 * Copyright (c) 2025  Viktar Dubovik.
 * Copyright (c) 2025  Bogdan Tolstik.
 * Copyright (c) 2025  Daniil Zabauski.
 * Copyright (c) 2025  Dzmitry Maslionchanka.
 * Copyright (c) 2023  Hubert Zięba.
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.tcs.raat.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.tcs.raat.model.ServerProfile
import com.tcs.raat.ui.vnc.FrameScroller
import com.tcs.raat.ui.vnc.FrameState
import com.tcs.raat.ui.vnc.FrameView
import com.tcs.raat.vnc.Messenger
import com.tcs.raat.vnc.UserCredential
import com.tcs.raat.vnc.VncClient
import kotlinx.coroutines.*
import java.io.IOException
import java.lang.ref.WeakReference
import com.tcs.raat.ui.home.getSessionStatus

/**
 * ViewModel for VncActivity
 *
 * Connection
 * ==========
 *
 * At construction, we instantiate a [VncClient] referenced by [client]. Then
 * activity starts the connection by calling [initConnection] which starts a coroutine to
 * handle connection setup.
 *
 * After successful connection, we continue to operate normally until the remote
 * server closes the connection, or an error occurs. Once disconnected, we
 * wait for the activity to finish and then cleanup any acquired resources.
 *
 * Currently, lifecycle of [client] is tied to this view model. So one [VncViewModel]
 * manages only one [VncClient].
 *
 *
 * Threading
 * =========
 *
 * Receiver thread :- This thread is started (as a coroutine) in [launchConnection].
 * It handles the protocol initialization, and after that processes incoming messages.
 * Most of the callbacks of [VncClient.Observer] are invoked on this thread. In most
 * cases it is stopped when activity is finished and this view model is cleaned up.
 *
 * Sender thread :- This thread is created (as an executor) by [messenger]. It is
 * used to send messages to remote server. We use this dedicated thread instead
 * of coroutines to preserve the order of sent messages.
 *
 * UI thread :- Main thread of the app. Used for updating UI and controlling other
 * Threads. This is where [frameState] is updated.
 *
 * Renderer thread :- This is managed by [FrameView] and used for rendering frame
 * via OpenGL ES. [frameState] is read from this thread to decide how/where frame
 * should be drawn.
 */
class VncViewModel(app: Application) : BaseViewModel(app), VncClient.Observer {

    /**
     * Connection lifecycle:
     *
     *            Created
     *               |
     *               v
     *          Connecting ----------+
     *               |               |
     *               v               |
     *           Connected           |
     *               |               |
     *               v               |
     *          Disconnected <-------+
     *
     */
    enum class State {
        Created,
        Connecting,
        Connected,
        Disconnected,
    }

    val client = VncClient(this)

    /**
     * [ServerProfile] used for current connection.
     */
    var profile = ServerProfile(); private set

    /**
     * We have two places for connection state (both are synced):
     *
     * [VncClient.connected] - Simple boolean state, used most of the time
     * [state]               - More granular, used by observers & data binding
     */
    val state = MutableLiveData(State.Created)

    /**
     * Reason for disconnecting.
     */
    val disconnectReason = MutableLiveData("")

    /**
     * Fired when [VncClient] has asked for credential. It is used to
     * show Credentials dialog to user and return the result to receiver
     * thread.
     *
     * Value of this request is true if username & password are required
     * and false if only password is required.
     */
    val credentialRequest = LiveRequest<Boolean, UserCredential>(UserCredential(), viewModelScope)

    /**
     * Fired to unlock saved servers.
     */
    val serverUnlockRequest = LiveRequest<Any?, Boolean>(false, viewModelScope)

    /**
     * List of known credentials. Used for providing suggestion when
     * new credentials are required.
     */
    val knownCredentials by lazy { serverProfileDao.getCredentials() }

    /**
     * Holds a weak reference to [FrameView] instance.
     *
     * This is used to tell [FrameView] to re-render its content when VncClient's
     * framebuffer is updated. Instead of using LiveData/LiveEvent, we keep a
     * weak reference because:
     *
     *      1. It avoids a context-switch to UI thread. Rendering request to
     *         a GlSurfaceView can be sent from any thread.
     *
     *      2. We don't have to invoke the whole ViewModel machinery just for
     *         a single call to FrameView.
     */
    var frameViewRef = WeakReference<FrameView>(null)

    /**
     * Holds information about scaling, translation etc.
     */
    val frameState = with(pref.viewer) { FrameState(zoomMin, zoomMax, perOrientationZoom) }

    /**
     * Used for scrolling/animating the frame.
     */
    val frameScroller = FrameScroller(this)

    /**
     * Used for sending events to remote server.
     */
    val messenger = Messenger(client)

    private val sshTunnel = SshTunnel(this)

    /**
     * Used to confirm unknown hosts.
     */
    val sshHostKeyVerifyRequest = LiveRequest<HostKey, Boolean>(false, viewModelScope)


    /**************************************************************************
     * Connection management
     **************************************************************************/

    /**
     * Initialize VNC connection using given profile.
     * It may be called multiple times due to activity restarts.
     */
    fun initConnection(profile: ServerProfile) {
        if (state.value == State.Created) {
            state.value = State.Connecting
            this.profile = profile
            frameState.setZoom(profile.zoom1, profile.zoom2)
            launchConnection()
        }
    }

    private fun launchConnection() {
        viewModelScope.launch(Dispatchers.IO) {

            runCatching {

                startServer()
                delay(2000)
                preConnect()
                connect()
                processMessages()

            }.onFailure {
                disconnectReason.postValue(it.message)
                Log.e("ReceiverCoroutine", "Connection failed", it)
            }

            state.postValue(State.Disconnected)

            //Wait until activity is finished and viewmodel is cleaned up.
            runCatching { awaitCancellation() }
            cleanup()
        }
    }

    private suspend fun startServer() {
        val jsch = JSch()
        val session = jsch.getSession(profile.sshUsername, profile.sshHost, profile.sshPort)

        session.setPassword(profile.sshPassword)
        session.setConfig("StrictHostKeyChecking", "no")
        session.timeout = 12000

        session.connect()

        val channel = session.openChannel("exec") as ChannelExec
        val port = if (profile.port <= 5900) profile.port+5900 else profile.port
        Log.d("StartRaatServer", "raat-server-request open-session --vnc_password=${profile.password} --rfb_port=$port --geometry=${profile.geometry} --de_choice=${profile.desktopEnv}")

        channel.setCommand("raat-server-request open-session --vnc_password=${profile.password} --rfb_port=$port --geometry=${profile.geometry} --de_choice=${profile.desktopEnv}")
        // debug
        val inputStream = channel.inputStream

        channel.connect()
        delay(2000)
        // Read server response
        val output = inputStream.bufferedReader().readText()
        Log.d("StartRaatServer", "Server response: $output for ${profile.sshUsername} ${profile.sshHost}, ${profile.sshPort}")
        channel.disconnect()
        session.disconnect()
        Log.d("StartRaatServer", "Session Status: ${getSessionStatus(profile)}")

    }

    private fun preConnect() {
        if (profile.ID != 0L && pref.server.lockSavedServer)
            if (!serverUnlockRequest.requestResponse(null))
                throw IOException("Could not unlock server")

        client.configure(profile.viewOnly, profile.securityType, true  /* Hardcoded to true */,
                         profile.imageQuality, profile.useRawEncoding)

        if (profile.useRepeater)
            client.setupRepeater(profile.idOnRepeater)
    }

    private fun connect() {
        when (profile.channelType) {
            ServerProfile.CHANNEL_TCP ->
                client.connect(profile.host, profile.port)

            ServerProfile.CHANNEL_SSH_TUNNEL -> {
                sshTunnel.open()
                client.connect(sshTunnel.localHost, sshTunnel.localPort)
                sshTunnel.stopAcceptingConnections()
            }

            else -> throw IOException("Unknown Channel: ${profile.channelType}")
        }

        state.postValue(State.Connected)
        sendClipboardText() //Initial sync
    }

    private fun processMessages() {
        while (viewModelScope.isActive)
            client.processServerMessage()
    }

    private fun cleanup() {
        messenger.cleanup()
        client.cleanup()
        sshTunnel.close()
    }

    /**
     * Can be used to persist any changes made to [profile]
     */
    fun saveProfile() {
        if (profile.ID != 0L)
            asyncMain { serverProfileDao.update(profile) }
    }

    /**************************************************************************
     * Frame management
     **************************************************************************/

    fun updateZoom(scaleFactor: Float, fx: Float, fy: Float) {
        val appliedScaleFactor = frameState.updateZoom(scaleFactor)

        //Calculate how much the focus would shift after scaling
        val dfx = (fx - frameState.frameX) * (appliedScaleFactor - 1)
        val dfy = (fy - frameState.frameY) * (appliedScaleFactor - 1)

        //Translate in opposite direction to keep focus fixed
        frameState.pan(-dfx, -dfy)

        frameViewRef.get()?.requestRender()
    }

    fun resetZoom() {
        frameState.setZoom(1f, 1f)
        frameViewRef.get()?.requestRender()
    }

    fun panFrame(deltaX: Float, deltaY: Float) {
        frameState.pan(deltaX, deltaY)
        frameViewRef.get()?.requestRender()
    }

    fun moveFrameTo(x: Float, y: Float) {
        frameState.moveTo(x, y)
        frameViewRef.get()?.requestRender()
    }

    fun saveZoom() {
        profile.zoom1 = frameState.zoomScale1
        profile.zoom2 = frameState.zoomScale2
        saveProfile()
    }

    /**************************************************************************
     * Clipboard Sync
     **************************************************************************/

    fun sendClipboardText() {
        viewModelScope.launch(Dispatchers.Main) {
            if (pref.server.clipboardSync)
                getClipboardText()?.let { messenger.sendClipboardText(it) }
        }
    }

    private fun receiveClipboardText(text: String) {
        viewModelScope.launch(Dispatchers.Main) {
            if (pref.server.clipboardSync)
                setClipboardText(text)
        }
    }

    /**************************************************************************
     * [VncClient.Observer] Implementation
     **************************************************************************/

    /**
     * Called when remote server has asked for password.
     */
    override fun onPasswordRequired(): String {
        if (profile.password.isNotBlank())
            return profile.password

        return obtainCredential(false).password
    }

    /**
     * Called when remote server has asked for both username & password.
     */
    override fun onCredentialRequired(): UserCredential {
        if (profile.username.isNotBlank() && profile.password.isNotBlank())
            return UserCredential(profile.username, profile.password)

        return obtainCredential(true)
    }

    private fun obtainCredential(usernameRequired: Boolean): UserCredential {
        return credentialRequest.requestResponse(usernameRequired)   //Blocking call
    }

    override fun onFramebufferUpdated() {
        frameViewRef.get()?.requestRender()
    }

    override fun onGotXCutText(text: String) {
        receiveClipboardText(text)
    }

    override fun onFramebufferSizeChanged(width: Int, height: Int) {
        viewModelScope.launch(Dispatchers.Main) {
            frameState.setFramebufferSize(width.toFloat(), height.toFloat())
        }
    }

    override fun onPointerMoved(x: Int, y: Int) {
        frameViewRef.get()?.requestRender()
    }
}
