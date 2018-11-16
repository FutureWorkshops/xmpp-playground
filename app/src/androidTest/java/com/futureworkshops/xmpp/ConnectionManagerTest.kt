package com.futureworkshops.xmpp

import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

import org.junit.Assert.*

/*
 * Copyright (c) 2018 FutureWorkshops. All rights reserved.
 */
class ConnectionManagerTest {

    val connectionManager = ConnectionManager()


    @Test
    fun login() {
        runBlocking {
            connectionManager.login(Credentials.ARIS)
        }
    }
}