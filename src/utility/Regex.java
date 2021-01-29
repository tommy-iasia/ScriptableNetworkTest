package utility;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Regex {

    public static Matcher find(String input, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        return matcher.find() ? matcher : null;
    }
}
