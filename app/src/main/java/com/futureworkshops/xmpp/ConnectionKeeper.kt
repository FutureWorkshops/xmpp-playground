package com.futureworkshops.xmpp

import org.jivesoftware.smack.AbstractXMPPConnection

/*
 * Copyright (c) 2018 FutureWorkshops. All rights reserved.
 */
// Singletons are cool!
object ConnectionKeeper {
    lateinit var connection: AbstractXMPPConnection
}