package runner;

import utility.HoldMemory;
import utility.LogFile;
import utility.Regex;
import utility.SocketChannelUtility;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.regex.Matcher;

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

        Matcher sleepMatcher = Regex.find(instruction, "sleep (\\d+)");
        if (sleepMatcher != null) {
            String lengthText = sleepMatcher.group(1);
            int lengthValue = Integer.parseInt(lengthText);

            sleep(lengthValue);
            return;
        }

        Matcher floodMatcher = Regex.find(instruction, "flood (\\d+) (\\d+)");
        if (floodMatcher != null) {
            String timeText = floodMatcher.group(1);
            long timeValue = Long.parseLong(timeText);

            String bufferSizeText = floodMatcher.group(2);
            int bufferSizeValue = Integer.parseInt(bufferSizeText);

            flood(channel, timeValue, bufferSizeValue);
            return;
        }

        Matcher syncMatcher = Regex.find(instruction, "sync");
        if (syncMatcher != null) {
            sync(channel);
            return;
        }

        Matcher holdMatcher = Regex.find(instruction, "hold (server|client) (\\d+)");
        if (holdMatcher != null) {
            String side = holdMatcher.group(1);

            String countText = holdMatcher.group(2);
            int countValue = Integer.parseInt(countText);

            if (side.equals("server")) {
                hold(countValue);
            } else {
                log("skip client hold");
            }
            return;
        }

        Matcher releaseMatcher = Regex.find(instruction, "release (server|client) (\\d+)");
        if (releaseMatcher != null) {
            String side = releaseMatcher.group(1);

            String countText = releaseMatcher.group(2);
            int countValue = Integer.parseInt(countText);

            if (side.equals("server")) {
                release(countValue);
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
    private static void flood(SocketChannel channel, long timeLength, int bufferSize) throws IOException {
        log("flood for " + timeLength + "ms with " + bufferSize + "B buffer...");

        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
        while (buffer.remaining() > 0) {
            buffer.put(floodContent);
        }

        long startTime = System.currentTimeMillis();
        long lastTime;

        long writeLength = 0;
        long writeTime = 0;
        long count = 0;

        while (true) {
            buffer.rewind();

            long writeStart = System.nanoTime();
            int write = channel.write(buffer);
            long writeEnd = System.nanoTime();

            if (write > 0) {
                writeLength += write;
                writeTime += writeEnd - writeStart;
                count++;
            }

            lastTime = System.currentTimeMillis();
            if (lastTime - startTime > timeLength) {
                break;
            }
        }

        long usedTime = lastTime - startTime;
        log("used " + usedTime / 1000 + "s / " + usedTime + "ms");

        log("written "
                + writeLength / 1024 / 1024 + "MB / " + writeLength + "B / "
                + count / 1000 + "kp / " + count + "p / "
                + writeTime / 1000 / 1000 + "ms / " + writeTime + "ns");

        long bandwidth = 8 * writeLength * 1000 / usedTime;
        log("bandwidth " + bandwidth / 1024 / 1024 + "Mbps / " + bandwidth + "bps");

        log("flood completed");
    }

    public final static byte syncStart = 88;
    public final static byte syncEnd = 89;
    private static void sync(SocketChannel channel) throws IOException, InterruptedException {
        log("sending for sync start...");
        while (true) {
            SocketChannelUtility.writeByte(channel, Server.syncStart);

            Thread.sleep(1);

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
