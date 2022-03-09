package fr.uge.net.tp10;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OnDemandConcurrentLongSumServer {

    private static final Logger logger = Logger.getLogger(OnDemandConcurrentLongSumServer.class.getName());
    private final ServerSocketChannel serverSocketChannel;

    public OnDemandConcurrentLongSumServer(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        logger.info(this.getClass().getName() + " starts on port " + port);
    }

    /**
     * Iterative server main loop
     *
     * @throws IOException
     */

    public void launch() throws IOException {
        logger.info("Server started");
        while (!Thread.interrupted()) {
            SocketChannel client = serverSocketChannel.accept();
            new Thread( ()-> {
                try {
                    logger.info("Connection accepted from " + client.getRemoteAddress());
                    serve(client);
                } catch (IOException ioe) {
                    logger.log(Level.SEVERE, "Connection terminated with client by IOException", ioe.getCause());
                } finally {
                    silentlyClose(client);
                }
            }).start();
        }
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

    public static void main(String[] args) throws NumberFormatException, IOException {
        var server = new OnDemandConcurrentLongSumServer(Integer.parseInt(args[0]));
        server.launch();
    }
}