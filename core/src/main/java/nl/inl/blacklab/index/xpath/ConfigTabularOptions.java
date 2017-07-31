package nl.inl.blacklab.index.xpath;

/**
 * Configuration for tabular file formats
 */
class ConfigTabularOptions {

    /** Tabular types we support */
    static enum Type {
        CSV,
        TSV;

        public static Type fromStringValue(String str) {
            switch(str.toUpperCase()) {
            case "TDF":
                return TSV;
            case "EXCEL":
                return CSV;
            }
            return valueOf(str.toUpperCase());
        }

        public String stringValue() {
            return toString().toLowerCase();
        }
    }

    /** If tabular, what flavour? (e.g. csv, tsv). */
    private Type type = Type.CSV;

    /** Does the data start with a column names record? */
    private boolean columnNames = false;

    public ConfigTabularOptions() {
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public boolean isColumnNames() {
        return columnNames;
    }

    public void setColumnNames(boolean columnNames) {
        this.columnNames = columnNames;
    }

}