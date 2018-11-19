package com.futureworkshops.xmpp

import android.util.Log
import kotlinx.coroutines.runBlocking
import org.jivesoftware.smack.chat2.ChatManager
import org.junit.Test

import java.util.concurrent.CountDownLatch
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.jid.EntityBareJid



/*
 * Copyright (c) 2018 FutureWorkshops. All rights reserved.
 */


class ConnectionManagerTest {

    private val connectionManager = ConnectionManager()
    private val TAG = "TEST"

    @Test
    fun loginTest() {
        runBlocking {
            connectionManager.login(Credentials.ARIS)
        }
    }


    @Test
    fun sendMessage() {
        val latch = CountDownLatch(2)

        runBlocking {
            val connection1 = connectionManager.login(Credentials.ARIS)
            val connection2 = connectionManager.login(Credentials.TEST)


            val chatManager1 = ChatManager.getInstanceFor(connection1)
            val chatManager2 = ChatManager.getInstanceFor(connection2)

            chatManager2.addIncomingListener { from, message, chat ->
                Log.d(TAG, "Received message \"${message.body}\" from $from")
                latch.countDown()
            }

            val jid = JidCreate.entityBareFrom("test@localhost.com")
            val chat = chatManager1.chatWith(jid)
            chat.send("Oh hi!")
            latch.countDown()
        }


        Log.d(TAG, "Waiting for latch....")
        latch.await()
        Log.d(TAG, "Waited. Bye!")



    }
}