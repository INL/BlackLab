package nl.inl.blacklab.indexers.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

import nl.inl.blacklab.index.complex.ComplexField;
import nl.inl.blacklab.index.complex.ComplexFieldProperty;
import nl.inl.blacklab.index.complex.ComplexFieldProperty.SensitivitySetting;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.indexers.preprocess.DocIndexerConvertAndTag;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataImpl;
import nl.inl.blacklab.search.indexmetadata.nint.AnnotatedField;

/**
 * A DocIndexer configured using a ConfigInputFormat structure.
 */
public abstract class DocIndexerConfig extends DocIndexerBase {

    protected static String replaceDollarRefs(String pattern, List<String> replacements) {
        if (pattern != null) {
            int i = 1;
            for (String replacement : replacements) {
                pattern = pattern.replace("$" + i, replacement);
                i++;
            }
        }
        return pattern;
    }

    public static DocIndexerConfig fromConfig(ConfigInputFormat config) {
        DocIndexerConfig docIndexer;
        switch (config.getFileType()) {
        case XML:
            docIndexer = new DocIndexerXPath();
            break;
        case TABULAR:
            docIndexer = new DocIndexerTabular();
            break;
        case TEXT:
            docIndexer = new DocIndexerPlainText();
            break;
        case CHAT:
            docIndexer = new DocIndexerChat();
            break;
        default:
            throw new InputFormatConfigException(
                    "Unknown file type: " + config.getFileType() + " (use xml, tabular, text or chat)");
        }

        docIndexer.setConfigInputFormat(config);

        if (config.getConvertPluginId() != null || config.getTagPluginId() != null) {
            return new DocIndexerConvertAndTag(docIndexer, config);
        } else {
            return docIndexer;
        }
    }

    /** Our input format */
    protected ConfigInputFormat config;

    boolean inited = false;

    public void setConfigInputFormat(ConfigInputFormat config) {
        this.config = config;
    }

    @SuppressWarnings("deprecation")
    protected SensitivitySetting getSensitivitySetting(ConfigAnnotation mainAnnotation) {
        if (mainAnnotation.getSensitivity() == SensitivitySetting.DEFAULT) {
            return getSensitivitySetting(mainAnnotation.getName());
        }
        return mainAnnotation.getSensitivity();
    }

    @Override
    protected String optTranslateFieldName(String from) {
        if (config == null) // test
            return from;
        String to = config.getIndexFieldAs().get(from);
        return to == null ? from : to;
    }

    @SuppressWarnings("deprecation")
    protected void init() {
        if (inited)
            return;
        inited = true;
        setStoreDocuments(config.shouldStore());
        for (ConfigAnnotatedField af : config.getAnnotatedFields().values()) {

            // Define the properties that make up our complex field
            List<ConfigAnnotation> annotations = new ArrayList<>(af.getAnnotations().values());
            if (annotations.isEmpty())
                throw new InputFormatConfigException("No annotations defined for field " + af.getName());
            ConfigAnnotation mainAnnotation = annotations.get(0);
            ComplexField complexField = new ComplexField(af.getName(), mainAnnotation.getName(),
                    getSensitivitySetting(mainAnnotation), false);
            addComplexField(complexField);

            IndexMetadataImpl indexMetadata;
            if (indexer != null) {
                indexMetadata = (IndexMetadataImpl)indexer.getSearcher().getIndexMetadataWriter();
                AnnotatedField f = indexMetadata.registerAnnotatedField(complexField.getName(), complexField.getMainProperty().getName());
                complexField.setAnnotatedField(f);
            }

            ComplexFieldProperty propStartTag = complexField.addProperty(ComplexFieldUtil.START_TAG_PROP_NAME,
                    getSensitivitySetting(ComplexFieldUtil.START_TAG_PROP_NAME), true);
            propStartTag.setForwardIndex(false);

            // Create properties for the other annotations
            for (int i = 1; i < annotations.size(); i++) {
                ConfigAnnotation annot = annotations.get(i);
                complexField.addProperty(annot.getName(), getSensitivitySetting(annot), false);
            }
            for (ConfigStandoffAnnotations standoff : af.getStandoffAnnotations()) {
                for (ConfigAnnotation annot : standoff.getAnnotations().values()) {
                    complexField.addProperty(annot.getName(), getSensitivitySetting(annot), false);
                }
            }
            if (!complexField.hasProperty(ComplexFieldUtil.PUNCTUATION_PROP_NAME)) {
                // Hasn't been created yet. Create it now.
                complexField.addProperty(ComplexFieldUtil.PUNCTUATION_PROP_NAME,
                        getSensitivitySetting(ComplexFieldUtil.PUNCTUATION_PROP_NAME), false);
            }
        }
    }

    @Override
    public void index() throws Exception {
        init();
    }

    @Override
    public void indexSpecificDocument(String documentExpr) {
        init();
    }

    protected String processString(String result, List<ConfigProcessStep> process) {
        for (ConfigProcessStep step : process) {
            String method = step.getMethod();
            Map<String, String> param = step.getParam();
            switch (method) {
            case "replace": {
                String find = param.get("find");
                String replace = param.get("replace");
                if (find == null || replace == null)
                    throw new InputFormatConfigException("replace needs parameters find and replace");
                try {
                    result = result.replaceAll(find, replace);
                } catch (PatternSyntaxException e) {
                    throw new InputFormatConfigException("Syntax error in replace regex: " + find);
                }
                break;
            }
            case "default": {
                if (result.length() == 0) {
                    String field = param.get("field");
                    String value;
                    if (field != null)
                        value = currentLuceneDoc.get(field);
                    else
                        value = param.get("value");
                    if (value != null)
                        result = value;
                }
                break;
            }
            case "append": {
                String separator = param.containsKey("separator") ? param.get("separator") : " ";
                String field = param.get("field");
                String value;
                if (field != null)
                    value = currentLuceneDoc.get(field);
                else
                    value = param.get("value");
                if (value != null && value.length() > 0) {
                    if (result.length() > 0)
                        result += separator;
                    result += value;
                }
                break;
            }
            case "split": {
                // Split on a separator regex and keep one part (first part by default)
                String separator = param.containsKey("separator") ? param.get("separator") : ";";
                int keep = param.containsKey("keep") ? Integer.parseInt(param.get("keep")) - 1 : 0;
                if (keep < 0) {
                    warn("action 'split', parameter 'keep': must be at least 1");
                    keep = 0;
                }
                String[] parts = result.split(separator, -1);
                if (keep >= parts.length)
                    result = "";
                else
                    result = parts[keep];
                break;
            }
            default: {
                // In the future, we'll support user plugins here
                throw new UnsupportedOperationException("Unknown processing step method " + method);
            }
            }
        }
        return result;
    }

    /**
     * If any processing steps were defined for this metadata field, apply them now.
     *
     * This is used for non-XML formats, where we don't actively seek out the
     * metadata but encounter it as we go.
     *
     * @param name metadata field name
     * @param value metadata field value
     * @return processed value (or orifinal value if not found / no processing steps
     *         defined)
     */
    protected String processMetadataValue(String name, String value) {
        ConfigMetadataField f = config.getMetadataField(name);
        if (f != null) {
            value = processString(value, f.getProcess());
        }
        return value;
    }

}
