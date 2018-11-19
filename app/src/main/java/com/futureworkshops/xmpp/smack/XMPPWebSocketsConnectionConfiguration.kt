package com.futureworkshops.xmpp.smack

import org.jivesoftware.smack.ConnectionConfiguration
import org.minidns.dnsname.DnsName
import java.lang.UnsupportedOperationException

class XMPPWebSocketsConnectionConfiguration private constructor(builder: Builder) : ConnectionConfiguration(builder) {

    val uri: String?

    init {
        uri = builder.uri
    }




    class Builder :
        ConnectionConfiguration.Builder<Builder, XMPPWebSocketsConnectionConfiguration>() {

        var uri: String? = null

        override fun getThis(): Builder {
            return this
        }

        override fun setPort(port: Int): Builder {
            throw UnsupportedOperationException("Please use setUri instead")
        }

        override fun setHost(host: DnsName?): Builder {
            throw UnsupportedOperationException("Please use setUri instead")
        }

        override fun setHost(host: String?): Builder {
            throw UnsupportedOperationException("Please use setUri instead")
        }

        override fun build(): XMPPWebSocketsConnectionConfiguration {
            return XMPPWebSocketsConnectionConfiguration(this)
        }

        fun setUri(uri: String): Builder {
            this.uri = uri
            return this
        }
    }

    companion object {

        fun builder(): Builder {
            return Builder()
        }
    }
}
