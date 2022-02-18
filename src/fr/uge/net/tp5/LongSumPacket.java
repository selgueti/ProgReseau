package fr.uge.net.tp5;

import java.nio.ByteBuffer;

public sealed interface LongSumPacket {

    int BUFFER_SIZE = 1024;

    /*
     * Return a ByteBuffer who represent the packet. The buffer is read mode.
     * */
    ByteBuffer toBuffer();

    record Op(long sessionID, long idPosOp, long totalOp, long opValue) implements LongSumPacket {
        @Override
        public ByteBuffer toBuffer() {
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            buffer.put((byte) 1).putLong(sessionID).putLong(idPosOp).putLong(totalOp).putLong(opValue);
            buffer.flip();
            return buffer;
        }
    }

    record Ack(long sessionID, long idPosOp) implements LongSumPacket {
        @Override
        public ByteBuffer toBuffer() {
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            buffer.put((byte) 2).putLong(sessionID).putLong(idPosOp);
            buffer.flip();
            return buffer;
        }
    }

    record Res(long sessionID, long sum) implements LongSumPacket {
        @Override
        public ByteBuffer toBuffer() {
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            buffer.put((byte) 3).putLong(sessionID).putLong(sum);
            buffer.flip();
            return buffer;
        }
    }

    record Clean(long sessionID) implements LongSumPacket {
        @Override
        public ByteBuffer toBuffer() {
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            buffer.put((byte) 4).putLong(sessionID);
            buffer.flip();
            return buffer;
        }
    }

    record AckClean(long sessionID) implements LongSumPacket {

        @Override
        public ByteBuffer toBuffer() {
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            buffer.put((byte) 5).putLong(sessionID);
            buffer.flip();
            return buffer;
        }
    }
}
