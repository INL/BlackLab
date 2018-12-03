package nl.inl.blacklab.indexers.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.index.annotated.AnnotationWriter.SensitivitySetting;
import nl.inl.blacklab.indexers.preprocess.DocIndexerConvertAndTag;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataImpl;

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
            throw new InvalidInputFormatConfig(
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

            // Define the properties that make up our annotated field
            List<ConfigAnnotation> annotations = new ArrayList<>(af.getAnnotationsFlattened().values());
            if (annotations.isEmpty())
                throw new InvalidInputFormatConfig("No annotations defined for field " + af.getName());
            ConfigAnnotation mainAnnotation = annotations.get(0);
            AnnotatedFieldWriter fieldWriter = new AnnotatedFieldWriter(af.getName(), mainAnnotation.getName(),
                    getSensitivitySetting(mainAnnotation), false);
            addAnnotatedField(fieldWriter);

            AnnotationWriter annotStartTag = fieldWriter.addAnnotation(null, AnnotatedFieldNameUtil.TAGS_ANNOT_NAME,
                    getSensitivitySetting(AnnotatedFieldNameUtil.TAGS_ANNOT_NAME), true);
            annotStartTag.setHasForwardIndex(false);

            // Create properties for the other annotations
            for (int i = 1; i < annotations.size(); i++) {
                ConfigAnnotation annot = annotations.get(i);
                if (!annot.isForEach())
                    fieldWriter.addAnnotation(annot, annot.getName(), getSensitivitySetting(annot), false);
            }
            for (ConfigStandoffAnnotations standoff : af.getStandoffAnnotations()) {
                for (ConfigAnnotation annot : standoff.getAnnotations().values()) {
                    fieldWriter.addAnnotation(annot, annot.getName(), getSensitivitySetting(annot), false);
                }
            }
            if (!fieldWriter.hasAnnotation(AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME)) {
                // Hasn't been created yet. Create it now.
                fieldWriter.addAnnotation(null, AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME,
                        getSensitivitySetting(AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME), false);
            }
            if (docWriter != null) {
                IndexMetadataImpl indexMetadata = (IndexMetadataImpl)docWriter.indexWriter().metadataWriter();
                indexMetadata.registerAnnotatedField(fieldWriter);
            }

        }
    }

    @Override
    public void index() throws IOException, MalformedInputFile, PluginException {
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
            case "replace":
                result = opReplace(result, param);
                break;
            case "default":
                result = opDefault(result, param);
                break;
            case "append": 
                result = opAppend(result, param);
                break;
            case "split":
                result = opSplit(result, param);
                break;
            case "chatFormatAgeToMonths":
                result = opChatFormatAgeToMonths(result);
                break;
            case "strip":
                result = opStrip(result, param);
                break;
            case "parsePos":
            {
                // Get individual feature out of a part of speech string like "NOU(gender=f,number=p)"
                String field = param.containsKey("field") ? param.get("field") : "_";
                result = opParsePartOfSpeech(result, field);
                break;
            }
            default:
                // In the future, we'll support user plugins here
                throw new UnsupportedOperationException("Unknown processing step method " + method);
            }
        }
        return result;
    }

    static String opParsePartOfSpeech(String result, String field) {
        // Trim character/string from beginning and end
        result = result.trim();
        if (field.equals("_")) {
            //  Get main pos: A(b=c,d=e) -> A 
            return result.replaceAll("^([^\\(]+)(\\s*\\(.*\\))?$", "$1");
        } else {
            //  Get feature: A(b=c,d=e) -> e  (if field == d)
            String featuresString = result.replaceAll("^[^\\(]+(\\s*\\((.*)\\))?$", "$2");
            return Arrays.stream(featuresString.split(","))
                .map(feat -> feat.split("="))
                .filter(featParts -> featParts[0].trim().equals(field))
                .map(featParts -> featParts[1].trim())
                .findFirst()
                .orElse("");
        }
    }

    private String opStrip(String result, Map<String, String> param) {
        // Trim character/string from beginning and end
        String stripChars = param.containsKey("chars") ? param.get("chars") : " ";
        result = StringUtils.strip(result, stripChars);
        return result;
    }

    protected String opChatFormatAgeToMonths(String result) {
        // 1;10.30 => 1 jaar, 10 maanden, 30 dagen => afgerond 23 maanden?
        String[] parts = result.split("[;\\.]", 3);
        int years = 0;
        int months = 0;
        int days = 0;
        try {
            years = Integer.parseInt(parts[0]);
            months = parts.length <= 1 ? 0 : Integer.parseInt(parts[1]);
            days = parts.length <= 2 ? 0 : Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            warn("action 'opChatFormatAgeToMonths': illegal value " + result);
        }
        return Integer.toString(years * 12 + months + (days > 14 ? 1 : 0) );
    }

    private String opSplit(String result, Map<String, String> param) {
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
        return result;
    }

    private String opAppend(String result, Map<String, String> param) {
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
        return result;
    }

    private String opDefault(String result, Map<String, String> param) {
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
        return result;
    }

    private static String opReplace(String result, Map<String, String> param) {
        String find = param.get("find");
        String replace = param.get("replace");
        if (find == null || replace == null)
            throw new InvalidInputFormatConfig("replace needs parameters find and replace");
        try {
            result = result.replaceAll(find, replace);
        } catch (PatternSyntaxException e) {
            throw new InvalidInputFormatConfig("Syntax error in replace regex: " + find);
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
