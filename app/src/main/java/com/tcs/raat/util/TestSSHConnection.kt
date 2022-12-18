package com.tcs.raat.util

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.tcs.raat.model.ServerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


fun testSSHConnection(profile: ServerProfile) {
    GlobalScope.launch(Dispatchers.IO) {
        val port = 22
        try {
            val jsch = JSch()
            val session = jsch.getSession(profile.username, profile.host, port)
            session.setPassword(profile.password)
            session.setConfig("StrictHostKeyChecking", "no")
            session.setTimeout(10000)
            session.connect()
            val channel: (ChannelExec) = session.openChannel("exec") as ChannelExec
            channel.setCommand("touch ~/newfile")
            channel.connect()
            channel.disconnect()
        } catch (e: JSchException) {
            e.printStackTrace()
            println("Failed")
        }
    }
}
