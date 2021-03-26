package utility;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class SocketChannelUtility {

    public static void writeByte(SocketChannel channel, byte value) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(1);
        buffer.put(value);
        buffer.flip();

        channel.write(buffer);
    }
    public static Byte readByte(SocketChannel channel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1);
        if (channel.read(buffer) <= 0) {
            return null;
        }

        buffer.flip();

        return buffer.get();
    }

    public static void lookFor(SocketChannel channel, byte value) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(8192);

        while (true) {
            if (channel.read(buffer) > 0) {
                buffer.flip();

                while (buffer.remaining() > 0) {
                    if (buffer.get() == value) {
                        return;
                    }
                }

                buffer.compact();
            }
        }
    }
    public static void readUntil(SocketChannel channel, byte value) throws IOException {
        while (true) {
            Byte content = SocketChannelUtility.readByte(channel);
            if (content != null && content == value) {
                break;
            }
        }
    }
}
