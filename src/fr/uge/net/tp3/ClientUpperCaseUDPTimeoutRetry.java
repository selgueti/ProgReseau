package fr.uge.net.tp3;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ClientUpperCaseUDPTimeoutRetry {

    record Response(InetSocketAddress sender, String msg, int size) {
        Response {
            Objects.requireNonNull(sender);
            Objects.requireNonNull(msg);
        }
    }

    public static final int BUFFER_SIZE = 1024;

    private static final Logger logger = Logger.getLogger(ClientUpperCaseUDPTimeoutRetry.class.getName());

    private static void usage() {
        System.out.println("Usage : NetcatUDP host port charset");
    }

    public static void main(String[] args) throws IOException, InterruptedException {

        if (args.length != 3) {
            usage();
            return;
        }

        final var server = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
        final var charset = Charset.forName(args[2]);
        SynchronousQueue<Response> blockingDeque = new SynchronousQueue<>();
        var bbSend = ByteBuffer.allocate(BUFFER_SIZE);

        try (var scanner = new Scanner(System.in); DatagramChannel dc = DatagramChannel.open().bind(null)) {
            Thread receiver = new Thread(() -> {
                var bbReceive = ByteBuffer.allocate(BUFFER_SIZE);
                InetSocketAddress sender;
                for (; ; ) {
                    try {
                        sender = (InetSocketAddress) dc.receive(bbReceive);
                    } catch (ClosedByInterruptException e) {
                        logger.info("thread receiver closed.");
                        return;
                    } catch (AsynchronousCloseException e) {
                        logger.info("thread already closed.");
                        return;
                    } catch (ClosedChannelException e) {
                        logger.warning("ClosedChannelException but in try-with-resource ?");
                        return;
                    } catch (IOException e) {
                        logger.info("IOException");
                        return;
                    }
                    bbReceive.flip();
                    int size = bbReceive.remaining();
                    var msg = charset.decode(bbReceive).toString();
                    bbReceive.clear();
                    try {
                        blockingDeque.put(new Response(sender, msg, size));
                    } catch (InterruptedException e) {
                        logger.info("thread receiver closed.");
                        return;
                    }
                }
            });

            receiver.start();
            while (scanner.hasNextLine()) {
                var line = scanner.nextLine();
                bbSend = charset.encode(line);
                dc.send(bbSend, server);
                Response response = null;
                while (response == null) {
                    response = blockingDeque.poll(1000, TimeUnit.MILLISECONDS);
                    if (response == null) {
                        System.out.println("Server doesn't reply, send back the message...");
                        bbSend.flip();
                        dc.send(bbSend, server);
                    }
                }
                System.out.println("Received " + response.size() + " bytes from " + response.sender());
                System.out.println("String: " + response.msg());
            }
            receiver.interrupt();
        }
    }
}
