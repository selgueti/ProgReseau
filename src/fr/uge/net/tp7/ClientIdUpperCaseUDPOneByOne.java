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
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import static java.nio.file.StandardOpenOption.*;

public class ClientIdUpperCaseUDPOneByOne {

    private static final Logger logger = Logger.getLogger(ClientIdUpperCaseUDPOneByOne.class.getName());
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final int BUFFER_SIZE = 1024;

    private enum State {
        SENDING, RECEIVING, FINISHED
    }

    private final List<String> lines;
    private final List<String> upperCaseLines = new ArrayList<>();
    private final long timeout;
    private final InetSocketAddress serverAddress;
    private final DatagramChannel dc;
    private final Selector selector;
    private final SelectionKey uniqueKey;
    private State state;

    // TODO add new fields
    private final ByteBuffer bufferSender = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private final ByteBuffer bufferReceiver = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private long currentLine = 0L;
    private long lastSend;


    private static void usage() {
        System.out.println("Usage : ClientIdUpperCaseUDPOneByOne in-filename out-filename timeout host port ");
    }

    private ClientIdUpperCaseUDPOneByOne(List<String> lines, long timeout, InetSocketAddress serverAddress,
                                         DatagramChannel dc, Selector selector, SelectionKey uniqueKey){
        this.lines = lines;
        this.timeout = timeout;
        this.serverAddress = serverAddress;
        this.dc = dc;
        this.selector = selector;
        this.uniqueKey = uniqueKey;
        this.state = State.SENDING;
    }

    public static ClientIdUpperCaseUDPOneByOne create(String inFilename, long timeout,
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
        return new ClientIdUpperCaseUDPOneByOne(lines, timeout, serverAddress, dc, selector, uniqueKey);
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
                    return 0; // Attente aussi longue que necessaire

                } else {
                    uniqueKey.interestOps(SelectionKey.OP_READ);
                    return time;
                }
            }
            case SENDING -> {
                uniqueKey.interestOps(SelectionKey.OP_WRITE);
                return 0; // Attente aussi longue que necessaire
            }
        }
        return 0;
    }

    private boolean isFinished() {
        return state == State.FINISHED;
    }

    /**
     * Performs the receptions of packets
     *
     * @throws IOException
     */

    private void doRead() throws IOException {
        bufferReceiver.clear();
        var sender = dc.receive(bufferReceiver);
        if(sender == null){
            return;
        }
        /*if(!bufferReceiver.hasRemaining()){
            return;
        }*/
        var id = bufferReceiver.get();
        if(id < 0 || id != currentLine){
            return;
        }

        upperCaseLines.add(Math.toIntExact(currentLine), UTF8.decode(bufferReceiver).toString());
        currentLine++;
        if(currentLine == lines.size()){
            state =  State.FINISHED;
            return;
        }

        bufferSender.put(UTF8.encode(lines.get(Math.toIntExact(currentLine)).toUpperCase()));
        bufferSender.flip();
        state = State.SENDING;
    }

    /**
     * Tries to send the packets
     *
     * @throws IOException
     */

    private void doWrite() throws IOException {
        dc.send(bufferSender, serverAddress);
        if(bufferSender.hasRemaining()){
            return;
        }
        lastSend = System.currentTimeMillis();
        state = State.RECEIVING;
        bufferSender.flip(); // Pour garantir l'invariant que le bufferSender soit en mode lecture
    }
}