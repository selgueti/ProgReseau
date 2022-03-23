package fr.uge.net.tp12;

import java.nio.ByteBuffer;

public interface Reader<T> {

    enum ProcessStatus { DONE, REFILL, ERROR };

    ProcessStatus process(ByteBuffer bb);

    T get();

    void reset();

}
