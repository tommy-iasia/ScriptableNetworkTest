package utility;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexMatch {

    public static RegexMatch create(String input, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        return new RegexMatch(matcher);
    }

    public RegexMatch(Matcher matcher) {
        this.matcher = matcher;
    }
    private final Matcher matcher;

    public boolean find() {
        return matcher.find();
    }

    public String get(int group) {
        return matcher.group(group);
    }
    public int getInt(int group) {
        String text = matcher.group(group);
        return Integer.parseInt(text);
    }

    public long getLong(int group) {
        String text = matcher.group(group);
        return Long.parseLong(text);
    }
    public long getLong(int group, long defaultValue) {
        Long value = findLong(group);

        if (value != null) {
            return value;
        } else {
            return defaultValue;
        }
    }
    public Long findLong(int group) {
        String text = matcher.group(group);
        if (text == null) {
            return null;
        }

        return Long.parseLong(text);
    }
}
