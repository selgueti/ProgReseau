package fr.uge.net.tp10;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

public class FixedPrestartedConcurrentLongSumServerWithTimeout {

    private static final Logger logger = Logger.getLogger(FixedPrestartedConcurrentLongSumServerWithTimeout.class.getName());
    private final ServerSocketChannel serverSocketChannel;
    private final int maxClient = 2;
    private final ThreadData[] threadsData = new ThreadData[maxClient];
    private final int timeout = 2000;
    private final List<Thread> workers;
    private final Thread manager;

    public FixedPrestartedConcurrentLongSumServerWithTimeout(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        logger.info(this.getClass().getName() + " starts on port " + port);
        for (int i = 0; i < maxClient; i++) {
            threadsData[i] = new ThreadData();
        }
        workers = IntStream.range(0, maxClient)
                .mapToObj(i -> new Thread(this.serverInstructions(i))).toList();
        for (int i = 0; i < workers.size(); i++) {
            workers.get(i).setName("Worker " + i);
        }
        manager = new Thread(this.threadControl());
        manager.setName("Manager");
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

    private long actualClientConnected(){
        return Arrays.stream(threadsData).filter(ThreadData::isAlreadyUsed).count();
    }

    private void processInfo(){
        logger.info("There are currently " + actualClientConnected() +  " clients connected to the server");
    }

    private void processShutdown() throws InterruptedException {
        logger.info("shutdown...");
        try {
            serverSocketChannel.close();
            //wait that all client will be served, and then shutdown now
            while(actualClientConnected() != 0){
                Thread.sleep(1000);
            }
            processShutdownNow();
        } catch (IOException e) {
            logger.severe("IOESHUTDOWN" + e.getCause());
        }
    }

    private void processShutdownNow(){
        logger.info("shutdown now...");
        workers.forEach(Thread::interrupt);
        manager.interrupt();
    }

    public static void main(String[] args) throws NumberFormatException, IOException, InterruptedException {
        var server = new FixedPrestartedConcurrentLongSumServerWithTimeout(Integer.parseInt(args[0]));
        boolean flagShutDown = false;
        server.launch();
        try(var scanner = new Scanner(System.in)){
            while (!flagShutDown && scanner.hasNextLine()){
                var line = scanner.nextLine();
                switch(line){
                    case "INFO" -> server.processInfo();
                    case "SHUTDOWN" -> {server.processShutdown(); flagShutDown = true;}
                    case "SHUTDOWNNOW" -> {server.processShutdownNow(); scanner.close(); flagShutDown = true;}
                    default -> logger.info("Command unknown. Available command : INFO, SHUTDOWN, SHUTDOWNNOW");
                }
            }
        }
    }

    private Runnable serverInstructions(int i) {
        return () -> {
            SocketChannel client;
            while (!Thread.interrupted()) {
                try {
                    client = serverSocketChannel.accept();
                } catch(AsynchronousCloseException e){
                    //logger.info("Preparation to close the server, new connexion is not allowed");
                    return;
                } catch(IOException ioe) {
                    logger.log(Level.SEVERE, "Connection terminated with client by IOException", ioe.getCause());
                    return;
                }

                try {
                    var threadData = threadsData[i];
                    threadData.setSocketChannel(client);
                    logger.info("Connection accepted from " + client.getRemoteAddress());
                    serve(client, threadData);
                }
                catch (AsynchronousCloseException e) {
                    // Do nothing
                } catch (ClosedChannelException e) {
                    // Do nothing
                } catch (IOException ioe) {
                    // Do nothing
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
                    Arrays.stream(threadsData).forEach(td -> td.incrementAndKillIfInactive(1));
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
        manager.start();
        workers.forEach(Thread::start);
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
                threadData.close();
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
                threadData.close();
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
        private static final Logger logger = Logger.getLogger(ThreadData.class.getName());
        private final Object lock = new Object();
        private SocketChannel sc;
        private int nbTick = 0;
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
                tick();
            }
        }

        /**
         * Indicates that the client is active at the time of the call to this method.
         */
        public void tick() {
            synchronized (lock) {
                nbTick = 0;
            }
        }

        /**
         * Disconnects the client if it has been inactive for more than timeout milliseconds.
         */
        public void incrementAndKillIfInactive(int timeoutTick) {
            synchronized (lock) {
                nbTick++;
                if(timeoutTick < nbTick && isAlreadyUsed){
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
                    isAlreadyUsed = false;
                } catch (IOException e) {
                    logger.warning("Connexion  closed " + e.getCause());
                }
            }
        }

        public boolean isAlreadyUsed(){
            synchronized (lock){
                return isAlreadyUsed;
            }
        }
    }
}