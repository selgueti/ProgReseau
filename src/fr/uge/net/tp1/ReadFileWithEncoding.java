package fr.uge.net.tp1;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ReadFileWithEncoding {

    private static void usage() {
        System.out.println("Usage: ReadFileWithEncoding charset filename");
    }

    private static String stringFromFile(Charset cs, Path path) throws IOException {
        try (FileChannel fc = FileChannel.open(path, StandardOpenOption.READ)){
            ByteBuffer bb = ByteBuffer.allocate((int)fc.size());
            while(bb.hasRemaining()){
                fc.read(bb);
            }
            bb.flip();
            return cs.decode(bb).toString();
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            usage();
            return;
        }
        var cs = Charset.forName(args[0]);
        var path = Path.of(args[1]);
        System.out.print(stringFromFile(cs, path));
    }
}
