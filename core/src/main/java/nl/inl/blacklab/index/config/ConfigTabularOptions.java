package nl.inl.blacklab.index.config;

/**
 * Configuration for tabular file formats
 */
class ConfigTabularOptions {

    /** If tabular, what flavour? (e.g. csv, tsv). */
    private DocIndexerTabular.Type type = DocIndexerTabular.Type.CSV;

    /** Does the data start with a column names record? */
    private boolean columnNames = false;

    /** The column delimiter (varies between locales); null for format default */
    private Character delimiter;

    /** Quote character; null for format default */
    private Character quote;

    /** Interpret lines with XML start/end tags as inline tags? */
    private boolean inlineTags = false;

    /** Interpret lines with <g/> as glue tags? */
    private boolean glueTags = false;

    public ConfigTabularOptions() {
    }

    public void validate() {
        // nothing to validate yet
    }

    public ConfigTabularOptions copy() {
        ConfigTabularOptions cp = new ConfigTabularOptions();
        cp.setType(type);
        cp.columnNames = columnNames;
        cp.delimiter = delimiter;
        cp.inlineTags = inlineTags;
        cp.glueTags = glueTags;
        return cp;
    }

    public DocIndexerTabular.Type getType() {
        return type;
    }

    public void setType(DocIndexerTabular.Type type) {
        this.type = type;
    }

    public boolean hasColumnNames() {
        return columnNames;
    }

    public void setColumnNames(boolean columnNames) {
        this.columnNames = columnNames;
    }

    public Character getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(Character delimiter) {
        this.delimiter = delimiter;
    }

    public Character getQuote() {
        return quote;
    }

    public void setQuote(Character quote) {
        this.quote = quote;
    }

    public boolean hasInlineTags() {
        return inlineTags;
    }

    public void setInlineTags(boolean inlineTags) {
        this.inlineTags = inlineTags;
    }

    public boolean hasGlueTags() {
        return glueTags;
    }

    public void setGlueTags(boolean glueTags) {
        this.glueTags = glueTags;
    }

}