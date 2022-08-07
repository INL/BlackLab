package nl.inl.blacklab.index.annotated;

import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;

/**
 * What sensitivities are indexed for an annotation.
 */
public enum AnnotationSensitivities {
    DEFAULT, // "choose default based on field name"
    ONLY_SENSITIVE, // only index case- and diacritics-sensitively
    ONLY_INSENSITIVE, // only index case- and diacritics-insensitively
    SENSITIVE_AND_INSENSITIVE, // case+diac sensitive as well as case+diac insensitive
    CASE_AND_DIACRITICS_SEPARATE; // all four combinations (sens, insens, case-insens, diac-insens)

    public static AnnotationSensitivities fromStringValue(String v) {
        switch (v.toLowerCase()) {
        case "default":
        case "":
            return DEFAULT;
        case "sensitive":
        case "s":
            return ONLY_SENSITIVE;
        case "insensitive":
        case "i":
            return ONLY_INSENSITIVE;
        case "sensitive_insensitive":
        case "si":
            return SENSITIVE_AND_INSENSITIVE;
        case "case_diacritics_separate":
        case "all":
            return CASE_AND_DIACRITICS_SEPARATE;
        default:
            throw new IllegalArgumentException("Unknown string value for SensitivitySetting: " + v
                    + " (should be default|sensitive|insensitive|sensitive_insensitive|case_diacritics_separate or s|i|si|all)");
        }
    }

    public String getStringValue() {
        switch (this) {
        case DEFAULT:
            return "default";
        case ONLY_SENSITIVE:
            return "sensitive";
        case ONLY_INSENSITIVE:
            return "insensitive";
        case SENSITIVE_AND_INSENSITIVE:
            return "sensitive_insensitive";
        case CASE_AND_DIACRITICS_SEPARATE:
            return "case_diacritics_separate";
        default:
            throw new IllegalArgumentException("Unknown AnnotationSensitivities: " + this);
        }
    }

    @Override
    public String toString() {
        // toString() is informative, not authoritative, but let's return
        // the same as the "official" method getStringValue.
        return getStringValue();
    }

    public static AnnotationSensitivities defaultForAnnotation(String name) {
        return AnnotatedFieldNameUtil.defaultSensitiveInsensitive(name) ?
                AnnotationSensitivities.SENSITIVE_AND_INSENSITIVE :
                AnnotationSensitivities.ONLY_INSENSITIVE;
    }
}
