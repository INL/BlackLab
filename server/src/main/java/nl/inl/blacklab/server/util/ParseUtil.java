package nl.inl.blacklab.server.util;

public class ParseUtil {

    public static boolean strToBool(String value) throws IllegalArgumentException {
        if (value.equals("true") || value.equals("1") || value.equals("yes") || value.equals("on"))
            return true;
        if (value.equals("false") || value.equals("0") || value.equals("no") || value.equals("off"))
            return false;
        throw new IllegalArgumentException("Cannot convert to boolean: " + value);
    }

    public static int strToInt(String value) throws IllegalArgumentException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot convert to int: " + value);
        }
    }

    public static long strToLong(String value) throws IllegalArgumentException {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot convert to long: " + value);
        }
    }

    public static float strToFloat(String value) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot convert to float: " + value);
        }
    }

}
