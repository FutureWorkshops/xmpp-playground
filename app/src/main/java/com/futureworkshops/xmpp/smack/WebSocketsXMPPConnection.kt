package com.futureworkshops.xmpp.smack

import android.util.Log
import com.koushikdutta.async.http.AsyncHttpClient
import com.koushikdutta.async.http.WebSocket
import org.jivesoftware.smack.AbstractXMPPConnection
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.SmackException.NotConnectedException
import org.jivesoftware.smack.SynchronizationPoint
import org.jivesoftware.smack.packet.Element
import org.jivesoftware.smack.packet.Nonza
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.util.ArrayBlockingQueueWithShutdown
import org.jivesoftware.smack.util.Async
import org.jxmpp.jid.parts.Resourcepart
import java.io.*
import java.nio.charset.Charset
import java.util.logging.Level
import java.util.logging.Logger

class XMPPWebSocketsConnection(val configuration: XMPPWebSocketsConnectionConfiguration) :
    AbstractXMPPConnection(configuration) {

    companion object {
        val TAG = "WS"
        val QUEUE_SIZE = 500
        val LOGGER = Logger.getLogger(XMPPWebSocketsConnection::class.java.name)

    }


    private var socketReader: SocketReader? = null

    private var socketWriter: SocketWriter? = null

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

        val isFirstInitialization = socketReader == null || socketWriter == null

        initReaderAndWriter()

        if (isFirstInitialization) {
            socketReader = SocketReader()
            socketWriter = SocketWriter()
        }

        socketWriter!!.init()
        socketReader!!.init()
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

    inner class SocketReader {
        fun init() {


        }

    }

    inner class SocketWriter {
        private val QUEUE_SIZE = XMPPWebSocketsConnection.QUEUE_SIZE

        private val queue = ArrayBlockingQueueWithShutdown<Element>(QUEUE_SIZE, true)

        /**
         * Needs to be protected for unit testing purposes.
         */
        var shutdownDone = SynchronizationPoint<SmackException.NoResponseException>(
            this@XMPPWebSocketsConnection, "shutdown completed"
        )

        /**
         * If set, the stanza writer is shut down
         */
        @Volatile
        var shutdownTimestamp: Long? = null

        @Volatile
        private var instantShutdown: Boolean = false

        /**
         * True if some preconditions are given to start the bundle and defer mechanism.
         *
         *
         * This will likely get set to true right after the start of the writer thread, because
         * [.nextStreamElement] will check if [queue] is empty, which is probably the case, and then set
         * this field to true.
         *
         */
        private var shouldBundleAndDefer: Boolean = false


        fun init() {
            shutdownDone.init()
            shutdownTimestamp = null

            // skipping unacknowledged stanzas - XEP198 related

            queue.start()

            Async.go({ writeStuff() }, "Smack Writer ($connectionCounter)")
        }

        private fun done(): Boolean {
            return shutdownTimestamp != null
        }

        /**
         * Sends the specified element to the server.
         *
         * @param element the element to send.
         * @throws NotConnectedException
         * @throws InterruptedException
         */
        @Throws(NotConnectedException::class, InterruptedException::class)
        fun sendStreamElement(element: Element) {
            // Removed some XEP198 related stuff
            queue.put(element)
        }


        /**
         * Shuts down the stanza writer. Once this method has been called, no further
         * packets will be written to the server.
         * @throws InterruptedException
         */
        internal fun shutdown(instant: Boolean) {
            instantShutdown = instant
            queue.shutdown()
            shutdownTimestamp = System.currentTimeMillis()
            try {
                shutdownDone.checkIfSuccessOrWait()
            } catch (e: SmackException.NoResponseException) {
                LOGGER.log(Level.WARNING, "shutdownDone was not marked as successful by the writer thread", e)
            } catch (e: InterruptedException) {
                LOGGER.log(Level.WARNING, "shutdownDone was not marked as successful by the writer thread", e)
            }

        }

        /**
         * Maybe return the next available element from the queue for writing. If the queue is shut down **or** a
         * spurious interrupt occurs, `null` is returned. So it is important to check the 'done' condition in
         * that case.
         *
         * @return the next element for writing or null.
         */
        private fun nextStreamElement(): Element? {
            // It is important the we check if the queue is empty before removing an element from it
            if (queue.isEmpty()) {
                shouldBundleAndDefer = true
            }
            var packet: Element? = null
            try {
                packet = queue.take()
            } catch (e: InterruptedException) {
                if (!queue.isShutdown) {
                    // Users shouldn't try to interrupt the packet writer thread
                    LOGGER.log(
                        Level.WARNING,
                        "Writer thread was interrupted. Don't do that. Use disconnect() instead.",
                        e
                    )
                }
            }

            return packet
        }




        private fun writeStuff() {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }
}