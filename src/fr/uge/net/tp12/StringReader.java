package fr.uge.net.tp12;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class StringReader implements Reader<String>{

    private enum State {
        DONE, WAITING, ERROR
    };

    private State state = State.WAITING;
    private static final int BUFFER_SIZE = 1_024;
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private final ByteBuffer internalBuffer = ByteBuffer.allocate(BUFFER_SIZE); // write-mode
    private String value;
    private int size;
    private boolean firstPassage = true;


    @Override
    public ProcessStatus process(ByteBuffer buffer) {

        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        buffer.flip();
        try {
            if(firstPassage && buffer.remaining() >= Integer.BYTES){
                size = buffer.getInt();
                firstPassage = false;
            }
            if (buffer.remaining() <= internalBuffer.remaining()) {
                //size -= buffer.remaining();
                internalBuffer.put(buffer);
            } else {
                var oldLimit = buffer.limit();
                buffer.limit(internalBuffer.remaining());
                //size -= internalBuffer.remaining();
                internalBuffer.put(buffer);
                buffer.limit(oldLimit);
            }
        } finally {
            buffer.compact();
        }
        System.out.println("position = " + internalBuffer.position() + ", size = " + size);
        if (!firstPassage && internalBuffer.position() != size) {
            return ProcessStatus.REFILL;
        }
        state = State.DONE;
        internalBuffer.flip();
        value = UTF8.decode(internalBuffer).toString();
        //internalBuffer.compact(); // maybe clear ?
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
    }
}
