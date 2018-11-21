package com.futureworkshops.xmpp.smack;

import android.support.annotation.NonNull;
import android.util.Log;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;
import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SynchronizationPoint;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Element;
import org.jivesoftware.smack.packet.Nonza;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.StreamOpen;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.util.ArrayBlockingQueueWithShutdown;
import org.jivesoftware.smack.util.Async;
import org.jivesoftware.smack.util.XmlStringBuilder;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.util.XmppStringUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Copyright (c) 2018 FutureWorkshops. All rights reserved.
 */
public class WebSocketsXMPPConnection extends AbstractXMPPConnection {

    private static final String TAG = "WS";
    private static final int QUEUE_SIZE = 500;
    private static final Logger LOGGER = Logger.getLogger(XMPPTCPConnection.class.getName());


    private final XMPPWebSocketsConnectionConfiguration config;

    private final SynchronizationPoint<Exception> socketConnected = new SynchronizationPoint<>(
            this, "Socket connected");

    private final SynchronizationPoint<Exception> initialOpenStreamSend = new SynchronizationPoint<>(
            this, "initial open stream element send to server");

    private WebSocket webSocket;
    private SocketReader socketReader;
    private SocketWriter socketWriter;


    public WebSocketsXMPPConnection(XMPPWebSocketsConnectionConfiguration config) {
        super(config);
        this.config = config;
    }


    @Override
    public boolean isSecureConnection() {
        return false;
    }

    @Override
    protected void sendStanzaInternal(Stanza packet) throws SmackException.NotConnectedException, InterruptedException {
        socketWriter.sendStreamElement(packet);
    }

    @Override
    public void sendNonza(Nonza element) throws SmackException.NotConnectedException, InterruptedException {
        socketWriter.sendStreamElement(element);
    }

    @Override
    public boolean isUsingCompression() {
        return false;
    }

    @Override
    protected void connectInternal() throws SmackException, IOException, XMPPException, InterruptedException {
        // Establishes the TCP connection to the server and does setup the reader and writer. Throws an exception if
        // there is an error establishing the connection
        connectUsingConfiguration();

        // We connected successfully to the servers TCP port
        initConnection();

        // TLS handled will be successful either if TLS was established, or if it was not mandatory.
//        tlsHandled.checkIfSuccessOrWaitOrThrow();

        // Wait with SASL auth until the SASL mechanisms have been received
//        saslFeatureReceived.checkIfSuccessOrWaitOrThrow();
    }

    private void connectUsingConfiguration() {
        socketConnected.init();

        AsyncHttpClient.getDefaultInstance()
                .websocket(config.getUri(), "xmpp", (Exception ex, WebSocket webSocket) -> {
                    if (ex != null) {
                        Log.e("WS", "Error while connecting", ex);
                        socketConnected.reportFailure(ex);
                    } else {
                        Log.d(TAG, "Established web socket connection");
                        this.webSocket = webSocket;
                        socketConnected.reportSuccess();
                    }

                });
    }

    private void initConnection() throws InterruptedException, NoResponseException, IOException {
        socketConnected.checkIfSuccessOrWait();
        boolean isFirstInitialization = socketReader == null || socketWriter == null;

        initReaderAndWriter();

        if (isFirstInitialization) {
            socketReader = new SocketReader();
            socketWriter = new SocketWriter();
        }
        socketWriter.init();
        socketReader.init();
    }

    // TODO does this make sense or does it work at all? It is important to get this right, so that
    // we can see all the socket traffic in the console, like it does in the TCP case.
    private void initReaderAndWriter() throws IOException {
        // reader holds what the socket receives (from server)
        // writer holds what we send to the socket


        writer = new WebSocketWriter(webSocket);


        PipedInputStream readerInputStream = new PipedInputStream();
        OutputStream readerOutputStream = new PipedOutputStream(readerInputStream);
        webSocket.setStringCallback((String string) -> {
            try {
                readerOutputStream.write(string.getBytes(Charset.defaultCharset()));
            } catch (IOException e) {
                e.printStackTrace(); // TODO
            }
        });
        reader = new BufferedReader(new InputStreamReader(readerInputStream, "UTF-8"));

//        PipedInputStream readerInputStream = new PipedInputStream();
//        OutputStream readerOutputStream = new PipedOutputStream(readerInputStream);
//        reader = new BufferedReader(new InputStreamReader(readerInputStream, "UTF-8"));
//        webSocket.setStringCallback((String string) -> {
//            try {
//                readerOutputStream.write(string.getBytes(Charset.defaultCharset()));
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        });
//        OutputStream outputStream = new ByteArrayOutputStream();
//        // TODO, whatever we send to the socket, we need to write it to this stream
//        OutputStream writerOutputStream = new BufferedOutputStream(outputStream);
//        writer = new OutputStreamWriter(writerOutputStream, "UTF-8");
//        readerOutputStream.write("HI".getBytes(Charset.defaultCharset()));
//


        initDebugger();
    }

    class WebSocketWriter extends Writer {
        private WebSocket webSocket;

        public WebSocketWriter(WebSocket webSocket) {
            this.webSocket = webSocket;

        }

        @Override
        public void write(@NonNull char[] cbuf, int off, int len) throws IOException {
            char[] a = new char[len];
            System.arraycopy(cbuf, off, a, 0, len);
            webSocket.send(String.valueOf(a));
        }

        @Override
        public void flush() throws IOException {
            // TODO - do nothing?
        }

        @Override
        public void close() throws IOException {
            // TODO
        }
    }


    @Override
    protected void loginInternal(String username, String password, Resourcepart resource) throws XMPPException, SmackException, IOException, InterruptedException {

    }

    @Override
    protected void shutdown() {

    }


    /**
     * Sends out a notification that there was an error with the connection
     * and closes the connection. Also prints the stack trace of the given exception
     *
     * @param e the exception that causes the connection close event.
     */
    private synchronized void notifyConnectionError(Exception e) {
        // Listeners were already notified of the exception, return right here.
        if ((socketReader == null || socketReader.done) &&
                (socketWriter == null || socketWriter.done())) return;

        // Closes the connection temporary. A reconnection is possible
        // Note that a connection listener of XMPPTCPConnection will drop the SM state in
        // case the Exception is a StreamErrorException.

        // TODO handle the shutdown
        //    instantShutdown();

        // Notify connection listeners of the error.
        callConnectionClosedOnErrorListener(e);
    }


    class SocketWriter {
        public static final int QUEUE_SIZE = WebSocketsXMPPConnection.QUEUE_SIZE;

        private final ArrayBlockingQueueWithShutdown<Element> queue = new ArrayBlockingQueueWithShutdown<>(
                QUEUE_SIZE, true);
        /**
         * If set, the stanza writer is shut down
         */
        protected volatile Long shutdownTimestamp = null;

        private volatile boolean instantShutdown;

        /**
         * Needs to be protected for unit testing purposes.
         */
        protected SynchronizationPoint<NoResponseException> shutdownDone = new SynchronizationPoint<>(
                WebSocketsXMPPConnection.this, "shutdown completed");


        /**
         * Initializes the writer in order to be used. It is called at the first connection and also
         * is invoked if the connection is disconnected by an error.
         */
        void init() {
            shutdownDone.init();
            shutdownTimestamp = null;

            // skipping unacknowledged stanzas - XEP198 related


            queue.start();


            Async.go(new Runnable() {
                @Override
                public void run() {
                    writeStuff();
                }
            }, "Smack Writer (" + getConnectionCounter() + ")");
        }

        private boolean done() {
            return shutdownTimestamp != null;
        }

        /**
         * Sends the specified element to the server.
         *
         * @param element the element to send.
         *                //         * @throws NotConnectedException
         * @throws InterruptedException
         */
        protected void sendStreamElement(Element element) throws InterruptedException {
//            try {
            queue.put(element);
//            }
//            catch (InterruptedException e) {
            // put() may throw an InterruptedException for two reasons:
            // 1. If the queue was shut down
            // 2. If the thread was interrupted
            // so we have to check which is the case
//                throwNotConnectedExceptionIfDoneAndResumptionNotPossible();
            // If the method above did not throw, then the sending thread was interrupted
//                throw e;
//            }
        }

        /**
         * Shuts down the stanza writer. Once this method has been called, no further
         * packets will be written to the server.
         *
         * @throws InterruptedException
         */
        void shutdown(boolean instant) {
            instantShutdown = instant;
            queue.shutdown();
            shutdownTimestamp = System.currentTimeMillis();
            try {
                shutdownDone.checkIfSuccessOrWait();
            } catch (NoResponseException | InterruptedException e) {
                LOGGER.log(Level.WARNING, "shutdownDone was not marked as successful by the writer thread", e);
            }
        }


        private void writeStuff() {
            Exception writerException = null;

            try {
                openStream();
                initialOpenStreamSend.reportSuccess();

                while (!done()) {
                    Element element = nextStreamElement();
                    if (element == null) {
                        continue;
                    }

                    // TODO skipping the bundle and defer stuff

                    Stanza packet = null;
                    if (element instanceof Stanza) {
                        packet = (Stanza) element;
                    }


                    CharSequence elementXml = element.toXML(StreamOpen.CLIENT_NAMESPACE);
                    if (elementXml instanceof XmlStringBuilder) {
                        ((XmlStringBuilder) elementXml).write(writer, StreamOpen.CLIENT_NAMESPACE);
                    } else {
                        writer.write(elementXml.toString());
                    }

                    if (queue.isEmpty()) {
                        writer.flush();
                    }
                    if (packet != null) {
                        firePacketSendingListeners(packet);
                    }
                }


                if (!instantShutdown) {
                    // Flush out the rest of the queue.
                    try {
                        while (!queue.isEmpty()) {
                            Element packet = queue.remove();
                            writer.write(packet.toXML(null).toString());
                        }
                        writer.flush();
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING,
                                "Exception flushing queue during shutdown, ignore and continue",
                                e);
                    }

                    // Close the stream.
                    try {
                        writer.write("</stream:stream>");
                        writer.flush();
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Exception writing closing stream element", e);
                    }

                    // Delete the queue contents (hopefully nothing is left).
                    queue.clear();
                }
                // TODO skipped XEP198 stuff (sm)

                // Do *not* close the writer here, as it will cause the socket
                // to get closed. But we may want to receive further stanzas
                // until the closing stream tag is received. The socket will be
                // closed in shutdown().


            } catch (Exception e) {
                // The exception can be ignored if the the connection is 'done'
                // or if the it was caused because the socket got closed
                if (!(done() || queue.isShutdown())) {
                    writerException = e;
                } else {
                    LOGGER.log(Level.FINE, "Ignoring Exception in writePackets()", e);
                }
            } finally {
                LOGGER.fine("Reporting shutdownDone success in writer thread");
                shutdownDone.reportSuccess();
            }
            // Delay notifyConnectionError after shutdownDone has been reported in the finally block.
            if (writerException != null) {
                notifyConnectionError(writerException);
            }
        }


        /**
         * Maybe return the next available element from the queue for writing. If the queue is shut down <b>or</b> a
         * spurious interrupt occurs, <code>null</code> is returned. So it is important to check the 'done' condition in
         * that case.
         *
         * @return the next element for writing or null.
         */
        private Element nextStreamElement() {
//             It is important the we check if the queue is empty before removing an element from it
//            if (queue.isEmpty()) {
//                shouldBundleAndDefer = true;
//            }
            Element packet = null;
            try {
                packet = queue.take();
            } catch (InterruptedException e) {
                if (!queue.isShutdown()) {
                    // Users shouldn't try to interrupt the packet writer thread
                    LOGGER.log(Level.WARNING, "Writer thread was interrupted. Don't do that. Use disconnect() instead.", e);
                }
            }
            return packet;
        }

        /**
         * Resets the parser using the latest connection's reader. Resetting the parser is necessary
         * when the plain connection has been secured or when a new opening stream element is going
         * to be sent by the server.
         *
         * @throws SmackException       if the parser could not be reset.
         * @throws InterruptedException
         */
        void openStream() throws SmackException, InterruptedException {
            // If possible, provide the receiving entity of the stream open tag, i.e. the server, as much information as
            // possible. The 'to' attribute is *always* available. The 'from' attribute if set by the user and no external
            // mechanism is used to determine the local entity (user). And the 'id' attribute is available after the first
            // response from the server (see e.g. RFC 6120 § 9.1.1 Step 2.)
            CharSequence to = getXMPPServiceDomain();
            CharSequence from = null;
            CharSequence localpart = config.getUsername();
            if (localpart != null) {
                from = XmppStringUtils.completeJidFrom(localpart, to);
            }
            String id = getStreamId();
            sendNonza(new StreamOpen(to, from, id));
//            try {
            // TODO wtf is this doing here? anyway, is there an equivalent operation we need to do?
//                packetReader.parser = PacketParserUtils.newXmppParser(reader);
//            }
//            catch (XmlPullParserException e) {
//                throw new SmackException(e);
//            }
        }

    }

    private class SocketReader {
        public boolean done;

        public void init() {
        }
    }
}


