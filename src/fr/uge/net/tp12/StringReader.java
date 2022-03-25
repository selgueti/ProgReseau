package fr.uge.net.tp12;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class StringReader implements Reader<String> {

    private static final int BUFFER_SIZE = 1_024;
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private final ByteBuffer sizeBuffer = ByteBuffer.allocate(Integer.BYTES); // write-mode
    private final ByteBuffer internalBuffer = ByteBuffer.allocate(BUFFER_SIZE); // write-mode
    private State state = State.WAITING;
    private String value;
    private int size;
    private boolean sizeIsSet = false;

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        buffer.flip();
        try {
            while (sizeBuffer.hasRemaining() && buffer.hasRemaining() && !sizeIsSet) {
                sizeBuffer.put(buffer.get());
            }
            if (!sizeBuffer.hasRemaining() && !sizeIsSet) {
                sizeBuffer.flip();
                size = sizeBuffer.getInt();
                sizeIsSet = true;
            }
            if (size < 0 || size > BUFFER_SIZE) {
                state = State.ERROR;
                return ProcessStatus.ERROR;
            }
            while (internalBuffer.hasRemaining() && buffer.hasRemaining() && size != internalBuffer.position()) {
                internalBuffer.put(buffer.get());
            }
        } finally {
            buffer.compact();
        }
        if (!sizeIsSet || (internalBuffer.position() < size)) {
            return ProcessStatus.REFILL;
        }
        state = State.DONE;
        internalBuffer.flip();
        value = UTF8.decode(internalBuffer).toString();
        internalBuffer.clear();
        return ProcessStatus.DONE;
    }

    @Override
    public String get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state = State.WAITING;
        internalBuffer.clear();
        sizeBuffer.clear();
        size = 0;
        sizeIsSet = false;
        value = null;
    }

    private enum State {
        DONE, WAITING, ERROR
    }
}
