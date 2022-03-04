package fr.uge.net.tp9;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


public class HTTPReader {

    private final Charset ASCII_CHARSET = StandardCharsets.US_ASCII;
    private final SocketChannel sc;
    private final ByteBuffer buffer;

    public HTTPReader(SocketChannel sc, ByteBuffer buffer) {
        this.sc = sc;
        this.buffer = buffer;
    }

    public static void main(String[] args) throws IOException {
        var charsetASCII = StandardCharsets.US_ASCII;
        var request = "GET / HTTP/1.1\r\n" + "Host: www.w3.org\r\n" + "\r\n";
        var sc = SocketChannel.open();
        sc.connect(new InetSocketAddress("www.w3.org", 80));
        sc.write(charsetASCII.encode(request));
        var buffer = ByteBuffer.allocate(50);
        var reader = new HTTPReader(sc, buffer);
        System.out.println(reader.readLineCRLF());
        System.out.println(reader.readLineCRLF());
        System.out.println(reader.readLineCRLF());
        sc.close();

        buffer = ByteBuffer.allocate(50);
        sc = SocketChannel.open();
        sc.connect(new InetSocketAddress("www.w3.org", 80));
        reader = new HTTPReader(sc, buffer);
        sc.write(charsetASCII.encode(request));
        System.out.println(reader.readHeader());
        sc.close();

        buffer = ByteBuffer.allocate(50);
        sc = SocketChannel.open();
        sc.connect(new InetSocketAddress("www.w3.org", 80));
        reader = new HTTPReader(sc, buffer);
        sc.write(charsetASCII.encode(request));
        var header = reader.readHeader();
        System.out.println(header);
        var content = reader.readBytes(header.getContentLength());
        content.flip();
        //System.out.println(header.getCharset().orElse(StandardCharsets.UTF_8).decode(content));
        sc.close();

        buffer = ByteBuffer.allocate(50);
        request = "GET / HTTP/1.1\r\n" + "Host: www.u-pem.fr\r\n" + "\r\n";
        sc = SocketChannel.open();
        sc.connect(new InetSocketAddress("www.u-pem.fr", 80));
        reader = new HTTPReader(sc, buffer);
        sc.write(charsetASCII.encode(request));
        header = reader.readHeader();
        System.out.println(header);
        content = reader.readChunks();
        content.flip();
        System.out.println(header.getCharset().orElse(StandardCharsets.UTF_8).decode(content));
        sc.close();
    }

    /**
     * @return The ASCII string terminated by CRLF without the CRLF
     * <p>
     * The method assume that buffer is in write mode and leaves it in
     * write mode The method process the data from the buffer and if necessary
     * will read more data from the socket.
     * @throws IOException HTTPException if the connection is closed before a line
     *                     could be read
     */
    public String readLineCRLF() throws IOException {
        char last;
        buffer.flip();
        try {
            var sb = new StringBuilder();
            for (; ; ) {
                readAgain();
                last = (char) buffer.get();
                if (last != '\r') {
                    sb.append(last);
                } else {
                    readAgain();
                    last = (char) buffer.get();
                    if (last == '\n') {
                        return sb.toString();
                    } else {
                        /* Don't forget to add \t and the last read byte */
                        sb.append('\r');
                        sb.append(last);
                    }
                }
            }
        } finally {
            buffer.compact();
        }
    }

    /**
     * Call a read on the socketChanel if the buffer has no remaining
     * The method assume that buffer is in read mode and leaves it in
     * read mode
     */
    private void readAgain() throws IOException {
        if (buffer.hasRemaining()) {
            return;
        }
        buffer.clear();
        if (sc.read(buffer) == -1) {
            System.out.println("Connexion closed");
            throw new HTTPException();
        }
        buffer.flip();
    }

    /**
     * @return The HTTPHeader object corresponding to the header read
     * @throws IOException HTTPException if the connection is closed before a header
     *                     could be read or if the header is ill-formed
     */
    public HTTPHeader readHeader() throws IOException {
        String response = readLineCRLF();
        Map<String, String> fields = new HashMap<>();
        String line = readLineCRLF();
        while (!Objects.equals(line, "")) {
            var content = line.split(":", 2);
            // We can't use computeIfAbsent because String is immutable
            if (fields.containsKey(content[0])) {
                fields.put(content[0], fields.get(content[0]).concat(";").concat(content[1]));
            } else {
                fields.put(content[0], content[1]);
            }
            line = readLineCRLF();
        }
        return HTTPHeader.create(response, fields);
    }

    /**
     * @param size - size of returned buffer
     * @return a ByteBuffer in write mode containing size bytes read on the socket
     * <p>
     * The method assume that buffer is in write mode and leaves it in
     * write mode The method process the data from the buffer and if necessary
     * will read more data from the socket.
     * @throws IOException HTTPException is the connection is closed before all
     *                     bytes could be read
     */
    public ByteBuffer readBytes(int size) throws IOException {
        ByteBuffer returnedBuffer = ByteBuffer.allocate(size);
        buffer.flip();
        try {
            while (returnedBuffer.hasRemaining()) {
                readAgain();
                if (buffer.remaining() <= returnedBuffer.remaining()) {
                    returnedBuffer.put(buffer);
                    continue;
                }
                int remaining = returnedBuffer.remaining();
                int oldLimit = buffer.limit(); // Save old limit to reset after
                buffer.limit(buffer.position() + remaining);
                returnedBuffer.put(buffer);
                buffer.limit(oldLimit);
            }
            return returnedBuffer;
        } finally {
            buffer.compact();
        }
    }

    /**
     * @return a ByteBuffer in write-mode containing a content read in chunks mode
     * @throws IOException HTTPException if the connection is closed before the end
     *                     of the chunks if chunks are ill-formed
     */

    public ByteBuffer readChunks() throws IOException {
        int size = 1024;
        ByteBuffer returnedBuffer = ByteBuffer.allocate(size);
        var line = readLineCRLF();
        while (!Objects.equals(line, "0")) {
            int sizeChunk = Integer.parseInt(line, 16);
            if (returnedBuffer.remaining() < sizeChunk) {
                size *= 2;
                var tmpBuffer = ByteBuffer.allocate(size);
                returnedBuffer.flip();
                tmpBuffer.put(returnedBuffer);
                returnedBuffer = tmpBuffer;
            }
            var chunk = readBytes(sizeChunk);
            chunk.flip();
            readBytes(2);
            returnedBuffer.put(chunk);
            line = readLineCRLF();
        }
        readBytes(2);
        return returnedBuffer;
    }
}