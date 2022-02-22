package fr.uge.net.tp6;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.IntStream;

public class ServerEchoMultiPort {

    private final List<DatagramChannel> datagramChannels;
    private final Selector selector;

    ServerEchoMultiPort(int port1, int port2) throws IOException {
        selector = Selector.open();

        dc.configureBlocking(false);
        dc.register(selector, SelectionKey.OP_READ);
        datagramChannels = IntStream.range(port1, port2 + 1)
                .mapToObj(i -> {
                    try {
                        var
                        return DatagramChannel.open().bind(new InetSocketAddress(i));
                    } catch (IOException e) {
                        e.printStackTrace();
                        return null;
                    }
                })
                .toList();
    }

    public void serve(){}

    private static final Logger logger = Logger.getLogger(ServerEcho.class.getName());

    private final int BUFFER_SIZE = 1024;

    public static void usage() {
        System.out.println("Usage : ServerEchoMultiPort port1 port2");
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

    }
}
