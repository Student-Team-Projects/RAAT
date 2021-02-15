/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.vnc

import android.graphics.PointF
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Allows sending different types of messages to remote server.
 */
class Messenger(private val client: VncClient) {

    /**************************************************************************
     * Sender thread
     **************************************************************************/

    private val sender by lazy { Executors.newSingleThreadExecutor() }

    private fun execute(action: () -> Unit) {
        if (client.state == VncClient.State.Connected)
            sender.execute(action)
    }

    fun cleanup() {
        sender.shutdownNow()
        sender.awaitTermination(60, TimeUnit.SECONDS)

        if (!sender.isShutdown)
            Log.w(this.javaClass.simpleName, "Unable to shutdown Sender thread!")
    }


    /**************************************************************************
     * Pointer events
     **************************************************************************/

    /**
     * Keeps track of current pointer button state.
     */
    private var pointerButtonMask: Int = 0

    fun sendPointerButtonDown(button: PointerButton, p: PointF) {
        pointerButtonMask = pointerButtonMask or button.bitMask
        val mask = pointerButtonMask //Need a copy to avoid passing reference
        execute { client.sendPointerEvent(p.x.toInt(), p.y.toInt(), mask) }
    }

    fun sendPointerButtonUp(button: PointerButton, p: PointF) {
        pointerButtonMask = pointerButtonMask and button.bitMask.inv()
        val mask = pointerButtonMask //Need a copy to avoid passing reference
        execute { client.sendPointerEvent(p.x.toInt(), p.y.toInt(), mask) }
    }

    fun sendClick(button: PointerButton, p: PointF) {
        sendPointerButtonDown(button, p)
        sendPointerButtonUp(button, p)
    }

    fun sendLeftClick(p: PointF) = sendClick(PointerButton.Left, p)
    fun sendMiddleClick(p: PointF) = sendClick(PointerButton.Middle, p)
    fun sendRightClick(p: PointF) = sendClick(PointerButton.Right, p)
    fun sendWheelUp(p: PointF) = sendClick(PointerButton.WheelUp, p)
    fun sendWheelDown(p: PointF) = sendClick(PointerButton.WheelDown, p)

    /**************************************************************************
     * Key events
     **************************************************************************/

    fun sendKeyDown(keyCode: Int, translate: Boolean) {
        execute { client.sendKeyEvent(keyCode, true, translate) }
    }

    fun sendKeyUp(keyCode: Int, translate: Boolean) {
        execute { client.sendKeyEvent(keyCode, false, translate) }
    }

    /**************************************************************************
     * Misc
     **************************************************************************/

    fun sendClipboardText(text: String) {
        execute { client.sendCutText(text) }
    }
}