package nl.inl.blacklab.resultproperty;

public class PropertyValueDecade extends PropertyValueInt {

    public PropertyValueDecade(int value) {
        super(value);
    }

    public static PropertyValue deserialize(String info) {
        if (info.equals("unknown"))
            return new PropertyValueDecade(HitPropertyDocumentDecade.UNKNOWN_VALUE);
        int decade;
        try {
            decade = Integer.parseInt(info);
        } catch (NumberFormatException e) {
            decade = 0;
        }
        return new PropertyValueDecade(decade);
    }

    @Override
    public String toString() {
        if (value == HitPropertyDocumentDecade.UNKNOWN_VALUE)
            return "unknown";
        long year = value - value % 10;
        return year + "-" + (year + 9);
    }

    @Override
    public String serialize() {
        if (value == HitPropertyDocumentDecade.UNKNOWN_VALUE)
            return "unknown";
        return PropertySerializeUtil.combineParts("dec", Long.toString(value));
    }
}
