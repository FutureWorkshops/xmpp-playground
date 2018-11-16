package com.futureworkshops.xmpp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jivesoftware.smack.ConnectionConfiguration
import org.jivesoftware.smack.debugger.ConsoleDebugger
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration

/*
 * Copyright (c) 2018 FutureWorkshops. All rights reserved.
 */

private const val HOST_TCP = "http://ec2-54-87-229-14.compute-1.amazonaws.com"
private const val PORT_TCP = 5222

class ConnectionManager {

    suspend fun login(credentials: Credentials) {
        val configBuilder = XMPPTCPConnectionConfiguration.builder()
            .setUsernameAndPassword(credentials.username, credentials.password)
            .setResource("test")
            .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled) // No TLS for the time being
            .setHost(HOST_TCP)
            .setXmppDomain("localhost")
            .setPort(PORT_TCP)
            .setDebuggerFactory(ConsoleDebugger.Factory.INSTANCE)

        val connection = XMPPTCPConnection(configBuilder.build())

        withContext(Dispatchers.IO) {
            // Connect to the server
            connection.connect()
            // Log into the server
            connection.login()
        }

    }

}