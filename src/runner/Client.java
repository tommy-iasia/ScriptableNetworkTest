package runner;

import utility.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
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
            throw new RuntimeException("instruction is too long to run.");
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

            if (side.equals("client")) {
                hold(countValue);
            } else {
                log("skip server hold");
            }
            return;
        }

        Matcher releaseMatcher = Regex.find(instruction, "release (server|client) (\\d+)");
        if (releaseMatcher != null) {
            String side = releaseMatcher.group(1);

            String countText = releaseMatcher.group(2);
            int countValue = Integer.parseInt(countText);

            if (side.equals("client")) {
                release(countValue);
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

    private static void flood(SocketChannel channel, long timeLength, int bufferSize) throws IOException {
        log("receive flood for " + timeLength + "ms with " + bufferSize + "B buffer...");

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

    private static void sync(SocketChannel channel) throws IOException {
        log("wait for sync start...");
        SocketChannelUtility.readUntil(channel, Server.syncStart);
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
