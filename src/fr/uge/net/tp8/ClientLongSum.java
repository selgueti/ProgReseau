package fr.uge.net.tp8;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.logging.Logger;

public class ClientLongSum {

    public static final Logger logger = Logger.getLogger(ClientLongSum.class.getName());

    private static List<Long> randomLongList(int size) {
        return new Random().longs(size).boxed().toList();
    }

    private static boolean checkSum(List<Long> list, long response) {
        return list.stream().reduce(Long::sum).orElse(0L) == response;
    }

    /**
     * Write all the longs in list in BigEndian on the server and read the long sent
     * by the server and returns it
     * <p>
     * returns Optional.empty if the protocol is not followed by the server but no
     * IOException is thrown
     *
     * @param sc
     * @param list
     * @return
     * @throws IOException
     */
    private static Optional<Long> requestSumForList(SocketChannel sc, List<Long> list) throws IOException {
        sendRequest(sc, list);
        var sizeBuffer = Long.BYTES;
        ByteBuffer receiveBuffer = ByteBuffer.allocate(sizeBuffer);
        if (!readFully(sc, receiveBuffer)) {
            return Optional.empty();
        }
        receiveBuffer.flip();
        return Optional.of(receiveBuffer.getLong());
    }

    private static void sendRequest(SocketChannel sc, List<Long> list) throws IOException {
        ByteBuffer sendBuffer = ByteBuffer.allocate(Integer.BYTES + list.size() * Long.BYTES);
        sendBuffer.putInt(list.size());
        list.forEach(sendBuffer::putLong);
        sendBuffer.flip();
        sc.write(sendBuffer);
    }

    /**
     * Fill the workspace of the Bytebuffer with bytes read from sc.
     *
     * @param sc
     * @param buffer
     * @return false if read returned -1 at some point and true otherwise
     * @throws IOException
     */
    static boolean readFully(SocketChannel sc, ByteBuffer buffer) throws IOException {
        while (-1 != sc.read(buffer) && buffer.hasRemaining()) ;
        return !buffer.hasRemaining();
    }

    public static void main(String[] args) throws IOException {
        var server = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
        try (var sc = SocketChannel.open(server)) {
            for (var i = 0; i < 5; i++) {
                var list = randomLongList(50);

                var sum = requestSumForList(sc, list);
                if (!sum.isPresent()) {
                    logger.warning("Connection with server lost.");
                    return;
                }
                if (!checkSum(list, sum.get())) {
                    logger.warning("Oups! Something wrong happened!");
                }
            }
            logger.info("Everything seems ok");
        }
    }
}