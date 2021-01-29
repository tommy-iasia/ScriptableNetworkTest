package utility;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogFile implements Closeable {

    public LogFile(String prefix) throws FileNotFoundException {
        String timeText = getTimeText();

        int random = (int) Math.round(Math.random() * 999999);

        String fileName = prefix + "." + timeText + "." + random + ".log";
        stream = new FileOutputStream(fileName);
    }
    private final FileOutputStream stream;

    private static final SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
    private static String getTimeText() {
        Date date = new Date();
        return format.format(date);
    }

    public void add(String line) throws IOException {
        String timeText = getTimeText();

        String consoleText = "[" + timeText + "] " + line;
        System.out.println(consoleText);

        String streamText = "[" + timeText + "]" + line + "\r\n";
        byte[] bytes = streamText.getBytes("UTF-8");
        stream.write(bytes);
    }

    @Override
    public void close() throws IOException {
        stream.flush();
        stream.close();
    }
}
