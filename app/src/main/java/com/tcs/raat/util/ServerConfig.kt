package com.tcs.raat.util

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.tcs.raat.model.ServerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


fun openVNCServer(profile: ServerProfile) {
    GlobalScope.launch(Dispatchers.Default) {
        try {
            val jsch = JSch()
            val session = jsch.getSession(profile.sshUsername, profile.sshHost, profile.sshPort)
            session.setPassword(profile.sshPassword)
            session.setConfig("StrictHostKeyChecking", "no")
            session.setTimeout(10000)
            session.connect()
            val channel: (ChannelExec) = session.openChannel("exec") as ChannelExec
            channel.setCommand("Xvnc -geometry ${profile.geometry} -rfbauth ~/.vnc/passwd :1 &\n" +
                               "DISPLAY=:1 cinnamon-session &")
            channel.connect()
            channel.disconnect()
        } catch (e: JSchException) {
            e.printStackTrace()
        }
    }
}
