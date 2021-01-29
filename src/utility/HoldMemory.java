package utility;

import java.nio.ByteBuffer;
import java.util.Stack;

public class HoldMemory {

    private final Stack<ByteBuffer> buffers = new Stack<ByteBuffer>();
    public long held() {
        long total = 0;

        for (ByteBuffer buffer : buffers) {
            total += buffer.capacity();
        }

        return total;
    }

    public void hold(int count) {
        ByteBuffer buffer = ByteBuffer.allocate(count);
        buffers.push(buffer);
    }
    public void release(int count) {
        int releasing = count;
        while (releasing > 0) {
            ByteBuffer buffer = buffers.pop();
            releasing -= buffer.capacity();
        }

        if (releasing < 0) {
            ByteBuffer remaining = ByteBuffer.allocate(-releasing);
            buffers.push(remaining);
        }
    }
}
