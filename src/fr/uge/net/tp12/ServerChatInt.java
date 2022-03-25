package fr.uge.net.tp12;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerChatInt {
    private static final int BUFFER_SIZE = 1_024;
    private static final Logger logger = Logger.getLogger(ServerChatInt.class.getName());
    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    public ServerChatInt(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        new ServerChatInt(Integer.parseInt(args[0])).launch();
    }

    private static void usage() {
        System.out.println("Usage : ServerChatInt port");
    }

    public void launch() throws IOException {
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        while (!Thread.interrupted()) {
            Helpers.printKeys(selector); // for debug
            System.out.println("Starting select");
            try {
                selector.select(this::treatKey);
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
            System.out.println("Select finished");
        }
    }

    private void treatKey(SelectionKey key) {
        Helpers.printSelectedKey(key); // for debug
        try {
            if (key.isValid() && key.isAcceptable()) {
                doAccept(key);
            }
        } catch (IOException ioe) {
            // lambda call in select requires to tunnel IOException
            throw new UncheckedIOException(ioe);
        }
        try {
            if (key.isValid() && key.isWritable()) {
                ((Context) key.attachment()).doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                ((Context) key.attachment()).doRead();
            }
        } catch (IOException e) {
            logger.log(Level.INFO, "Connection closed with client due to IOException", e);
            silentlyClose(key);
        }
    }

    private void doAccept(SelectionKey key) throws IOException {
        var sc = serverSocketChannel.accept();
        if (sc == null) {
            logger.info("Selector lied, no accept");
            return;
        }
        sc.configureBlocking(false);
        var selectionKey = sc.register(selector, SelectionKey.OP_READ);
        selectionKey.attach(new Context(this, selectionKey));
    }

    private void silentlyClose(SelectionKey key) {
        Channel sc = key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    /**
     * Add a text to all connected clients queue
     *
     * @param msg - text to add to all connected clients queue
     */
    private void broadcast(Integer msg) {
        for (SelectionKey key : selector.keys()) {
            if (key.channel() == serverSocketChannel) {
                continue;
            }
            Context context = (Context) key.attachment(); // Safe Cast
            context.queueMessage(msg);
        }
    }

    static private class Context {
        private final SelectionKey key;
        private final SocketChannel sc;
        private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
        private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
        private final ArrayDeque<Integer> queue = new ArrayDeque<>();
        private final ServerChatInt server; // we could also have Context as an instance class, which would naturally
        // give access to ServerChatInt.this
        private boolean closed = false;

        private Context(ServerChatInt server, SelectionKey key) {
            this.key = key;
            this.sc = (SocketChannel) key.channel();
            this.server = server;
        }

        /**
         * Process the content of bufferIn
         * <p>
         * The convention is that bufferIn is in write-mode before the call to process and
         * after the call
         */
        private void processIn() {
            bufferIn.flip();
            while (bufferIn.remaining() >= Integer.BYTES) {
                server.broadcast(bufferIn.getInt());
            }
            bufferIn.compact();
            //bufferIn.clear();
        }

        /**
         * Add a text to the text queue, tries to fill bufferOut and updateInterestOps
         *
         * @param msg - text to add to the text queue
         */
        public void queueMessage(Integer msg) {
            queue.addLast(msg);
            processOut();
            updateInterestOps();
        }

        /**
         * Try to fill bufferOut from the text queue
         */
        private void processOut() {
            while (bufferOut.remaining() >= Integer.BYTES && !queue.isEmpty()) {
                bufferOut.putInt(queue.pollFirst());
            }
        }

        /**
         * Update the interestOps of the key looking only at values of the boolean
         * closed and of both ByteBuffers.
         * <p>
         * The convention is that both buffers are in write-mode before the call to
         * updateInterestOps and after the call. Also, it is assumed that process has
         * been be called just before updateInterestOps.
         */

        private void updateInterestOps() {
            var interestOps = 0;
            if (!closed && bufferIn.hasRemaining()) {
                interestOps |= SelectionKey.OP_READ;
            }
            if (bufferOut.position() != 0) {
                interestOps |= SelectionKey.OP_WRITE;
            }
            if (interestOps == 0) {
                silentlyClose();
                return;
            }
            key.interestOps(interestOps);
        }

        private void silentlyClose() {
            try {
                sc.close();
            } catch (IOException e) {
                // ignore exception
            }
        }

        /**
         * Performs the read action on sc
         * <p>
         * The convention is that both buffers are in write-mode before the call to
         * doRead and after the call
         *
         * @throws IOException - if some I/O error occurs
         */
        private void doRead() throws IOException {
            if (-1 == sc.read(bufferIn)) {
                closed = true;
            }
            processIn();
            updateInterestOps();
        }

        /**
         * Performs the write action on sc
         * <p>
         * The convention is that both buffers are in write-mode before the call to
         * doWrite and after the call
         *
         * @throws IOException - if some I/O error occurs
         */

        private void doWrite() throws IOException {
            bufferOut.flip();
            sc.write(bufferOut);
            bufferOut.compact();
            processOut();
            updateInterestOps();
        }

    }
}