package fr.uge.net.tp6;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.logging.Logger;

public class ServerEchoPlus {
    private static final Logger logger = Logger.getLogger(ServerEcho.class.getName());

    private final DatagramChannel dc;
    private final Selector selector;
    private final int BUFFER_SIZE = 1024;
    private final ByteBuffer bufferSender = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private final ByteBuffer bufferReceiver = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private SocketAddress sender;
    private final int port;

    public ServerEchoPlus(int port) throws IOException {
        this.port = port;
        selector = Selector.open();
        dc = DatagramChannel.open();
        dc.bind(new InetSocketAddress(port));

        dc.configureBlocking(false);
        dc.register(selector, SelectionKey.OP_READ);
    }

    public void serve() throws IOException {
        logger.info("ServerEcho started on port " + port);
        while (!Thread.interrupted()) {
            try {
                selector.select(this::treatKey);
            }catch (UncheckedIOException tunneled){
                throw tunneled.getCause();
            }
        }
    }

    private void treatKey(SelectionKey key) {
        try {
            if (key.isValid() && key.isWritable()) {
                doWrite(key);
            }
            if (key.isValid() && key.isReadable()) {
                doRead(key);
            }
        } catch (IOException ioe) {
            logger.warning("Hardware might be on fire");
            throw  new UncheckedIOException(ioe);
        }
    }

    private void doRead(SelectionKey key) throws IOException {
        var datagramChannel = (DatagramChannel)key.channel();
        bufferReceiver.clear();
        sender = datagramChannel.receive(bufferReceiver);
        if(null != sender){
            bufferReceiver.flip();
            while(bufferReceiver.hasRemaining()){
                bufferSender.put((byte) ((bufferReceiver.get() + 1) % 256));
            }
            bufferSender.flip();
            key.interestOps(SelectionKey.OP_WRITE);
        }
    }

    private void doWrite(SelectionKey key) throws IOException {
        var datagramChannel = (DatagramChannel)key.channel();
        datagramChannel.send(bufferSender, sender);
        if (!bufferSender.hasRemaining()){
            key.interestOps(SelectionKey.OP_READ);
            bufferSender.clear();
        }
    }

    public static void usage() {
        System.out.println("Usage : ServerEcho port");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        new ServerEchoPlus(Integer.parseInt(args[0])).serve();
    }
}
