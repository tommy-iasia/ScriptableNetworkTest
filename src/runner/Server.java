package runner;

import utility.*;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class Server {

    public static void main(String[] args) throws IOException, InterruptedException {
        logFile = new LogFile("server");
        log("start");

        String host = args[0];
        int port = Integer.parseInt(args[1]);

        runServer(host, port);

        log("end");
        logFile.close();
    }

    private static LogFile logFile;
    private static void log(String line) throws IOException {
        logFile.add(line);
    }

    public static void runServer(String host, int port) throws IOException, InterruptedException {
        ServerSocketChannel serverChannel = ServerSocketChannel.open();

        InetAddress hostAddress = InetAddress.getByName(host);
        InetSocketAddress socketAddress = new InetSocketAddress(hostAddress, port);

        log("bind to " + socketAddress);
        serverChannel.socket().bind(socketAddress);

        log("accepting new client...");
        SocketChannel clientChannel = serverChannel.accept();

        SocketAddress clientAddress = clientChannel.socket().getRemoteSocketAddress();
        log("client is from " + clientAddress);

        runClient(clientChannel);

        log("client completed");
        clientChannel.close();

        log("server completed");
        serverChannel.close();
    }

    public final static int instructionSize = 5000;
    private static void runClient(SocketChannel channel) throws IOException, InterruptedException {
        log("reading instructions....");

        ByteBuffer buffer = ByteBuffer.allocate(instructionSize);
        while (buffer.position() < buffer.capacity()) {
            channel.read(buffer);
        }

        channel.configureBlocking(false);

        log("start execution");

        String instruction = new String(buffer.array(), "UTF-8");
        runInstructions(instruction, channel);

        log("execution completed");
    }

    private static void runInstructions(String instructionsText, SocketChannel channel) throws IOException, InterruptedException {
        String[] instructions = instructionsText.trim().split("\n");

        int i = 0;
        for (String instruction : instructions) {
            String content = instruction.trim();

            log(++i + ") " + content);

            if (content.length() > 0) {
                runInstruction(content, channel);
            }
        }
    }
    private static void runInstruction(String instruction, SocketChannel channel) throws InterruptedException, IOException {
        if (instruction.startsWith("#") || instruction.startsWith("//") || instruction.startsWith("--")) {
            log("skip comment");
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

            long bandwidth = udpFloodMatch.getLong(5, Long.MAX_VALUE);

            floodUdp(group, port, time, bufferSize, bandwidth);
            return;
        }

        RegexMatch floodMatch = RegexMatch.create(instruction, "flood(?: TCP| tcp)? (\\d+) (\\d+)(?: (\\d+))?");
        if (floodMatch.find()) {
            long time = floodMatch.getLong(1);
            int bufferSize = floodMatch.getInt(2);
            long bandwidth = floodMatch.getLong(3, Long.MAX_VALUE);

            floodTcp(channel, time, bufferSize, bandwidth);
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
            if (side.equals("server")) {
                int count = holdMatch.getInt(2);
                hold(count);
            } else {
                log("skip client hold");
            }
            return;
        }

        RegexMatch releaseMatch = RegexMatch.create(instruction, "release (server|client) (\\d+)");
        if (releaseMatch.find()) {
            String side = releaseMatch.get(1);
            if (side.equals("server")) {
                int count = releaseMatch.getInt(2);
                release(count);
            } else {
                log("skip client release");
            }
            return;
        }

        log("unrecognized instruction: " + instruction);
    }

    private static void sleep(int length) throws InterruptedException, IOException {
        log("sleep " + length + "ms");
        Thread.sleep(length);
    }

    public final static byte floodContent = 77;
    private static void floodTcp(SocketChannel channel, long timeLength, int bufferSize, long limitBandwidth) throws IOException {
        log("flood through TCP for " + timeLength + "ms with " + bufferSize + "B buffer "
                + "at " + (limitBandwidth >= Long.MAX_VALUE ? "maximum speed" : limitBandwidth + "bps") + "...");

        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
        while (buffer.remaining() > 0) {
            buffer.put(floodContent);
        }

        long startTime = System.currentTimeMillis();
        long lastTime = startTime;

        long writeLength = 0;
        long writeTime = 0;
        long count = 0;

        while (true) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - startTime > timeLength) {
                break;
            }

            long limitLength = (currentTime - startTime) * limitBandwidth / 1000 / 8;
            if (writeLength >= limitLength) {
                continue;
            }

            lastTime = currentTime;

            buffer.rewind();

            long writeStart = System.nanoTime();
            int write = channel.write(buffer);
            long writeEnd = System.nanoTime();

            if (write > 0) {
                writeLength += write;
                writeTime += writeEnd - writeStart;
                count++;
            }
        }

        long usedTime = lastTime - startTime;
        log("used " + usedTime / 1000 + "s / " + usedTime + "ms");

        log("written "
                + writeLength / 1024 / 1024 + "MB / " + writeLength + "B / "
                + count / 1000 + "kp / " + count + "p / "
                + writeTime / 1000 / 1000 + "ms / " + writeTime + "ns");

        if (usedTime > 0) {
            long resultBandwidth = 8 * writeLength * 1000 / usedTime;
            log("bandwidth " + resultBandwidth / 1024 / 1024 + "Mbps / " + resultBandwidth + "bps");
        } else {
            log("bandwidth undetermined due to zero time");
        }

        log("flood completed");
    }

    private static void floodUdp(String group, int port, long timeLength, int bufferSize, long limitBandwidth) throws IOException {
        log("flood through UDP for " + timeLength + "ms with " + bufferSize + "B buffer "
                + "at " + (limitBandwidth >= Long.MAX_VALUE ? "maximum speed" : limitBandwidth + "bps") + "...");

        MulticastSocket socket = new MulticastSocket();

        byte[] buffer = new byte[bufferSize];
        Arrays.fill(buffer, floodContent);

        InetAddress groupAddress = InetAddress.getByName(group);

        long startTime = System.currentTimeMillis();
        long lastTime = startTime;

        long writeLength = 0;
        long writeTime = 0;
        long count = 0;

        while (true) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - startTime > timeLength) {
                break;
            }

            long limitLength = (currentTime - startTime) * limitBandwidth / 1000 / 8;
            if (writeLength >= limitLength) {
                continue;
            }

            lastTime = currentTime;

            DatagramPacket packet = new DatagramPacket(buffer, 0, buffer.length);
            packet.setAddress(groupAddress);
            packet.setPort(port);

            long writeStart = System.nanoTime();
            socket.send(packet);
            long writeEnd = System.nanoTime();

            writeLength += packet.getLength();
            writeTime += writeEnd - writeStart;
            count++;
        }

        long usedTime = lastTime - startTime;
        log("used " + usedTime / 1000 + "s / " + usedTime + "ms");

        log("written "
                + writeLength / 1024 / 1024 + "MB / " + writeLength + "B / "
                + count / 1000 + "kp / " + count + "p / "
                + writeTime / 1000 / 1000 + "ms / " + writeTime + "ns");

        if (usedTime > 0) {
            long bandwidth = 8 * writeLength * 1000 / usedTime;
            log("bandwidth " + bandwidth / 1024 / 1024 + "Mbps / " + bandwidth + "bps");
        } else {
            log("bandwidth undetermined due to zero time");
        }

        log("flood completed");
    }

    public final static byte syncStart = 88;
    public final static byte syncEnd = 89;
    private static void sync(SocketChannel channel) throws IOException, InterruptedException {
        log("sending for sync start...");
        while (true) {
            SocketChannelUtility.writeByte(channel, Server.syncStart);

            TimeUnit.MILLISECONDS.sleep(1);

            Byte content = SocketChannelUtility.readByte(channel);
            if (content != null && content == syncStart) {
                log("sync start received.");
                break;
            }
        }

        SocketChannelUtility.writeByte(channel, Server.syncEnd);

        log("wait for sync end...");
        SocketChannelUtility.readUntil(channel, Server.syncEnd);
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
