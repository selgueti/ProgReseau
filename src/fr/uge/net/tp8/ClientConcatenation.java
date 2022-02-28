package fr.uge.net.tp8;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.logging.Logger;

public class ClientConcatenation {

    public static final Logger logger = Logger.getLogger(ClientLongSum.class.getName());
    public static final Charset UTF8 = StandardCharsets.UTF_8;

    /**
     * Reads keyboard until reading the empty string
     * @return a list of strings without the last empty string
     */
    private static List<String> readClavier() {
        Scanner scanner = new Scanner(System.in);
        var stringList = new ArrayList<String>();
        while(scanner.hasNext()){
            var string = scanner.next();
            stringList.add(string);
        }
        return stringList;
    }

    /**
     * Sends request
     * @param sc - SocketChannel which represents the connexion
     * @param list - List<String> list of string to be concatenated
     * @throws IOException - If some I/O error occurs
     * */
    private static int sendRequest(SocketChannel sc, List<String> list) throws IOException {
        int sizeBuffer = (list.size() + 1) * Integer.BYTES;
        var bufferList = list.stream().map(UTF8::encode).toList();
        for (var bb: bufferList) {
            sizeBuffer += bb.remaining();
        }
        ByteBuffer sendBuffer = ByteBuffer.allocate(sizeBuffer);
        sendBuffer.putInt(list.size());
        bufferList.forEach(bb -> {
            sendBuffer.putInt(bb.remaining());
            sendBuffer.put(bb);
        });
        sendBuffer.flip();
        sc.write(sendBuffer);
        return sizeBuffer - (list.size() + 1) * Integer.BYTES;
    }

    /**
     * Sends request and wait the response if server hasn't closed connexion
     * @param sc - SocketChannel which represents the connexion
     * @param list - List<String> list of string to be concatenated
     * @return Options.empty() if the server has closed connexion, otherwise returns the concatenation
     * @throws IOException - If some I/O error occurs
     */
    private static Optional<String> requestConcatenationForList(SocketChannel sc, List<String> list) throws IOException{
        var sizeBuffer = sendRequest(sc, list);
        var receiveBuffer = ByteBuffer.allocate(Integer.BYTES);

        if(!readFully(sc, receiveBuffer)){
            logger.info("Connexion closed before processing");
            return Optional.empty();
        }
        receiveBuffer.flip();
        sizeBuffer = receiveBuffer.getInt();
        receiveBuffer = ByteBuffer.allocate(sizeBuffer);
        if(!readFully(sc, receiveBuffer)){
            logger.info("Connexion closed before processing");
            return Optional.empty();
        }
        receiveBuffer.flip();
        var msg = UTF8.decode(receiveBuffer).toString();
        System.out.println(msg);
        return Optional.of(msg);
    }

    /**
     * Fill the workspace with the Bytebuffer with bytes read from sc.
     *
     * @param sc - SocketChannel which represents the connexion
     * @param buffer - Buffer to fill
     * @return false if read returned -1 at some point and true otherwise
     * @throws IOException – If some I/O error occurs
     */
    static boolean readFully(SocketChannel sc, ByteBuffer buffer) throws IOException {
        while(-1 != sc.read(buffer)){
            if(!buffer.hasRemaining()){
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) throws IOException {
        var server = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
        try (var sc = SocketChannel.open(server)) {

            var stringsList = readClavier();
            var response = requestConcatenationForList(sc, stringsList);
            if(response.isEmpty()){
                logger.warning("Connection with server lost.");
            }else{
                logger.info("Everything seems ok");
                System.out.println(response.get());
            }
        }
    }
}