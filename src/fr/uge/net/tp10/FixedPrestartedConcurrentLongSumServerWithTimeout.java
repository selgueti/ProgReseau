package fr.uge.net.tp10;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

public class FixedPrestartedConcurrentLongSumServerWithTimeout {

    private static final Logger logger = Logger.getLogger(FixedPrestartedConcurrentLongSumServerWithTimeout.class.getName());
    private final ServerSocketChannel serverSocketChannel;
    private final int maxClient = 20;
    private final List<ThreadData> threadData = new ArrayList<>(maxClient);
    private final int timeout = 2000;

    private Runnable serverInstructions() {
        return  () -> {
            SocketChannel client = null;
            while (!Thread.interrupted()) {
                try {
                    client = serverSocketChannel.accept();
                    var tdOpt = threadData.stream().filter(td -> td.isNotAlreadyUsed()).findAny();
                    logger.info("Connection accepted from " + client.getRemoteAddress());
                    serve(client);
                }
                /* catch(ClosedByInterruptException e){
                    logger.info("Chanel is closed " + e.getCause());
                    return;
                }*/
                catch (AsynchronousCloseException e) {
                    logger.info("Chanel is closed" + e.getCause());
                    return;
                } catch (ClosedChannelException e) {
                    logger.info("Chanel is closed" + e.getCause());
                    return;
                } catch (IOException ioe) {
                    logger.log(Level.SEVERE, "Connection terminated with client by IOException", ioe.getCause());
                    return;
                }finally {
                    silentlyClose(client);
                }
            }
        };
    }

    private Runnable threadControle(){
        return () -> {
            threadData.forEach(td -> td.closeIfInactive(timeout));
        };
    }

    public FixedPrestartedConcurrentLongSumServerWithTimeout(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        logger.info(this.getClass().getName() + " starts on port " + port);
    }

    /**
     * Iterative server main loop
     *
     * @throws IOException
     */
    public void launch() {
        logger.info("Server started");

        new Thread(this.threadControle()).start();

        IntStream.range(0, maxClient)
                .mapToObj(i -> new Thread(this.serverInstructions()))
                .forEach(Thread::start);
    }

    /**
     * Treat the connection sc applying the protocol. All IOException are thrown
     *
     * @param sc
     * @throws IOException
     */
    private void serve(SocketChannel sc) throws IOException {
        ByteBuffer nbOperandBuffer = ByteBuffer.allocate(Integer.BYTES);
        for(;;){
            long res = 0;
            nbOperandBuffer.clear();
            if(!readFully(sc, nbOperandBuffer)){
               return;
            }

            nbOperandBuffer.flip();
            int nbOperand = nbOperandBuffer.getInt();
            ByteBuffer operands = ByteBuffer.allocate(Long.BYTES * nbOperand);
            boolean readStatus = readFully(sc, operands);
            operands.flip();
            for (int i = 0; i < nbOperand; i++) {
                res += operands.getLong();
            }
            ByteBuffer response = ByteBuffer.allocate(Long.BYTES);
            response.putLong(res);
            response.flip();
            sc.write(response);
            if(!readStatus){
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

    private static class ThreadData {
        private final Object lock = new Object();
        private SocketChannel sc;
        private long lastActivity;
        private boolean isAlreadyUsed = false;

        private ThreadData() throws IOException {
            this.sc = SocketChannel.open();
            this.lastActivity = System.currentTimeMillis();
        }

        /**
         * Changes the current client managed by this thread.
         */
        public void setSocketChannel(SocketChannel client){
            Objects.requireNonNull(client);
            synchronized (lock){
                sc = client;
                isAlreadyUsed = true;
            }
        }

        /**
         * Indicates that the client is active at the time of the call to this method.
         */
        public void tick(){
            synchronized (lock){
                lastActivity = System.currentTimeMillis();
            }
        }

        /**
         * Disconnects the client if it has been inactive for more than timeout milliseconds.
         */
        public void closeIfInactive(int timeout){
            synchronized (lock){
                if(lastActivity + timeout < System.currentTimeMillis()){
                    close();
                }
            }
        }

        /**
         * Disconnects the client
         */
        public void close(){
            synchronized (lock){
                try {
                    sc.close();
                } catch (IOException e) {
                    logger.warning("Connexion  closed " + e.getCause());
                }finally {
                    isAlreadyUsed = false;
                }
            }
        }

        public boolean isNotAlreadyUsed(){
            synchronized (lock){
                return !isAlreadyUsed;
            }
        }
    }
}