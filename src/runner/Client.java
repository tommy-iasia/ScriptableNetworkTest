package runner;

import utility.*;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

public class Client {

    public static void main(String[] args) throws IOException, InterruptedException {
        logFile = new LogFile("client");
        log("start");

        String host = args[0];
        int port = Integer.parseInt(args[1]);

        String file = args[2];
        String instruction = TextFile.read(file);

        runClient(host, port, instruction);

        log("end");
        logFile.close();
    }

    private static LogFile logFile;
    private static void log(String line) throws IOException {
        logFile.add(line);
    }

    private static void runClient(String host, int port, String instruction) throws IOException, InterruptedException {
        SocketChannel channel = SocketChannel.open();

        InetAddress hostAddress = InetAddress.getByName(host);
        InetSocketAddress socketAddress = new InetSocketAddress(hostAddress, port);

        log("connecting to " + socketAddress + " ...");
        if (!channel.connect(socketAddress)) {
            log("fail to connect to " + socketAddress);
            return;
        }

        log("sending instructions...");
        sendInstruction(channel, instruction);

        channel.configureBlocking(false);

        log("start execution");
        runInstructions(instruction, channel);

        log("execution completed");
        channel.close();;
    }
    private static void sendInstruction(SocketChannel channel, String instruction) throws IOException {
        ByteBuffer instructionBuffer = ByteBuffer.allocate(Server.instructionSize);

        byte[] instructionBytes = instruction.getBytes("UTF-8");

        if (instructionBytes.length > instructionBuffer.capacity()) {
            throw new RuntimeException("instruction is too long.");
        }

        instructionBuffer.put(instructionBytes);

        while (instructionBuffer.remaining() > 0) {
            instructionBuffer.put((byte) 0);
        }

        instructionBuffer.flip();
        channel.write(instructionBuffer);
    }

    private static void runInstructions(String instructionsText, SocketChannel channel) throws IOException, InterruptedException {
        String[] instructions = instructionsText.split("\r?\n");

        int i = 0;
        for (String instruction : instructions) {
            String content = instruction.trim();

            log(++i + ") " + content);

            if (content.length() > 0) {
                runInstruction(content, channel);
            }
        }
    }
    private static void runInstruction(String instruction, SocketChannel channel) throws IOException, InterruptedException {
        if (instruction.startsWith("#") || instruction.startsWith("//") || instruction.startsWith("--")) {
            log("skip comment: " + instruction);
            return;
        }

        RegexMatch sleepMatch = RegexMatch.create(instruction, "sleep (\\d+)");
        if (sleepMatch.find()) {
            int length = sleepMatch.getInt(1);

            sleep(length);
            return;
        }

        RegexMatch udpFloodMatch = RegexMatch.create(instruction, "flood (?:UDP|udp) (\\S+) (\\d+) (\\d+) (\\d+)(?: (\\d+))?");
        if (udpFloodMatch.find()) {
            String group = udpFloodMatch.get(1);
            int port = udpFloodMatch.getInt(2);

            long time = udpFloodMatch.getLong(3);
            int bufferSize = udpFloodMatch.getInt(4);

            floodUdp(channel, group, port, time, bufferSize);
            return;
        }

        RegexMatch tcpFloodMatch = RegexMatch.create(instruction, "flood(?: TCP| tcp)? (\\d+) (\\d+)(?: (\\d+))?");
        if (tcpFloodMatch.find()) {
            long time = tcpFloodMatch.getLong(1);
            int bufferSize = tcpFloodMatch.getInt(2);

            floodTcp(channel, time, bufferSize);
            return;
        }

        RegexMatch syncMatch = RegexMatch.create(instruction, "sync");
        if (syncMatch.find()) {
            sync(channel);
            return;
        }

        RegexMatch holdMatch = RegexMatch.create(instruction, "hold (server|client) (\\d+)");
        if (holdMatch.find()) {
            String side = holdMatch.get(1);
            if (side.equals("client")) {
                int count = holdMatch.getInt(2);
                hold(count);
            } else {
                log("skip server hold");
            }
            return;
        }

        RegexMatch releaseMatch = RegexMatch.create(instruction, "release (server|client) (\\d+)");
        if (releaseMatch.find()) {
            String side = releaseMatch.get(1);
            if (side.equals("client")) {
                int count = releaseMatch.getInt(2);
                release(count);
            } else {
                log("skip server release");
            }
            return;
        }

        log("unrecognized instruction: " + instruction);
    }

    private static void sleep(int length) throws InterruptedException, IOException {
        log("sleep " + length + "ms");
        Thread.sleep(length);
    }

    private static void floodTcp(SocketChannel channel, long timeLength, int bufferSize) throws IOException {
        log("receive flood through TCP for " + timeLength + "ms with " + bufferSize + "B buffer...");

        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);

        long startTime = System.currentTimeMillis();
        long lastTime;

        long readLength = 0;
        long readTime = 0;
        long count = 0;

        while (true) {
            lastTime = System.currentTimeMillis();
            if (lastTime - startTime > timeLength) {
                break;
            }

            long readStart = System.nanoTime();
            int read = channel.read(buffer);
            long readEnd = System.nanoTime();

            if (read > 0) {
                buffer.flip();

                boolean valid = false;
                while (buffer.remaining() > 0) {
                    byte content = buffer.get();
                    if (content == Server.floodContent) {
                        valid = true;
                        break;
                    }
                }

                buffer.rewind();

                if (valid) {
                    readLength += read;
                    readTime += readEnd - readStart;
                    count++;
                }
            }
        }

        long usedTime = lastTime - startTime;
        log("used " + usedTime / 1000 + "s / " + usedTime + "ms");

        log("read "
                + readLength / 1024 / 1024 + "MB / " + readLength + "B / "
                + count / 1000 + "kp / " + count + "p / "
                + readTime / 1000 / 1000 + "ms / " + readTime + "ns");

        long bandwidth = 8 * readLength * 1000 / (usedTime > 0 ? usedTime : 1);
        log("bandwidth " + bandwidth / 1024 / 1024 + "Mbps / " + bandwidth + "bps");

        log("flood completed");
    }

    private static void floodUdp(SocketChannel channel, String group, int port, long timeLength, final int bufferSize) throws IOException {
        log("receive flood through UDP for " + timeLength + "ms with " + bufferSize + "B buffer...");

        MulticastSocket socket = new MulticastSocket(port);
        socket.setReceiveBufferSize(bufferSize);

        socket.setInterface(channel.socket().getLocalAddress());

        InetAddress groupAddress = InetAddress.getByName(group);
        socket.joinGroup(groupAddress);

        final FloodUdp flood = new FloodUdp(socket, timeLength, bufferSize);

        Thread thread = new Thread(flood);
        thread.start();

        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeLength) {
            try { TimeUnit.MILLISECONDS.sleep(1); }
            catch (InterruptedException ignore) { }
        }

        socket.close();

        synchronized (flood.synchronizer) {
            long usedTime = flood.lastTime - startTime;
            log("used " + usedTime / 1000 + "s / " + usedTime + "ms");

            log("read "
                    + flood.readLength / 1024 / 1024 + "MB / " + flood.readLength + "B / "
                    + flood.count / 1000 + "kp / " + flood.count + "p / "
                    + flood.readTime / 1000 / 1000 + "ms / " + flood.readTime + "ns");

            long bandwidth = 8 * flood.readLength * 1000 / (usedTime > 0 ? usedTime : 1);
            log("bandwidth " + bandwidth / 1024 / 1024 + "Mbps / " + bandwidth + "bps");

            log("flood completed");
        }
    }
    static class FloodUdp implements Runnable {
        FloodUdp(MulticastSocket socket, long timeLength, int bufferSize) {
            this.socket = socket;

            this.timeLength = timeLength;

            buffer = new byte[bufferSize];
        }

        private final MulticastSocket socket;

        public final Object synchronizer = new Object();

        private final long timeLength;
        public long startTime = System.currentTimeMillis();
        public long lastTime = -1;

        public long readLength = 0;
        public long readTime = 0;
        public long count = 0;

        private final byte[] buffer;

        @Override
        public void run() {
            while (true) {
                synchronized (synchronizer) {
                    lastTime = System.currentTimeMillis();
                }
                if (lastTime - startTime > timeLength) {
                    break;
                }

                long readStart = System.nanoTime();

                DatagramPacket packet = new DatagramPacket(buffer, 0, buffer.length);
                try {
                    socket.receive(packet);
                } catch (IOException e) {
                    break;
                }

                long readEnd = System.nanoTime();

                if (packet.getLength() > 0) {
                    boolean valid = false;

                    for (int i = packet.getLength() - 1; i >= 0; i--) {
                        if (buffer[i] == Server.floodContent) {
                            valid = true;
                            break;
                        }
                    }

                    if (valid) {
                        synchronized (synchronizer) {
                            readLength += packet.getLength();
                            readTime += readEnd - readStart;
                            count++;
                        }
                    }
                }
            }
        }
    }

    private static void sync(SocketChannel channel) throws IOException {
        log("wait for sync start...");
        SocketChannelUtility.lookFor(channel, Server.syncStart);
        SocketChannelUtility.writeByte(channel, Server.syncStart);
        log("sync start received.");

        log("wait for sync end...");
        SocketChannelUtility.readUntil(channel, Server.syncEnd);
        SocketChannelUtility.writeByte(channel, Server.syncEnd);
        log("sync end received.");
    }

    private final static HoldMemory holdMemory = new HoldMemory();
    private static void hold(int count) throws IOException {
        log("hold " + count / 1024 / 1024 + "MB / " + count + "B");

        holdMemory.hold(count);

        long held = holdMemory.held();
        log("total held " + held / 1024 / 1024 + "MB / " + held + "B");
    }
    private static void release(int count) throws IOException {
        log("release " + count / 1024 / 1024 + "MB / " + count + "B");

        holdMemory.release(count);

        long held = holdMemory.held();
        log("total held " + held / 1024 / 1024 + "MB / " + held + "B");
    }
}
