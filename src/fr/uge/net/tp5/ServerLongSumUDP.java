package fr.uge.net.tp5;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.HashMap;
import java.util.logging.Logger;


public class ServerLongSumUDP {

    private static final Logger logger = Logger.getLogger(ServerLongSumUDP.class.getName());
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final int BUFFER_SIZE = 1024;

    private final DatagramChannel dc;
    private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

    private final HashMap<InetSocketAddress, HashMap<Long , SessionSum>> map = new HashMap<>();

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
            return;
        }
    }

    public void serve() throws IOException {
        try {
            while (!Thread.interrupted()) {
                ;
            }
        } finally {
            dc.close();
        }
    }

    private static class SessionSum {
        private final BitSet bitSet;
        private final int totalOper;
        private long sum;
        private int alreadyReceive;

        public SessionSum(int totalOper){
            this.totalOper = totalOper;
            bitSet = new BitSet(this.totalOper);
            sum = 0;
            alreadyReceive = 0;
        }



        public void update(int idPosOper, long opValue){
            if(bitSet.get(idPosOper)){
                return;
            }
            bitSet.set(idPosOper);
            alreadyReceive++;
            sum += opValue;
        }

        /* Optional ? */
        public boolean allAnswersAreReceived(){
            return alreadyReceive == totalOper;
        }

        public long getSum(){
            return sum;
        }
    }
}

