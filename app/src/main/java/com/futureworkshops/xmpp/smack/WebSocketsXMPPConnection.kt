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
import java.io.*
import java.nio.charset.Charset

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

    private lateinit var webSocket: WebSocket

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

        initReaderAndWriter()
    }



    // TODO does this make sense or does it work at all? It is important to get this right, so that
    // we can see all the socket traffic in the console, like it does in the TCP case.
    private fun initReaderAndWriter() {
        // reader holds what the socket receives (from server)
        // writer holds what we send to the socket

        val readerInputStream = PipedInputStream()
        val readerOutputStream = PipedOutputStream(readerInputStream)

        reader = BufferedReader(InputStreamReader(readerInputStream, "UTF-8"))

        webSocket.setStringCallback {
            readerOutputStream.write(it.toByteArray(Charset.defaultCharset()))
        }

        val outputStream = ByteArrayOutputStream()
        // TODO, whatever we send to the socket, we need to write it to this stream -
        val writerOutputStream = BufferedOutputStream(outputStream)

        writer = OutputStreamWriter(writerOutputStream, "UTF-8")

        readerOutputStream.write("HI".toByteArray(Charset.defaultCharset()))

        initDebugger()
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