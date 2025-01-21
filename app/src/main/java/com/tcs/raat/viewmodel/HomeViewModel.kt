/*
 * Copyright (c) 2025  Viktar Dubovik.
 * Copyright (c) 2025  Bogdan Tolstik.
 * Copyright (c) 2025  Daniil Zabauski.
 * Copyright (c) 2025  Dzmitry Maslionchanka.
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.tcs.raat.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.viewModelScope
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.tcs.raat.model.ServerProfile
import com.tcs.raat.vnc.Discovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tcs.raat.ui.home.getSessionStatus


class HomeViewModel(app: Application) : BaseViewModel(app) {

    /**
     * [ServerProfile]s stored in database.
     * Depending on the user pref, this list may be sorted by server name.
     */
    val serverProfiles by lazy {
        Transformations.switchMap(pref.ui.sortServerList) {
            if (it) serverProfileDao.getSortedLiveList()
            else serverProfileDao.getLiveList()
        }
    }

    /**
     * Used to find new servers.
     */
    val discovery by lazy { Discovery(app) }

    /**
     * Used for starting new VNC connections.
     */
    val newConnectionEvent = LiveEvent<ServerProfile>()

    /**
     * This event is used for editing/creating server profiles.
     * Home activity observes this event and starts profile editor when it is fired.
     */
    val editProfileEvent = LiveEvent<ServerProfile>()

    /**
     * Fired when a new profile is saved to database.
     * Can be used to highlight the new profile in UI.
     */
    val profileInsertedEvent = LiveEvent<ServerProfile>()

    /**
     * Fired when a profile is updated in database
     */
    val profileUpdatedEvent = LiveEvent<ServerProfile>()

    /**
     * Fired when a profile is deleted from database.
     * This is used for notifying the user and potentially undo the deletion.
     */
    val profileDeletedEvent = LiveEvent<ServerProfile>()

    /**
     * Starts new connection to given profile.
     */
    fun startConnection(profile: ServerProfile) = newConnectionEvent.fire(profile)

    /**************************************************************************
     * Server Discovery
     *
     * To save battery, Discovery is stopped when HomeActivity is in background.
     **************************************************************************/
    private var autoStopped = false

    fun startDiscovery() {
        autoStopped = false
        discovery.start(viewModelScope)
    }

    fun stopDiscovery() {
        autoStopped = false
        discovery.stop()
    }

    fun autoStartDiscovery() {
        if (pref.server.discoveryAutorun || autoStopped)
            startDiscovery()
    }

    fun autoStopDiscovery() {
        if (discovery.isRunning.value == true) {
            stopDiscovery()
            autoStopped = true
        }
    }


    /**************************************************************************
     * Profile editing/creating
     *
     * These are invoked from UI on user actions. We simply fire [editProfileEvent]
     * with appropriate profile, causing the profile editor to be shown.
     *
     * NOTE: We need to make a copy of given profile because the instance
     * given to [editProfileEvent] can be modified by the editor.
     **************************************************************************/

    fun onNewProfile() = editProfileEvent.fire(ServerProfile())
    fun onNewProfile(source: ServerProfile) = editProfileEvent.fire(source.copy(ID = 0))
    fun onEditProfile(profile: ServerProfile) = editProfileEvent.fire(profile.copy())

    fun onDuplicateProfile(profile: ServerProfile) {
        val duplicate = profile.copy(ID = 0)
        duplicate.name += " (Copy)"
        editProfileEvent.fire(duplicate)
    }

    fun onCloseSessionProfile(profile: ServerProfile) {
        viewModelScope.launch {
            var exception: Exception? = null
            var session: Session? = null

            try {
                withContext(Dispatchers.IO) {
                    val jsch = JSch()
                    Log.d("CloseSessionRaat", "${profile.sshUsername} ${profile.sshHost}, ${profile.sshPort}")
                    session = jsch.getSession(profile.sshUsername, profile.sshHost, profile.sshPort)
                    session?.setPassword(profile.sshPassword)
                    session?.setConfig("StrictHostKeyChecking", "no")
                    session?.timeout = 10000
                    session?.connect()

                    val channel = session?.openChannel("exec") as ChannelExec
                    val port = if (profile.port <= 5900) profile.port + 5900 else profile.port
                    Log.d("CloseSessionRaat", "raat-server-request kill-session --rfb_port=$port")

                    channel.setCommand("raat-server-request kill-session --rfb_port=$port")
                    val inputStream = channel.inputStream
                    channel.connect()
                    delay(1000)
                    val output = inputStream.bufferedReader().readText()
                    Log.d("CloseSessionRaat", "Server response: $output for ${profile.sshUsername} ${profile.sshHost}, ${profile.sshPort}")
                    channel.disconnect()
                }
            } catch (e: Exception) {
                exception = e
                Log.e("CloseSessionRaat", "Error closing session for ${profile.sshUsername} ${profile.sshHost}, ${profile.sshPort}", e)
            } finally {
                session?.disconnect()
            }

            withContext(Dispatchers.Main) {
                if (exception == null) {
                    Log.d("CloseSessionRaat", "Session closed for ${profile.sshUsername} ${profile.sshHost}, ${profile.sshPort}")
                    getSessionStatus(profile)
                    updateProfile(profile)
                }
            }
        }
    }

    /**************************************************************************
     * Profile persistence
     *
     * These operations are asynchronous.
     **************************************************************************/

    fun insertProfile(profile: ServerProfile) = asyncMain {
        serverProfileDao.insert(profile)
        profileInsertedEvent.fire(profile)
    }

    fun updateProfile(profile: ServerProfile) = asyncMain {
        serverProfileDao.update(profile)
        profileUpdatedEvent.fire(profile)
    }

    fun deleteProfile(profile: ServerProfile) = asyncMain {
        serverProfileDao.delete(profile)
        profileDeletedEvent.fire(profile)
    }


    /**************************************************************************
     * Rediscovery Indicator
     *
     * [rediscoveredProfiles] is the intersection of saved & discovered servers.
     *
     * To detect reachable server in [serverProfiles], we could directly 'ping'
     * them, but that has its own issues.
     **************************************************************************/
    val rediscoveredProfiles by lazy {
        Transformations.switchMap(pref.server.rediscoveryIndicator) {
            if (it) prepareRediscoveredProfiles()
            else MutableLiveData(null)
        }
    }

    private fun prepareRediscoveredProfiles() = with(MediatorLiveData<List<ServerProfile>>()) {
        val filter = { saved: List<ServerProfile>?, discovered: List<ServerProfile>? ->
            saved?.filter { s -> discovered?.find { s.host == it.host && s.port == it.port } != null }
        }
        addSource(serverProfiles) { value = filter(it, discovery.servers.value) }
        addSource(discovery.servers) { value = filter(serverProfiles.value, it) }
        this
    }
}