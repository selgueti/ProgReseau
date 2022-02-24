package fr.uge.net.tp4;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static java.nio.file.StandardOpenOption.*;

public class ClientIdUpperCaseUDPOneByOne {

    private static final Logger logger = Logger.getLogger(ClientIdUpperCaseUDPOneByOne.class.getName());
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final int BUFFER_SIZE = 1024;
    private final String inFilename;
    private final String outFilename;
    private final long timeout;
    private final InetSocketAddress server;
    private final DatagramChannel dc;
    private final SynchronousQueue<Response> queue = new SynchronousQueue<>();

    public ClientIdUpperCaseUDPOneByOne(String inFilename, String outFilename, long timeout, InetSocketAddress server)
            throws IOException {
        this.inFilename = Objects.requireNonNull(inFilename);
        this.outFilename = Objects.requireNonNull(outFilename);
        this.timeout = timeout;
        this.server = server;
        this.dc = DatagramChannel.open();
        dc.bind(null);
    }

    public static void usage() {
        System.out.println("Usage : ClientIdUpperCaseUDPOneByOne in-filename out-filename timeout host port ");
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 5) {
            usage();
            return;
        }

        var inFilename = args[0];
        var outFilename = args[1];
        var timeout = Long.parseLong(args[2]);
        var server = new InetSocketAddress(args[3], Integer.parseInt(args[4]));

        // Create client with the parameters and launch it
        new ClientIdUpperCaseUDPOneByOne(inFilename, outFilename, timeout, server).launch();
    }

    private void listenerThreadRun() {
        ByteBuffer bbReceive = ByteBuffer.allocateDirect(BUFFER_SIZE);
        //InetSocketAddress sender;
        for (; ; ) {
            try {
                //sender = (InetSocketAddress) dc.receive(bbReceive);
                dc.receive(bbReceive);
                bbReceive.flip();
                // TODO  check that we have size for a long in buffer
                var id = bbReceive.getLong();
                // TODO check that we have a good datagram
                var msg = UTF8.decode(bbReceive).toString();
                queue.put(new Response(id, msg));
                bbReceive.clear();
            } catch (AsynchronousCloseException | InterruptedException e) {
                logger.info("DatagramChanel closed");
                return;
            } catch (IOException e) {
                logger.severe("Hardware might be on fire, caused by" + e.getCause() + ", " + e.getMessage());
                return;
            }
        }
    }

    public void launch() throws IOException, InterruptedException {
        ByteBuffer bbSender = ByteBuffer.allocateDirect(BUFFER_SIZE);
        try {
            var listenerThread = new Thread(this::listenerThreadRun);
            listenerThread.start();

            // Read all lines of inFilename opened in UTF-8
            var lines = Files.readAllLines(Path.of(inFilename), UTF8);
            var upperCaseLines = new ArrayList<String>();
            long id = 0;
            for (var line : lines) {
                var lastSend = 0L;
                bbSender.putLong(id).put(UTF8.encode(line));
                bbSender.flip();
                for (; ; ) {
                    var currentTime = System.currentTimeMillis();
                    if (currentTime - lastSend > timeout) {
                        dc.send(bbSender, server);
                        lastSend = currentTime;
                        bbSender.flip();
                    }
                    var remainingTime = (lastSend + timeout) - currentTime;
                    Response response = queue.poll(remainingTime, TimeUnit.MILLISECONDS);
                    if (response == null) {
                        lastSend = 0L; // force the return of the (i-1)th datagram
                    } else if (response.id() == id) {
                        upperCaseLines.add(response.message());
                        break;
                    }
                }
                bbSender.clear();
                id++;
            }
            listenerThread.interrupt();
            Files.write(Paths.get(outFilename), upperCaseLines, UTF8, CREATE, WRITE, TRUNCATE_EXISTING);
        } finally {
            dc.close();
        }
    }

    private record Response(long id, String message) {
    }
}
