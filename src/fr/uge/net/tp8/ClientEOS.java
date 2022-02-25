package fr.uge.net.tp8;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class ClientEOS {

    public static final Charset UTF8_CHARSET = StandardCharsets.UTF_8;
    public static final int BUFFER_SIZE = 1024;
    private static int sizeBuffer = BUFFER_SIZE;
    public static final Logger logger = Logger.getLogger(ClientEOS.class.getName());


    /**
     * This method:
     * - connect to server
     * - writes the bytes corresponding to request in UTF8
     * - closes the write-channel to the server
     * - stores the bufferSize first bytes of server response
     * - return the corresponding string in UTF8
     *
     * @param request
     * @param server
     * @param bufferSize
     * @return the UTF8 string corresponding to bufferSize first bytes of server
     *         response
     * @throws IOException
     */

    public static String getFixedSizeResponse(String request, SocketAddress server, int bufferSize) throws IOException {
        try(SocketChannel sc = SocketChannel.open()){
            sc.connect(server);
            ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
            sc.write(UTF8_CHARSET.encode(request));
            sc.shutdownOutput();
            readFully(sc, buffer);
            buffer.flip();
            return UTF8_CHARSET.decode(buffer).toString();
        }
    }

    /**
     * This method:
     * - connect to server
     * - writes the bytes corresponding to request in UTF8
     * - closes the write-channel to the server
     * - reads and stores all bytes from server until read-channel is closed
     * - return the corresponding string in UTF8
     *
     * @param request
     * @param server
     * @return the UTF8 string corresponding the full response of the server
     * @throws IOException
     */

    public static String getUnboundedResponse(String request, SocketAddress server) throws IOException {
        try(SocketChannel sc = SocketChannel.open()){
            sc.connect(server);
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            sc.write(UTF8_CHARSET.encode(request));
            sc.shutdownOutput();
            while(readFully(sc, buffer)){
                sizeBuffer *= 2;
                var tmp = ByteBuffer.allocate(sizeBuffer);
                buffer.flip();
                tmp.put(buffer);
                buffer = tmp;
            }
            buffer.flip();
            return UTF8_CHARSET.decode(buffer).toString();
        }
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
        while(-1 != sc.read(buffer) && buffer.hasRemaining());
        return !buffer.hasRemaining();
    }

    public static void main(String[] args) throws IOException {
        var google = new InetSocketAddress("www.google.fr", 80);
        //System.out.println(getFixedSizeResponse("GET / HTTP/1.1\r\nHost: www.google.fr\r\n\r\n", google, 512));
        System.out.println(getUnboundedResponse("GET / HTTP/1.1\r\nHost: www.google.fr\r\n\r\n", google));
    }
}
