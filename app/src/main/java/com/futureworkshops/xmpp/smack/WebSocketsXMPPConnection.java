package com.futureworkshops.xmpp.smack;

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

    }

    @Override
    public void sendNonza(Nonza element) throws SmackException.NotConnectedException, InterruptedException {

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
        initReaderAndWriter();
    }

    // TODO does this make sense or work at all?
    private void initReaderAndWriter() throws IOException {
        PipedInputStream readerInputStream = new PipedInputStream();
        OutputStream readerOutputStream = new PipedOutputStream(readerInputStream);
        reader = new BufferedReader(new InputStreamReader(readerInputStream, "UTF-8"));
        webSocket.setStringCallback((String string) -> {
            try {
                readerOutputStream.write(string.getBytes(Charset.defaultCharset()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        OutputStream outputStream = new ByteArrayOutputStream();
        // TODO, whatever we send to the socket, we need to write it to this stream
        OutputStream writerOutputStream = new BufferedOutputStream(outputStream);
        writer = new OutputStreamWriter(writerOutputStream, "UTF-8");
        readerOutputStream.write("HI".getBytes(Charset.defaultCharset()));
        initDebugger();
    }

    @Override
    protected void loginInternal(String username, String password, Resourcepart resource) throws XMPPException, SmackException, IOException, InterruptedException {

    }

    @Override
    protected void shutdown() {

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
         * True if some preconditions are given to start the bundle and defer mechanism.
         * <p>
         * This will likely get set to true right after the start of the writer thread, because
         * {@link #nextStreamElement()} will check if {@link queue} is empty, which is probably the case, and then set
         * this field to true.
         * </p>
         */
        private boolean shouldBundleAndDefer;


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
            try {
                openStream();

            } catch (Exception ex) {

            }
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
}


