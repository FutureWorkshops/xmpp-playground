package com.futureworkshops.xmpp

import org.jivesoftware.smack.debugger.ConsoleDebugger
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration

/*
 * Copyright (c) 2018 FutureWorkshops. All rights reserved.
 */

private const val DOMAIN_TCP = "ec2-54-87-229-14.compute-1.amazonaws.com"
private const val PORT_TCP = 5222

class ConnectionManager {

    fun login(credentials: Credentials): Unit {
        val configBuilder = XMPPTCPConnectionConfiguration.builder()
            .setUsernameAndPassword(credentials.username, credentials.password)
            .setResource("test")
            .setXmppDomain(DOMAIN_TCP)
            .setPort(PORT_TCP)
            .setDebuggerFactory(ConsoleDebugger.Factory.INSTANCE)

        val connection = XMPPTCPConnection(configBuilder.build())
        // Connect to the server
        connection.connect()
        // Log into the server
        connection.login()

    }

}