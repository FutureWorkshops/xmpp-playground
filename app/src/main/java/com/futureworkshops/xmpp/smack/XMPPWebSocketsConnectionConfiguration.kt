package com.futureworkshops.xmpp.smack

import org.jivesoftware.smack.ConnectionConfiguration

class XMPPWebSocketsConnectionConfiguration private constructor(builder: Builder) : ConnectionConfiguration(builder) {


    class Builder() :
        ConnectionConfiguration.Builder<Builder, XMPPWebSocketsConnectionConfiguration>() {

        override fun getThis(): Builder {
            return this
        }

        override fun build(): XMPPWebSocketsConnectionConfiguration {
            return XMPPWebSocketsConnectionConfiguration(this)
        }
    }

    companion object {

        fun builder(): Builder {
            return Builder()
        }
    }
}
