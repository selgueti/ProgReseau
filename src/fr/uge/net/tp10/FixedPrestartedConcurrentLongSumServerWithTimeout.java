package fr.uge.net.tp10;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

public class FixedPrestartedConcurrentLongSumServerWithTimeout {

    private static final Logger logger = Logger.getLogger(FixedPrestartedConcurrentLongSumServerWithTimeout.class.getName());
    private final ServerSocketChannel serverSocketChannel;
    private final int maxClient = 2;
    private final List<ThreadData> threadsData;
    private final int timeout = 2000;

    public FixedPrestartedConcurrentLongSumServerWithTimeout(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        logger.info(this.getClass().getName() + " starts on port " + port);
        threadsData = IntStream.range(0, maxClient).mapToObj(i -> new ThreadData()).toList();
    }

    static boolean readFully(SocketChannel sc, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (sc.read(buffer) == -1) {
                logger.info("Input stream closed");
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) throws NumberFormatException, IOException, InterruptedException {
        var server = new FixedPrestartedConcurrentLongSumServerWithTimeout(Integer.parseInt(args[0]));
        server.launch();
    }

    private Runnable serverInstructions(int i) {
        return () -> {
            SocketChannel client = null;
            while (!Thread.interrupted()) {
                try {
                    client = serverSocketChannel.accept();
                    var threadData = threadsData.get(i);
                    threadData.setSocketChannel(client);
                    logger.info("Connection accepted from " + client.getRemoteAddress());
                    serve(client, threadData);
                }
                /* catch(ClosedByInterruptException e){
                    logger.info("Chanel is closed " + e.getCause());
                    return;
                }*/ catch (AsynchronousCloseException e) {
                    // Do nothing
                } catch (ClosedChannelException e) {
                    // Do nothing
                } catch (IOException ioe) {
                    logger.log(Level.SEVERE, "Connection terminated with client by IOException", ioe.getCause());
                    return;
                } finally {
                    silentlyClose(client);
                }
            }
        };
    }

    private Runnable threadControl() {
        return () -> {
            for (; ; ) {
                try {
                    Thread.sleep(timeout);
                    threadsData.forEach(td -> td.closeIfInactive(timeout));
                } catch (InterruptedException e) {
                    return;
                }
            }
        };
    }

    /**
     * Iterative server main loop
     */
    public void launch() {
        logger.info("Server started");

        new Thread(this.threadControl()).start();

        IntStream.range(0, maxClient)
                .mapToObj(i -> new Thread(this.serverInstructions(i)))
                .forEach(Thread::start);
    }

    /**
     * Treat the connection sc applying the protocol. All IOException are thrown
     *
     * @param sc
     * @throws IOException
     */
    private void serve(SocketChannel sc, ThreadData threadData) throws IOException {
        ByteBuffer nbOperandBuffer = ByteBuffer.allocate(Integer.BYTES);
        for (; ; ) {
            long res = 0;
            nbOperandBuffer.clear();
            if (!readFully(sc, nbOperandBuffer)) {
                return;
            }
            threadData.tick();

            nbOperandBuffer.flip();
            int nbOperand = nbOperandBuffer.getInt();
            ByteBuffer operands = ByteBuffer.allocate(Long.BYTES * nbOperand);
            boolean readStatus = readFully(sc, operands);
            threadData.tick();
            operands.flip();
            for (int i = 0; i < nbOperand; i++) {
                res += operands.getLong();
            }
            ByteBuffer response = ByteBuffer.allocate(Long.BYTES);
            response.putLong(res);
            response.flip();
            sc.write(response);
            threadData.tick();
            if (!readStatus) {
                break;
            }
        }
    }

    /**
     * Close a SocketChannel while ignoring IOException
     *
     * @param sc
     */
    private void silentlyClose(Closeable sc) {
        if (sc != null) {
            try {
                sc.close();
            } catch (IOException e) {
                // Do nothing
            }
        }
    }

    private static class ThreadData {
        private final Object lock = new Object();
        private SocketChannel sc;
        private long lastActivity;
        private boolean isAlreadyUsed = false;

        private ThreadData() {
        }

        /**
         * Changes the current client managed by this thread.
         */
        public void setSocketChannel(SocketChannel client) {
            Objects.requireNonNull(client);
            synchronized (lock) {
                isAlreadyUsed = true;
                sc = client;
            }
        }

        /**
         * Indicates that the client is active at the time of the call to this method.
         */
        public void tick() {
            synchronized (lock) {
                lastActivity = System.currentTimeMillis();
            }
        }

        /**
         * Disconnects the client if it has been inactive for more than timeout milliseconds.
         */
        public void closeIfInactive(int timeout) {
            synchronized (lock) {
                if (System.currentTimeMillis() - lastActivity > timeout && isAlreadyUsed) {
                    close();
                    logger.info("Connexion closed because timeout is elapsed");
                    isAlreadyUsed = false;
                }
            }
        }

        /**
         * Disconnects the client
         */
        public void close() {
            synchronized (lock) {
                try {
                    sc.close();
                } catch (IOException e) {
                    logger.warning("Connexion  closed " + e.getCause());
                }
            }
        }

    }
}