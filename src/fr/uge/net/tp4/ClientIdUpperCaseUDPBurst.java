package fr.uge.net.tp4;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import static java.lang.Integer.parseInt;

public class ClientIdUpperCaseUDPBurst {

    private static final Logger logger = Logger.getLogger(ClientIdUpperCaseUDPBurst.class.getName());
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final int BUFFER_SIZE = 1024;
    private final List<String> lines;
    private final int nbLines;
    private final String[] upperCaseLines;
    private final int timeout;
    private final String outFilename;
    private final InetSocketAddress serverAddress;
    private final DatagramChannel dc;
    private final AnswersLog answersLog;         // Thread-safe structure keeping track of missing responses

    public ClientIdUpperCaseUDPBurst(List<String> lines, int timeout, InetSocketAddress serverAddress, String outFilename) throws IOException {
        this.lines = lines;
        this.nbLines = lines.size();
        this.timeout = timeout;
        this.outFilename = outFilename;
        this.serverAddress = serverAddress;
        this.dc = DatagramChannel.open();
        dc.bind(null);
        this.upperCaseLines = new String[nbLines];
        this.answersLog = new AnswersLog(nbLines);
    }

    public static void usage() {
        System.out.println("Usage : ClientIdUpperCaseUDPBurst in-filename out-filename timeout host port ");
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 5) {
            usage();
            return;
        }

        String inFilename = args[0];
        String outFilename = args[1];
        int timeout = parseInt(args[2]);
        String host = args[3];
        int port = parseInt(args[4]);
        InetSocketAddress serverAddress = new InetSocketAddress(host, port);

        //Read all lines of inFilename opened in UTF-8
        List<String> lines = Files.readAllLines(Paths.get(inFilename), UTF8);
        //Create client with the parameters and launch it
        ClientIdUpperCaseUDPBurst client = new ClientIdUpperCaseUDPBurst(lines, timeout, serverAddress, outFilename);
        client.launch();
    }

    private void senderThreadRun() {
        ByteBuffer bbSender = ByteBuffer.allocateDirect(BUFFER_SIZE);
        try {
            for (; ; ) {
                for (var id : answersLog.getPositions()) {
                    Objects.checkIndex(id, nbLines);
                    bbSender.putLong(id).put(UTF8.encode(lines.get(id)));
                    bbSender.flip();
                    dc.send(bbSender, serverAddress);
                    bbSender.clear();
                }
                Thread.sleep(timeout);
            }
        } catch (AsynchronousCloseException | InterruptedException e) {
            logger.info("DatagramChanel closed");
        } catch (IOException e) {
            logger.severe("Hardware might be on fire, caused by" + e.getCause() + ", " + e.getMessage());
        }
    }

    public void launch() throws IOException {
        Thread senderThread = new Thread(this::senderThreadRun);
        senderThread.start();

        //body of the receiver thread
        ByteBuffer bbReceive = ByteBuffer.allocateDirect(BUFFER_SIZE);
        while (!answersLog.allAnswersAreReceived()) {
            dc.receive(bbReceive);
            bbReceive.flip();
            var id = bbReceive.getLong();
            var msg = UTF8.decode(bbReceive).toString();
            if (id >= 0 && id < nbLines) {
                upperCaseLines[Math.toIntExact(id)] = msg;
                answersLog.setSend(id);
            }
            bbReceive.clear();
        }
        senderThread.interrupt();
        logger.info("senderThread is correctly interrupted, all answers are received");
        Files.write(Paths.get(outFilename), Arrays.asList(upperCaseLines), UTF8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static class AnswersLog {
        //Thread-safe class handling the information about missing lines

        private final BitSet bitSet;
        private final int size;
        private int count = 0;

        public AnswersLog(int nbLines) {
            size = nbLines;
            bitSet = new BitSet(size);
        }

        public List<Integer> getPositions() {
            synchronized (bitSet) {
                return IntStream.range(0, size)
                        .filter(id -> !bitSet.get(id))
                        .boxed()
                        .toList();
            }
        }

        public void setSend(long position) {
            Objects.checkIndex(position, size);
            synchronized (bitSet) {
                if (!bitSet.get(Math.toIntExact(position))) {
                    bitSet.set(Math.toIntExact(position));
                    count++;
                }
            }
        }

        public boolean allAnswersAreReceived() {
            synchronized (bitSet) {
                return count == size;
            }
        }
    }
}