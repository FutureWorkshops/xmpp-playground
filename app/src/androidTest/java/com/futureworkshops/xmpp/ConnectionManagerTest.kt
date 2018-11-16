package com.futureworkshops.xmpp

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
        connectionManager.login(Credentials.ARIS)
    }
}