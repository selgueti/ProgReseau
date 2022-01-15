package fr.uge.net.tp1;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;

public class ReadStandardInputWithEncoding {

	private static final int BUFFER_SIZE = 1024;

	private static void usage() {
		System.out.println("Usage: ReadStandardInputWithEncoding charset");
	}

	private static String stringFromStandardInput(Charset cs) throws IOException {
		int size = BUFFER_SIZE;
		ReadableByteChannel in = Channels.newChannel(System.in);
		ByteBuffer bb = ByteBuffer.allocate(size);
		while(in.read(bb) != -1) {
			if (!bb.hasRemaining()) {
				size *= 2;
				var old = bb;
				bb = ByteBuffer.allocate(size);
				old.flip();
				bb.put(old);
			}
		}
		bb.flip();
		return cs.decode(bb).toString();
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			usage();
			return;
		}
		Charset cs = Charset.forName(args[0]);
		System.out.print(stringFromStandardInput(cs));
	}
}