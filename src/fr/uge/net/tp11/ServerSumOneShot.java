package fr.uge.net.tp11;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.logging.Logger;

public class ServerSumOneShot {

    private static final int BUFFER_SIZE = 2 * Integer.BYTES;
    private static final Logger logger = Logger.getLogger(ServerSumOneShot.class.getName());

    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;

    public ServerSumOneShot(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();
    }

    public void launch() throws IOException {
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        while (!Thread.interrupted()) {
            Helpers.printKeys(selector); // for debug
            System.out.println("Starting select");
            try{
            selector.select(this::treatKey);
            }catch (UncheckedIOException e){
                throw e.getCause();
            }
            System.out.println("Select finished");
        }
    }

    private void treatKey(SelectionKey key) {
        Helpers.printSelectedKey(key); // for debug
        if (key.isValid() && key.isAcceptable()) {
            try {
                doAccept(key);
            } catch (IOException e) {
                logger.severe("Hardware on fire!");
                throw new UncheckedIOException(e);
            }
        }
        if (key.isValid() && key.isWritable()) {
            try {
                doWrite(key);
            } catch (IOException e) {
                logger.info("Client on fire");
            }
        }
        if (key.isValid() && key.isReadable()) {
            try {
                doRead(key);
            } catch (IOException e) {
                logger.info("Client on fire");
            }
        }
    }

    private void doAccept(SelectionKey key) throws IOException {
        var sc = serverSocketChannel.accept();
        if(sc != null){
            sc.configureBlocking(false);
            sc.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(BUFFER_SIZE));
        }
    }

    /**
     * Buffer must be in write mode
     * */
    private void doRead(SelectionKey key) throws IOException {
        var sc = (SocketChannel)key.channel();
        var buffer = (ByteBuffer)key.attachment();

        if(sc.read(buffer) == -1){
            logger.info("Connexion closed");
            silentlyClose(key);
            return;
        }

        if(buffer.hasRemaining()){
            return;
        }
        buffer.flip();
        var sum = buffer.getInt() + buffer.getInt();
        buffer.clear().putInt(sum);
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void doWrite(SelectionKey key) throws IOException {
        var sc = (SocketChannel)key.channel();
        var buffer = (ByteBuffer)key.attachment();
        buffer.flip();
        sc.write(buffer);
        if(buffer.hasRemaining()){
            buffer.compact();
            return;
        }
        //silentlyClose(key);
        buffer.clear();
        key.interestOps(SelectionKey.OP_READ);
    }

    private void silentlyClose(SelectionKey key) {
        var sc = (Channel) key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        new ServerSumOneShot(Integer.parseInt(args[0])).launch();
    }

    private static void usage() {
        System.out.println("Usage : ServerSumOneShot port");
    }
}