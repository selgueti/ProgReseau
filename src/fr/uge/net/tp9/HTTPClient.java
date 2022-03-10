package fr.uge.net.tp9;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class HTTPClient {

    private static final Charset ASCII_CHARSET = StandardCharsets.US_ASCII;
    private final SocketChannel sc;
    private final String request;
    private final HTTPReader reader;

    public HTTPClient(String hostname, String resource) throws IOException {
        this.request = "GET /" + resource + " HTTP/1.1\r\n" + "Host: " + hostname + "\r\n" + "\r\n";
        sc = SocketChannel.open();
        sc.connect(new InetSocketAddress(hostname, 80));
        reader = new HTTPReader(sc, ByteBuffer.allocateDirect(50));
    }

    public static void main(String[] args) throws IOException {
        var hostname = "www-igm.univ-mlv.fr";
        var resource = "/~carayol/redirect.php";
        new HTTPClient(hostname, resource).process();
    }

    public void process() throws IOException {
        HTTPHeader header = sendRequest();
        System.out.println(header);
        if((header.getCode() == 301 || header.getCode() == 302 ) && header.getFields().containsKey("location")){
            var url = new URL(header.getFields().get("location"));
            var host = url.getHost();
            var resource = url.getPath();
            System.out.println("CODE 301/302... but you can retrive the resource at " + host + resource);
            new HTTPClient(host, resource).process();
        }
        else if (header.getContentLength() != -1) {
            displayResourceWithContentLength(header);
        }else if(header.isChunkedTransfer()){
            displayResourceChunked(header);
        }else{
            System.out.println("Header hasn't content length and is not chunked transfer ...");
        }
        sc.close();
    }

    private void displayResourceChunked(HTTPHeader header) throws IOException {
        var content = reader.readChunks();
        content.flip();
        System.out.println(header.getCharset().orElse(StandardCharsets.UTF_8).decode(content));
        sc.close();
    }

    private void displayResourceWithContentLength(HTTPHeader header) throws IOException {
        var content = reader.readBytes(header.getContentLength());
        content.flip();
        System.out.println(header.getCharset().orElse(StandardCharsets.UTF_8).decode(content));
    }

    private HTTPHeader sendRequest() throws IOException {
        sc.write(ASCII_CHARSET.encode(request));
        return reader.readHeader();
    }
}
