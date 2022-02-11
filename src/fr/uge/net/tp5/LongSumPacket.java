package fr.uge.net.tp5;

import java.nio.ByteBuffer;

public interface LongSumPacket {

    int BUFFER_SIZE = 1024;

    /*
    * Return a ByteBuffer who represent the packet. The buffer is already flipped.
    * */
    ByteBuffer toBuffer();

    record OP(long sessionID, long idPosOper, long totalOper, long opValue) implements  LongSumPacket{
        @Override
        public ByteBuffer toBuffer() {
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            buffer.put((byte)1).putLong(sessionID).putLong(idPosOper).putLong(totalOper).putLong(opValue);
            buffer.flip();
            return buffer;
        }
    }

    record ACK(long sessionID, long idPosOper) implements  LongSumPacket{

        @Override
        public ByteBuffer toBuffer() {
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            buffer.put((byte)2).putLong(sessionID).putLong(idPosOper);
            buffer.flip();
            return buffer;
        }
    }

    record RES(long sessionID, long sum) implements  LongSumPacket{

        @Override
        public ByteBuffer toBuffer() {
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            buffer.put((byte)3).putLong(sessionID).putLong(sum);
            buffer.flip();
            return buffer;
        }
    }
}
