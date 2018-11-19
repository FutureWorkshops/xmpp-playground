package com.futureworkshops.xmpp

import com.futureworkshops.xmpp.smack.XMPPWebSocketsConnection
import com.futureworkshops.xmpp.smack.XMPPWebSocketsConnectionConfiguration
import com.koushikdutta.async.http.AsyncHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jivesoftware.smack.AbstractXMPPConnection
import org.jivesoftware.smack.ConnectionConfiguration
import org.jivesoftware.smack.SASLAuthentication
import org.jivesoftware.smack.debugger.ConsoleDebugger
import org.jivesoftware.smack.sasl.core.SCRAMSHA1Mechanism
import org.jivesoftware.smack.sasl.core.ScramMechanism
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration

/*
 * Copyright (c) 2018 FutureWorkshops. All rights reserved.
 */

private const val HOST = "ec2-54-87-229-14.compute-1.amazonaws.com"
private const val PORT_TCP = 5222

private const val URI_SOCKETS = "ws://ec2-54-87-229-14.compute-1.amazonaws.com:5280/ws-xmpp"



class ConnectionManager {

    suspend fun loginTCP(credentials: Credentials): AbstractXMPPConnection {

        // disable security
        SASLAuthentication.unBlacklistSASLMechanism("PLAIN")
        SASLAuthentication.blacklistSASLMechanism(ScramMechanism.DIGESTMD5)
        SASLAuthentication.blacklistSASLMechanism(SCRAMSHA1Mechanism.NAME)

        val configBuilder = XMPPTCPConnectionConfiguration.builder()
            .setUsernameAndPassword(credentials.username, credentials.password)
            .setResource("test")
            .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled) // No TLS for the time being
            .setHost(HOST)
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

        return connection

    }



    suspend fun loginWebSockets(credentials : Credentials) : AbstractXMPPConnection {
        val configBuilder = XMPPWebSocketsConnectionConfiguration.builder()
            .setUsernameAndPassword(credentials.username, credentials.password)
            .setResource("test")
            .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled) // No TLS for the time being
            .setXmppDomain("localhost")
            .setUri(URI_SOCKETS)
            .setDebuggerFactory(ConsoleDebugger.Factory.INSTANCE)

        val connection = XMPPWebSocketsConnection(configBuilder.build())

        withContext(Dispatchers.IO) {
            // Connect to the server
            connection.connect()
            // Log into the server
//            connection.login()
        }

        return connection
    }
}