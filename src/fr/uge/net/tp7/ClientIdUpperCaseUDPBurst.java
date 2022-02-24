package fr.uge.net.tp7;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import static java.nio.file.StandardOpenOption.*;

public class ClientIdUpperCaseUDPBurst {

    private static final Logger logger = Logger.getLogger(ClientIdUpperCaseUDPBurst.class.getName());
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final int BUFFER_SIZE = 1024;
    private final List<String> lines;
    private final ArrayList<String> upperCaseLines = new ArrayList<>();
    private final long timeout;
    private final InetSocketAddress serverAddress;
    private final DatagramChannel dc;
    private final Selector selector;
    private final SelectionKey uniqueKey;
    // TODO add new fields
    private final ResponsesLog responsesLog;
    private final ByteBuffer sendBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private final ByteBuffer receiveBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private State state;
    private long lastSend = 0L;
    private long currentLine = 0;
    private ClientIdUpperCaseUDPBurst(List<String> lines, long timeout, InetSocketAddress serverAddress,
                                      DatagramChannel dc, Selector selector, SelectionKey uniqueKey) {
        this.lines = lines;
        this.timeout = timeout;
        this.serverAddress = serverAddress;
        this.dc = dc;
        this.selector = selector;
        this.uniqueKey = uniqueKey;
        this.state = State.SENDING;
        this.responsesLog = new ResponsesLog(lines.size());
    }

    private static void usage() {
        System.out.println("Usage : ClientIdUpperCaseUDPBurst in-filename out-filename timeout host port ");
    }

    public static ClientIdUpperCaseUDPBurst create(String inFilename, long timeout,
                                                   InetSocketAddress serverAddress) throws IOException {
        Objects.requireNonNull(inFilename);
        Objects.requireNonNull(serverAddress);
        Objects.checkIndex(timeout, Long.MAX_VALUE);

        // Read all lines of inFilename opened in UTF-8
        var lines = Files.readAllLines(Path.of(inFilename), UTF8);
        var dc = DatagramChannel.open();
        dc.configureBlocking(false);
        dc.bind(null);
        var selector = Selector.open();
        var uniqueKey = dc.register(selector, SelectionKey.OP_WRITE);
        return new ClientIdUpperCaseUDPBurst(lines, timeout, serverAddress, dc, selector, uniqueKey);
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
        var upperCaseLines = create(inFilename, timeout, server).launch();

        Files.write(Path.of(outFilename), upperCaseLines, UTF8, CREATE, WRITE, TRUNCATE_EXISTING);
    }

    private List<String> launch() throws IOException {
        try {
            while (!isFinished()) {
                try {
                    selector.select(this::treatKey, updateInterestOps());
                } catch (UncheckedIOException tunneled) {
                    throw tunneled.getCause();
                }
            }
            return upperCaseLines;
        } finally {
            dc.close();
        }
    }

    private void treatKey(SelectionKey key) {
        try {
            if (key.isValid() && key.isWritable()) {
                doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                doRead();
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    /**
     * Updates the interestOps on key based on state of the context
     *
     * @return the timeout for the next select (0 means no timeout)
     */

    private long updateInterestOps() {
        switch (state) {
            case RECEIVING -> {
                var time = lastSend + timeout - System.currentTimeMillis();
                if (time <= 0) {
                    state = State.SENDING;
                    uniqueKey.interestOps(SelectionKey.OP_WRITE);
                    return 0; // Waiting as long as necessary

                } else {
                    uniqueKey.interestOps(SelectionKey.OP_READ);
                    return time;
                }
            }
            case SENDING -> {
                uniqueKey.interestOps(SelectionKey.OP_WRITE);
                return 0; // Waiting as long as necessary
            }
        }
        return 0;
    }

    private boolean isFinished() {
        return state == State.FINISHED;
    }

    private void fillSendBuffer() {
        sendBuffer.clear();
        sendBuffer.putLong(currentLine);
        sendBuffer.put(UTF8.encode(lines.get(Math.toIntExact(currentLine))));
        sendBuffer.flip();
    }

    /**
     * Performs the receptions of packets
     *
     * @throws IOException – If some I/O error occurs
     */

    private void doRead() throws IOException {
        receiveBuffer.clear();
        var sender = dc.receive(receiveBuffer);
        if (null == sender) {
            logger.info("Selector lied, we have no data in the system buffer");
            return;
        }
        receiveBuffer.flip();
        var id = receiveBuffer.getLong();
        if (id < 0 || id >= lines.size()) {
            return;
        }
        logger.info("id = " + id);
        logger.info("upperCaseLines.size() = " + upperCaseLines.size());
        upperCaseLines.set(Math.toIntExact(id), UTF8.decode(receiveBuffer).toString());
        responsesLog.setReceive(id);
        if (responsesLog.allResponsesAreReceived()) {
            state = State.FINISHED;
            logger.info("Processing finished");
        }
    }

    /**
     * Tries to send the packets
     *
     * @throws IOException – If some I/O error occurs
     */

    private void doWrite() throws IOException {

        fillSendBuffer();
        dc.send(sendBuffer, serverAddress);
        if (sendBuffer.hasRemaining()) {
            logger.info("Message don't effect send.");
            return;
        }

        currentLine++;
        if (currentLine >= lines.size()) {
            lastSend = System.currentTimeMillis();
            state = State.RECEIVING;
            currentLine = 0; // for next burst
        }

        //sendBuffer.flip(); // To guarantee the invariant that the bufferSender is in read mode
    }

    private enum State {
        SENDING, RECEIVING, FINISHED
    }

    private static class ResponsesLog {
        //Class handling the information about missing lines

        private final BitSet bitSet;
        private final int size;
        private int count = 0;

        public ResponsesLog(int nbLines) {
            size = nbLines;
            bitSet = new BitSet(size);
        }

        public void setReceive(long position) {
            Objects.checkIndex(position, size);
            if (!bitSet.get(Math.toIntExact(position))) {
                bitSet.set(Math.toIntExact(position));
                count++;
            }
        }

        public boolean allResponsesAreReceived() {
            return count == size;
        }
    }
}