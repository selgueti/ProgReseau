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
import java.util.stream.IntStream;

public class ServerEchoMultiPort {

    private static final Logger logger = Logger.getLogger(ServerEcho.class.getName());
    private static final int BUFFER_SIZE = 1024;
    private final Selector selector = Selector.open();
    private final int port1;
    private final int port2;

    private ServerEchoMultiPort(int port1, int port2) throws IOException {
        this.port1 = port1;
        this.port2 = port2;
    }

    public static void usage() {
        System.out.println("Usage : ServerEchoMultiPort port1 port2");
    }

    public static ServerEchoMultiPort createServerEchoMultiPort(int port1, int port2) throws IOException {
        var server = new ServerEchoMultiPort(port1, port2);
        server.bindAllPort();
        return server;
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            usage();
            return;
        }
        var port1 = Integer.parseInt(args[0]);
        var port2 = Integer.parseInt(args[1]);
        if (port1 < 1024 || port2 < 1024) {
            System.out.println("Port must be > 1024");
            return;
        }
        if (port1 > port2) {
            System.out.println("Please give a correct range of socket");
            return;
        }
        ServerEchoMultiPort.createServerEchoMultiPort(port1, port2).serve();
    }

    private void bindAllPort() {
        IntStream.range(port1, port2 + 1)
                .forEach(i -> {
                    try {
                        var sender = new InetSocketAddress(i);
                        var dc = DatagramChannel.open().bind(sender);
                        dc.configureBlocking(false);
                        dc.register(selector, SelectionKey.OP_READ, new Context());
                    } catch (IOException e) {
                        logger.warning("Port " + i + "is already used");
                    }
                });
    }

    public void serve() throws IOException {
        logger.info("ServerEcho started on port range(" + port1 + "," + port2 + ")");
        while (!Thread.interrupted()) {
            try {
                selector.select(this::treatKey);
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
        }
    }

    private void doRead(SelectionKey key) throws IOException {
        var dc = (DatagramChannel) key.channel();
        var context = (Context) key.attachment();
        var buffer = context.buffer;
        buffer.clear();
        context.sender = dc.receive(buffer);
        if (null == context.sender) {
            return;
        }
        buffer.flip();
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void doWrite(SelectionKey key) throws IOException {
        var dc = (DatagramChannel) key.channel();
        var context = (Context) key.attachment();
        var buffer = context.buffer;
        dc.send(buffer, context.sender);
        if (buffer.hasRemaining()) {
            return;
        }
        key.interestOps(SelectionKey.OP_READ);
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
            throw new UncheckedIOException(ioe);
        }
    }

    static class Context {
        private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        private SocketAddress sender;
    }
}
