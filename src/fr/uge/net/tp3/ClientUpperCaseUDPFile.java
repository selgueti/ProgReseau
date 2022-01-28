package fr.uge.net.tp3;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardOpenOption.*;

public class ClientUpperCaseUDPFile {
    private final static Charset UTF8 = StandardCharsets.UTF_8;
    private final static int BUFFER_SIZE = 1024;

    private static void usage() {
        System.out.println("Usage : ClientUpperCaseUDPFile in-filename out-filename timeout host port ");
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 5) {
            usage();
            return;
        }

        var inFilename = args[0];
        var outFilename = args[1];
        var timeout = Integer.parseInt(args[2]);
        var server = new InetSocketAddress(args[3], Integer.parseInt(args[4]));
        final var charset = StandardCharsets.UTF_8;


        // Read all lines of inFilename opened in UTF-8
        var lines = Files.readAllLines(Path.of(inFilename), UTF8);
        var upperCaseLines = new ArrayList<String>();

        SynchronousQueue<ClientUpperCaseUDPTimeoutRetry.Response> blockingDeque = new SynchronousQueue<>();
        var bbSend = ByteBuffer.allocate(BUFFER_SIZE);

        try (DatagramChannel dc = DatagramChannel.open().bind(null)) {
            Thread receiver = new Thread(() -> {
                var bbReceive = ByteBuffer.allocate(BUFFER_SIZE);
                InetSocketAddress sender;
                for (; ; ) {
                    try {
                        sender = (InetSocketAddress) dc.receive(bbReceive);
                    } catch (ClosedByInterruptException e) {
                        System.out.println("ClosedByInterruptException");
                        return;
                    } catch (AsynchronousCloseException e) {
                        System.out.println("AsynchronousCloseException");
                        return;
                    } catch (ClosedChannelException e) {
                        System.out.println("ClosedChannelException");
                        return;
                    } catch (IOException e) {
                        System.out.println("IOException");
                        return;
                    }
                    bbReceive.flip();
                    int size = bbReceive.remaining();
                    var msg = charset.decode(bbReceive).toString();
                    bbReceive.clear();
                    try {
                        blockingDeque.put(new ClientUpperCaseUDPTimeoutRetry.Response(sender, msg, size));
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            });

            receiver.start();
            for (var line : lines) {
                bbSend = charset.encode(line);
                dc.send(bbSend, server);
                ClientUpperCaseUDPTimeoutRetry.Response response = null;
                while (response == null) {
                    response = blockingDeque.poll(timeout, TimeUnit.MILLISECONDS);
                    if (response == null) {
                        System.out.println("Server doesn't reply, send back the message...");
                        bbSend.flip();
                        dc.send(bbSend, server);
                    }
                }
                upperCaseLines.add(response.msg());
            }
            receiver.interrupt();
        }

        // Write upperCaseLines to outFilename in UTF-8
        Files.write(Path.of(outFilename), upperCaseLines, UTF8, CREATE, WRITE, TRUNCATE_EXISTING);
    }
}