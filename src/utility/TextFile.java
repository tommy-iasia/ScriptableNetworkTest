package utility;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class TextFile {

    public static String read(String path) throws IOException {
        File file = new File(path);

        int length = (int) file.length();
        byte[] buffer = new byte[length];

        FileInputStream stream = new FileInputStream(file);
        stream.read(buffer);

        return new String(buffer, "UTF-8");
    }
}
