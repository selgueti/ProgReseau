package fr.uge.net.tp6;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.IntStream;

public class ServerEchoMultiPort {

    //private final List<DatagramChannel> datagramChannels;
    private final Selector selector;
    private static final Logger logger = Logger.getLogger(ServerEcho.class.getName());
    private final int BUFFER_SIZE = 1024;
    public static void usage() {
        System.out.println("Usage : ServerEchoMultiPort port1 port2");
    }
    private final int port1;
    private final int port2;

    ServerEchoMultiPort(int port1, int port2) throws IOException {
        selector = Selector.open();
        this.port1 = port1;
        this.port2 = port2;

        /*
        datagramChannels = IntStream.range(port1, port2 + 1)
                .mapToObj(i -> {
                    try {
                        var dc = DatagramChannel.open().bind(new InetSocketAddress(i));
                        dc.configureBlocking(false);
                        dc.register(selector, SelectionKey.OP_READ);
                        return dc;
                    } catch (IOException e) {
                        logger.warning("carte réseau en feu");
                        return null; // TODO ok ?
                    }
                })
                .toList();
                */


        IntStream.range(port1, port2 + 1)
                .forEach(i -> {
                    try {
                        var dc = DatagramChannel.open().bind(new InetSocketAddress(i));
                        dc.configureBlocking(false);
                        dc.register(selector, SelectionKey.OP_READ, new Context());
                    } catch (IOException e) {
                        logger.warning("carte réseau en feu / port utilisé / accès non autorisé sur le port");
                    }
                });
    }

    public void serve() throws IOException {
        logger.info("ServerEcho started on port range(" + port1 + "," + port2 +")");
        while (!Thread.interrupted()) {
            try {
                selector.select(this::treatKey);
            }catch (UncheckedIOException tunneled){
                throw tunneled.getCause();
            }
        }
    }

    private void doRead(SelectionKey key) throws IOException {
       //TODO
    }

    private void doWrite(SelectionKey key) throws IOException {
        //TODO
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

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            usage();
            return;
        }
        var port1 = Integer.parseInt(args[0]);
        var port2 = Integer.parseInt(args[1]);
        if(port1 < 1024 || port2 < 1024){
            System.out.println("Socket must be > 1024");
            return;
        }
        if(port1 > port2){
            System.out.println("Please give a range of socket");
            return;
        }

        new ServerEchoMultiPort(port1, port2).serve();
    }

    static class Context {
        //TODO
        //private final SocketAddress sender;

    }
}
