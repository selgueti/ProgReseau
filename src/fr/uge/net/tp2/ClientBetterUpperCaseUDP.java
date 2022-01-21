package fr.uge.net.tp2;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;

public class ClientBetterUpperCaseUDP {
    private static final int MAX_PACKET_SIZE = 1024;

    private static final Charset ASCII_CHARSET = StandardCharsets.US_ASCII; //Charset.forName("US-ASCII");

    /**
     * Creates and returns an Optional containing a new ByteBuffer containing the encoded representation
     * of the String <code>msg</code> using the charset <code>charsetName</code>
     * in the following format:
     * - the size (as a Big Indian int) of the charsetName encoded in ASCII<br/>
     * - the bytes encoding this charsetName in ASCII<br/>
     * - the bytes encoding the String msg in this charset.<br/>
     * The returned ByteBuffer is in <strong>write mode</strong> (i.e. need to
     * be flipped before to be used).
     * If the buffer is larger than MAX_PACKET_SIZE bytes, then returns Optional.empty.
     *
     * @param msg         the String to encode
     * @param charsetName the name of the Charset to encode the String msg
     * @return an Optional containing a newly allocated ByteBuffer containing the representation of msg,
     * or an empty Optional if the buffer would be larger than 1024
     */
    public static Optional<ByteBuffer> encodeMessage(String msg, String charsetName) {
        Objects.requireNonNull(msg);
        Objects.requireNonNull(charsetName);
        var charset = Charset.forName(charsetName);
        var bb = ByteBuffer.allocate(MAX_PACKET_SIZE);
        try {
            bb.putInt(charsetName.length());            /* size of charsetName  */
            bb.put(ASCII_CHARSET.encode(charsetName));  /* charsetName in ASCII */
            bb.put(charset.encode(msg));                /* msg in this charset  */
        } catch (BufferOverflowException b) {
            return Optional.empty();
        }
        return Optional.of(bb);
    }

    /**
     * Creates and returns an Optional containing a String message represented by the ByteBuffer buffer,
     * encoded in the following representation:
     * - the size (as a Big Indian int) of a charsetName encoded in ASCII<br/>
     * - the bytes encoding this charsetName in ASCII<br/>
     * - the bytes encoding the message in this charset.<br/>
     * The accepted ByteBuffer buffer must be in <strong>write mode</strong>
     * (i.e. need to be flipped before to be used).
     *
     * @param buffer a ByteBuffer containing the representation of an encoded String message
     * @return an Optional containing the String represented by buffer, or an empty Optional if the buffer cannot be decoded
     */
    public static Optional<String> decodeMessage(ByteBuffer buffer) {
        Objects.requireNonNull(buffer);
        buffer.flip();
        if(buffer.capacity() > MAX_PACKET_SIZE){
            return Optional.empty();
        }
        try {
            var sizeCharset = buffer.getInt();
            if(sizeCharset < 0){
                return Optional.empty();
            }
            ByteBuffer tmp = ByteBuffer.allocate(sizeCharset);
            for(int i = 0; i < sizeCharset; i++){
                tmp.put(buffer.get());
            }
            tmp.flip();
            var charsetName = ASCII_CHARSET.decode(tmp).toString();
            if(!Charset.isSupported(charsetName)){
                return Optional.empty();
            }
            var charset = Charset.forName(charsetName);
            var msg = charset.decode(buffer).toString();
            return Optional.of(msg);

        }catch (BufferUnderflowException e){
            return Optional.empty();
        }
    }

    public static void usage() {
        System.out.println("Usage : ClientBetterUpperCaseUDP host port charsetName");
    }

    public static void main(String[] args) throws IOException {
        // check and retrieve parameters
        if (args.length != 3) {
            usage();
            return;
        }
        var host = args[0];
        var port = Integer.parseInt(args[1]);
        var charsetName = args[2];

        var destination = new InetSocketAddress(host, port);
        // buffer to receive messages
        var buffer = ByteBuffer.allocateDirect(MAX_PACKET_SIZE);

        try (var scanner = new Scanner(System.in);
             var dc = DatagramChannel.open()) {
            while (scanner.hasNextLine()) {
                var line = scanner.nextLine();

                var message = encodeMessage(line, charsetName);
                if (message.isEmpty()) {
                    System.out.println("Line is too long to be sent using the protocol BetterUpperCase");
                    continue;
                }
                var packet = message.get();
                packet.flip();
                dc.send(packet, destination);
                buffer.clear();
                dc.receive(buffer);

                // buffer.flip();

                decodeMessage(buffer).ifPresentOrElse(
                        (str) -> System.out.println("Received: " + str),
                        () -> System.out.println("Received an invalid packet"));
            }
        }
    }
}
