package nl.inl.blacklab.index.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Settings that will be used to write the indexmetadata file
 * for any corpus we create from this format.
 *
 * Stuff used by BLS and user interfaces.
 *
 * None of these settings have any impact on indexing.
 * All fields are optional.
 */
public class ConfigCorpus {

    /** Text direction: left-to-right or right-to-left (and possible future values?) */
    public static enum TextDirection {
        LEFT_TO_RIGHT("ltr", "lefttoright"),
        RIGHT_TO_LEFT("rtl", "righttoleft");

        private String[] codes;

        private TextDirection(String... codes) {
            this.codes = codes;
        }

        /**
         * Get the primary string code for this TextDirection.
         * @return string code
         */
        public String getCode() {
            return this.codes[0];
        }

        /**
         * Does the specified code match this value?
         * @param code code to match, e.g. "LTR" or "right-to-left"
         * @return true if it matches, false if not
         */
        public boolean matchesCode(String code) {
            code = code.toLowerCase().replaceAll("[^a-z]", ""); // remove any spaces, dashes, etc.
            for (String checkCode: codes) {
                if (checkCode.equals(code))
                    return true;
            }
            return false;
        }

        /** Return a TextDirection from a string code (e.g. "LTR", "right-to-left", etc.)
         *
         * @param code textDirection code to recognize
         * @return corresponding TextDirection
         */
        public static TextDirection fromCode(String code) {
            for (TextDirection d: TextDirection.values()) {
                if (d.matchesCode(code))
                    return d;
            }
            return LEFT_TO_RIGHT; // default value
        }
    }

    /** Corpus display name */
    private String displayName = "";

    /** Corpus description */
    private String description = "";

    /** May end user fetch contents of whole documents? [false] */
    private boolean contentViewable = false;

    /** What is the text direction of the script used? (e.g. LTR / RTL) */
    private TextDirection textDirection = TextDirection.LEFT_TO_RIGHT;

    /** Special field roles, such as pidField, titleField, etc. */
    Map<String, String> specialFields = new LinkedHashMap<>();

    /** How to group metadata fields */
    Map<String, ConfigMetadataFieldGroup> metadataFieldGroups = new LinkedHashMap<>();

    public ConfigCorpus copy() {
        ConfigCorpus result = new ConfigCorpus();
        result.contentViewable = contentViewable;
        result.textDirection = textDirection;
        result.specialFields.putAll(specialFields);
        for (ConfigMetadataFieldGroup g: getMetadataFieldGroups().values()) {
            result.addMetadataFieldGroup(g.copy());
        }
        return result;
    }

    public Map<String, String> getSpecialFields() {
        return Collections.unmodifiableMap(specialFields);
    }

    public void addSpecialField(String type, String fieldName) {
        specialFields.put(type, fieldName);
    }

    public Map<String, ConfigMetadataFieldGroup> getMetadataFieldGroups() {
        return metadataFieldGroups;
    }

    void addMetadataFieldGroup(ConfigMetadataFieldGroup g) {
        metadataFieldGroups.put(g.getName(), g);
    }

    public boolean isContentViewable() {
        return contentViewable;
    }

    public void setContentViewable(boolean contentViewable) {
        this.contentViewable = contentViewable;
    }

    public TextDirection getTextDirection() {
        return this.textDirection;
    }

    public void setTextDirection(TextDirection textDirection) {
        this.textDirection = textDirection;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}
