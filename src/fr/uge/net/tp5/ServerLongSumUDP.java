package fr.uge.net.tp5;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static fr.uge.net.tp5.LongSumPacket.Ack;
import static fr.uge.net.tp5.LongSumPacket.Res;


public class ServerLongSumUDP {

    private static final Logger logger = Logger.getLogger(ServerLongSumUDP.class.getName());
    private static final int BUFFER_SIZE = 1024;

    private final DatagramChannel dc;
    private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

    private final Map<InetSocketAddress, Map<Long, SessionSum>> map = new HashMap<>();

    public ServerLongSumUDP(int port) throws IOException {
        dc = DatagramChannel.open();
        dc.bind(new InetSocketAddress(port));
        logger.info("ServerBetterUpperCaseUDP started on port " + port);
    }

    public static void usage() {
        System.out.println("Usage : ServerLongSumUDP port");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }

        var port = Integer.parseInt(args[0]);

        if (!(port >= 1024) & port <= 65535) {
            logger.severe("The port number must be between 1024 and 65535");
            return;
        }

        try {
            new ServerLongSumUDP(port).serve();
        } catch (BindException e) {
            logger.severe("Server could not bind on " + port + "\nAnother server is probably running on this port.");
        }
    }

    /*
     * Suppose buffer in read mode
     * */
    private void processOp(InetSocketAddress sender) throws IOException {
        if(buffer.limit() - buffer.position() != 4 * Long.BYTES){
            return;
        }
        var sessionId = buffer.getLong();
        var idPosOp = buffer.getLong();
        var totalOp = buffer.getLong();
        var opValue = buffer.getLong();
        var optionalSum = map.computeIfAbsent(sender, __ -> new HashMap<>())
                .computeIfAbsent(sessionId, __ -> new SessionSum(totalOp))
                .update(idPosOp, opValue);
        dc.send(new Ack(sessionId, idPosOp).toBuffer(), sender);
        if (optionalSum.isPresent()) {
            dc.send(new Res(sessionId, optionalSum.get()).toBuffer(), sender);
        }
    }

    public void serve() throws IOException {
        try {
            while (!Thread.interrupted()) {
                buffer.clear();
                var sender = (InetSocketAddress) dc.receive(buffer);
                if (buffer.position() > Byte.BYTES) {
                    buffer.flip();
                    var b = buffer.get();
                    if (b == 1) {
                        processOp(sender);
                    }
                }
            }
        } finally {
            dc.close();
        }
    }

    private static class SessionSum {
        private final BitSet bitSet;
        private final long totalOp;
        private long sum = 0;
        private int count = 0;

        private SessionSum(long totalOp) {
            this.totalOp = totalOp;
            bitSet = new BitSet(Math.toIntExact(this.totalOp));
        }

        private boolean allAnswersAreReceived() {
            return count == totalOp;
        }

        public Optional<Long> update(long idPosOp, long opValue) {
            if (!bitSet.get(Math.toIntExact(idPosOp))) {
                bitSet.set(Math.toIntExact(idPosOp));
                count++;
                sum += opValue;
            }
            if (allAnswersAreReceived()) {
                return Optional.of(sum);
            }
            return Optional.empty();
        }
    }
}

