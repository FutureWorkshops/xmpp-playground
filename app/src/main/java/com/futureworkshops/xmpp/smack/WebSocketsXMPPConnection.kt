package com.futureworkshops.xmpp.smack

import android.util.Log
import com.koushikdutta.async.http.AsyncHttpClient
import com.koushikdutta.async.http.WebSocket
import org.jivesoftware.smack.AbstractXMPPConnection
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.SynchronizationPoint
import org.jivesoftware.smack.packet.Nonza
import org.jivesoftware.smack.packet.Stanza
import org.jxmpp.jid.parts.Resourcepart
import java.net.ConnectException

class XMPPWebSocketsConnection(val configuration: XMPPWebSocketsConnectionConfiguration) :
    AbstractXMPPConnection(configuration) {

    private val TAG = "WS"

    private val socketConnected = SynchronizationPoint<Exception>(this, "Socket connected")

    override fun isSecureConnection(): Boolean {
        return false
    }

    override fun sendStanzaInternal(packet: Stanza?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private var webSocket: WebSocket? = null

    override fun connectInternal() {

        // Establishes the TCP connection to the server and does setup the reader and writer. Throws an exception if
        // there is an error establishing the connection
        connectUsingConfiguration()

        // We connected successfully to the servers TCP port
        initConnection()

//        // TLS handled will be successful either if TLS was established, or if it was not mandatory.
//        tlsHandled.checkIfSuccessOrWaitOrThrow()
//
//        // Wait with SASL auth until the SASL mechanisms have been received
//        saslFeatureReceived.checkIfSuccessOrWaitOrThrow()
    }

    private fun connectUsingConfiguration() {

        socketConnected.init()

        AsyncHttpClient.getDefaultInstance()
            .websocket(configuration.uri, "xmpp") { ex, webSocket ->
                ex?.let {
                    Log.e("WS", "Error while connecting", it)
                    socketConnected.reportFailure(it)
                    throw SmackException.ConnectionException(it)
                }
                webSocket?.let {
                    Log.d(TAG, "Established web socket connection")
                    this.webSocket = it
                    socketConnected.reportSuccess()
                }
            }

    }

    private fun initConnection() {
        socketConnected.checkIfSuccessOrWait()

        webSocket!!.let {
            Log.d(TAG, "Web socket is initialized")
        }
    }

    override fun loginInternal(username: String?, password: String?, resource: Resourcepart?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isUsingCompression(): Boolean {
        return false
    }

    override fun sendNonza(element: Nonza?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun shutdown() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}