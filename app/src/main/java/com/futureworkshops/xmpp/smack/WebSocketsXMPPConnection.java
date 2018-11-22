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
import org.jivesoftware.smack.XMPPException.StreamErrorException;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.sasl.packet.SaslStreamElements;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.util.ArrayBlockingQueueWithShutdown;
import org.jivesoftware.smack.util.Async;
import org.jivesoftware.smack.util.PacketParserUtils;
import org.jivesoftware.smack.util.XmlStringBuilder;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.util.XmppStringUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

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
    private PacketReader packetReader;
    private PacketWriter packetWriter;


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
        packetWriter.sendStreamElement(packet);
    }

    @Override
    public void sendNonza(Nonza element) throws SmackException.NotConnectedException, InterruptedException {
        packetWriter.sendStreamElement(element);
    }
    /**
     * A synchronization point which is successful if this connection has received the closing
     * stream element from the remote end-point, i.e. the server.
     */
    private final SynchronizationPoint<Exception> closingStreamReceived = new SynchronizationPoint<>(
            this, "stream closing element received");

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
        boolean isFirstInitialization = packetReader == null || packetWriter == null;

        initReaderAndWriter();

        if (isFirstInitialization) {
            packetReader = new PacketReader();
            packetWriter = new PacketWriter();
        }
        packetWriter.init();
        packetReader.init();
    }

    // TODO does this make sense or does it work at all? It is important to get this right, so that
    // we can see all the socket traffic in the console, like it does in the TCP case.
    private void initReaderAndWriter() throws IOException {
        // reader holds what the socket receives (from server)
        // writer holds what we send to the socket


        writer = new WebSocketWriter(webSocket);


        // For the reader, we first create an OutputStream where we copy everything we receive from the socket.
        // Then we convert this into an InputStream through the use of pipes.
        PipedInputStream readerInputStream = new PipedInputStream();
        OutputStream readerOutputStream = new PipedOutputStream(readerInputStream);
        webSocket.setStringCallback((String string) -> {
            try {
                Log.d(TAG, "Socket received: "  + string);
                readerOutputStream.write(string.getBytes(Charset.defaultCharset()));
            } catch (IOException e) {
                e.printStackTrace(); // TODO
            }
        });
        reader = new BufferedReader(new InputStreamReader(readerInputStream, "UTF-8"));


        initDebugger();
    }

    /**
     * Resets the parser using the latest connection's reader. Resetting the parser is necessary
     * when the plain connection has been secured or when a new opening stream element is going
     * to be sent by the server.
     *
     * @throws SmackException if the parser could not be reset.
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
        try {
            packetReader.parser = PacketParserUtils.newXmppParser(reader);
        }
        catch (XmlPullParserException e) {
            throw new SmackException(e);
        }
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
        if ((packetReader == null || packetReader.done) &&
                (packetWriter == null || packetWriter.done())) return;

        // Closes the connection temporary. A reconnection is possible
        // Note that a connection listener of XMPPTCPConnection will drop the SM state in
        // case the Exception is a StreamErrorException.

        // TODO handle the shutdown
        //    instantShutdown();

        // Notify connection listeners of the error.
        callConnectionClosedOnErrorListener(e);
    }


    // Well, there are no "packets", but leaving the same name as in the TCP case, also because I also have a WebSocketWriter.
    class PacketWriter {
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
            try {
                packetReader.parser = PacketParserUtils.newXmppParser(reader);
            }
            catch (XmlPullParserException e) {
                throw new SmackException(e);
            }
        }

    }

    // Well, there are no "packets", but leaving the same name as in the TCP case, also for consistency with the reader

    protected class PacketReader {

        XmlPullParser parser;

        private volatile boolean done;

        /**
         * Initializes the reader in order to be used. The reader is initialized during the
         * first connection and when reconnecting due to an abruptly disconnection.
         */
        void init() {
            done = false;

            Async.go(new Runnable() {
                @Override
                public void run() {
                    parsePackets();
                }
            }, "Smack Reader (" + getConnectionCounter() + ")");
        }

        /**
         * Shuts the stanza reader down. This method simply sets the 'done' flag to true.
         */
        void shutdown() {
            done = true;
        }

        /**
         * Parse top-level packets in order to process them further.
         */
        private void parsePackets() {
            try {
                initialOpenStreamSend.checkIfSuccessOrWait();
                int eventType = parser.getEventType();
                while (!done) {
                    switch (eventType) {
                        case XmlPullParser.START_TAG:
                            final String name = parser.getName();
                            switch (name) {
                                case Message.ELEMENT:
                                case IQ.IQ_ELEMENT:
                                case Presence.ELEMENT:
                                    try {
                                        parseAndProcessStanza(parser);
                                    } finally {
//                                        clientHandledStanzasCount = SMUtils.incrementHeight(clientHandledStanzasCount);
                                    }
                                    break;
                                case "stream":
                                    // We found an opening stream.
                                    if ("jabber:client".equals(parser.getNamespace(null))) {
                                        streamId = parser.getAttributeValue("", "id");
                                        String reportedServerDomain = parser.getAttributeValue("", "from");
                                        assert (config.getXMPPServiceDomain().equals(reportedServerDomain));
                                    }
                                    break;
                                case "error":
                                    StreamError streamError = PacketParserUtils.parseStreamError(parser);
                                    saslFeatureReceived.reportFailure(new StreamErrorException(streamError));
                                    // Mark the tlsHandled sync point as success, we will use the saslFeatureReceived sync
                                    // point to report the error, which is checked immediately after tlsHandled in
                                    // connectInternal().
                                    tlsHandled.reportSuccess();
                                    throw new StreamErrorException(streamError);
                                case "features":
                                    parseFeatures(parser);
                                    break;
                                case "proceed":
                                    try {
                                        // Secure the connection by negotiating TLS
                                        // TODO
//                                        proceedTLSReceived();
                                        // Send a new opening stream to the server
//                                        openStream();
                                    }
                                    catch (Exception e) {
                                        SmackException smackException = new SmackException(e);
                                        tlsHandled.reportFailure(smackException);
                                        throw e;
                                    }
                                    break;
                                case "failure":
                                    String namespace = parser.getNamespace(null);
                                    switch (namespace) {
                                        case "urn:ietf:params:xml:ns:xmpp-tls":
                                            // TLS negotiation has failed. The server will close the connection
                                            // TODO Parse failure stanza
                                            throw new SmackException("TLS negotiation has failed");
                                        case "http://jabber.org/protocol/compress":
                                            // Stream compression has been denied. This is a recoverable
                                            // situation. It is still possible to authenticate and
                                            // use the connection but using an uncompressed connection
                                            // TODO Parse failure stanza
//                                            compressSyncPoint.reportFailure(new SmackException(
//                                                    "Could not establish compression"));
                                            break;
                                        case SaslStreamElements.NAMESPACE:
                                            // SASL authentication has failed. The server may close the connection
                                            // depending on the number of retries
                                            final SaslStreamElements.SASLFailure failure = PacketParserUtils.parseSASLFailure(parser);
                                            getSASLAuthentication().authenticationFailed(failure);
                                            break;
                                    }
                                    break;
                                case SaslStreamElements.Challenge.ELEMENT:
                                    // The server is challenging the SASL authentication made by the client
                                    String challengeData = parser.nextText();
                                    getSASLAuthentication().challengeReceived(challengeData);
                                    break;
                                case SaslStreamElements.Success.ELEMENT:
                                    SaslStreamElements.Success success = new SaslStreamElements.Success(parser.nextText());
                                    // We now need to bind a resource for the connection
                                    // Open a new stream and wait for the response
                                    openStream();
                                    // The SASL authentication with the server was successful. The next step
                                    // will be to bind the resource
                                    getSASLAuthentication().authenticated(success);
                                    break;

                                default:
                                    LOGGER.warning("Unknown top level stream element: " + name);
                                    break;
                            }
                            break;
                        case XmlPullParser.END_TAG:
                            final String endTagName = parser.getName();
                            if ("stream".equals(endTagName)) {
                                if (!parser.getNamespace().equals("http://etherx.jabber.org/streams")) {
                                    LOGGER.warning(WebSocketsXMPPConnection.this +  " </stream> but different namespace " + parser.getNamespace());
                                    break;
                                }

                                // Check if the queue was already shut down before reporting success on closing stream tag
                                // received. This avoids a race if there is a disconnect(), followed by a connect(), which
                                // did re-start the queue again, causing this writer to assume that the queue is not
                                // shutdown, which results in a call to disconnect().
                                final boolean queueWasShutdown = packetWriter.queue.isShutdown();
                                closingStreamReceived.reportSuccess();

                                if (queueWasShutdown) {
                                    // We received a closing stream element *after* we initiated the
                                    // termination of the session by sending a closing stream element to
                                    // the server first
                                    return;
                                } else {
                                    // We received a closing stream element from the server without us
                                    // sending a closing stream element first. This means that the
                                    // server wants to terminate the session, therefore disconnect
                                    // the connection
                                    LOGGER.info(WebSocketsXMPPConnection.this
                                            + " received closing </stream> element."
                                            + " Server wants to terminate the connection, calling disconnect()");
                                    disconnect();
                                }
                            }
                            break;
                        case XmlPullParser.END_DOCUMENT:
                            // END_DOCUMENT only happens in an error case, as otherwise we would see a
                            // closing stream element before.
                            throw new SmackException(
                                    "Parser got END_DOCUMENT event. This could happen e.g. if the server closed the connection without sending a closing stream element");
                    }
                    eventType = parser.next();
                }
            }
            catch (Exception e) {
                closingStreamReceived.reportFailure(e);
                // The exception can be ignored if the the connection is 'done'
                // or if the it was caused because the socket got closed
                if (!(done || packetWriter.queue.isShutdown())) {
                    // Close the connection and notify connection listeners of the
                    // error.
                    notifyConnectionError(e);
                }
            }
        }
    }
}


