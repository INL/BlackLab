package nl.inl.blacklab.indexers.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
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
        DocIndexerConfig docIndexer=null;
        switch (config.getFileType()) {
        case XML:
            for (ConfigInputFormat.FileTypeOption fto : ConfigInputFormat.FileTypeOption.fromConfig(config, ConfigInputFormat.FileType.XML)) {
                if (fto== ConfigInputFormat.FileTypeOption.SAXONICA) {
                    docIndexer=new DocIndexerSaxon();
                    break;
                }
            }
            if (docIndexer==null) {
                docIndexer=new DocIndexerXPath();
            }
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
            try {
                return new DocIndexerConvertAndTag(docIndexer, config);
            } catch (Exception e) {
                throw BlackLabRuntimeException.wrap(e);
            }
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
            if (af.isDummyForStoringLinkedDocuments())
                continue;
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
                IndexMetadataImpl indexMetadata = (IndexMetadataImpl)docWriter.indexWriter().metadata();
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

    protected String processString(String result, List<ConfigProcessStep> process, Map<String, String> mapValues) {
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
                result = opSplit(result, param).get(0);
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
        if (mapValues != null && !mapValues.isEmpty()) {
            // Finally, apply any value mappings specified.
            String mappedResult = mapValues.get(result);
            if (mappedResult != null)
                result = mappedResult;
        }
        return result;
    }

    /**
     * process linked documents when configured. An xPath processor can be provided,
     * it will retrieve information from the document to construct a path to a linked document.
     * @param ld
     * @param xpathProcessor
     */
    protected void processLinkedDocument( ConfigLinkedDocument ld, Function<String, String> xpathProcessor) {
        // Resolve linkPaths to get the information needed to fetch the document
        List<String> results = new ArrayList<>();
        for (ConfigLinkValue linkValue : ld.getLinkValues()) {
            String valuePath = linkValue.getValuePath();
            String valueField = linkValue.getValueField();
            if (valuePath != null) {
                // Resolve value using XPath
                String result = xpathProcessor.apply(valuePath);
                if (result == null || result.isEmpty()) {
                    switch (ld.getIfLinkPathMissing()) {
                        case IGNORE:
                            break;
                        case WARN:
                            docWriter.listener()
                                    .warning("Link path " + valuePath + " not found in document " + documentName);
                            break;
                        case FAIL:
                            throw new BlackLabRuntimeException("Link path " + valuePath + " not found in document " + documentName);
                    }
                }
                results.add(result);
            } else if (valueField != null) {
                // Fetch value from Lucene doc
                results.addAll(getMetadataField(valueField));
            }
            List<String> resultAfterProcessing = new ArrayList<>();
            for (String inputValue : results) {
                resultAfterProcessing.addAll(processStringMultipleValues(inputValue, linkValue.getProcess(), null));
            }
            results = resultAfterProcessing;
        }

        // Substitute link path results in inputFile, pathInsideArchive and documentPath
        String inputFile = replaceDollarRefs(ld.getInputFile(), results);
        String pathInsideArchive = replaceDollarRefs(ld.getPathInsideArchive(), results);
        String documentPath = replaceDollarRefs(ld.getDocumentPath(), results);

        try {
            // Fetch and index the linked document
            indexLinkedDocument(inputFile, pathInsideArchive, documentPath, ld.getInputFormatIdentifier(),
                    ld.shouldStore() ? ld.getName() : null);
        } catch (Exception e) {
            String moreInfo = "(inputFile = " + inputFile;
            if (pathInsideArchive != null)
                moreInfo += ", pathInsideArchive = " + pathInsideArchive;
            if (documentPath != null)
                moreInfo += ", documentPath = " + documentPath;
            moreInfo += ")";
            switch (ld.getIfLinkPathMissing()) {
                case IGNORE:
                case WARN:
                    docWriter.listener().warning("Could not find or parse linked document for " + documentName + moreInfo
                            + ": " + e.getMessage());
                    break;
                case FAIL:
                    throw new BlackLabRuntimeException("Could not find or parse linked document for " + documentName + moreInfo, e);
            }
        }
    }

    protected List<String> processStringMultipleValues(String input, List<ConfigProcessStep> process, Map<String, String> mapValues) {
        List<String> result = Arrays.asList(input);

        for (ConfigProcessStep step : process) {
            String method = step.getMethod();
            Map<String, String> param = step.getParam();

            switch (method) {
            case "replace":
                for (int i = 0; i < result.size(); ++i) {
                    result.set(i, opReplace(result.get(i), param));
                }
                break;
            case "default":
                for (int i = 0; i < result.size(); ++i) {
                    result.set(i, opDefault(result.get(i), param));
                }
                break;
            case "append":
                for (int i = 0; i < result.size(); ++i) {
                    result.set(i, opAppend(result.get(i), param));
                }
                break;
            case "split":
                result = result.stream()
                    .flatMap(r -> opSplit(r, param).stream())
                    .collect(Collectors.toCollection(ArrayList<String>::new));
                break;
            case "chatFormatAgeToMonths":
                for (int i = 0; i < result.size(); ++i) {
                    result.set(i, opChatFormatAgeToMonths(result.get(i)));
                }
                break;
            case "strip":
                for (int i = 0; i < result.size(); ++i) {
                    result.set(i, opStrip(result.get(i), param));
                }
                break;
            case "parsePos":
            {
                // Get individual feature out of a part of speech string like "NOU(gender=f,number=p)"
                String field = param.containsKey("field") ? param.get("field") : "_";
                for (int i = 0; i < result.size(); ++i) {
                    result.set(i, opParsePartOfSpeech(result.get(i), field));
                }
                break;
            }
            default:
                // In the future, we'll support user plugins here
                throw new UnsupportedOperationException("Unknown processing step method " + method);
            }
        }
        if (mapValues != null && !mapValues.isEmpty()) {
            // Finally, apply any value mappings specified.
            for (int i = 0; i < result.size(); ++i) {
                String mappedResult = mapValues.get(result.get(i));
                if (mappedResult != null)
                    result.set(i, mappedResult);
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

    static String opStrip(String result, Map<String, String> param) {
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

    /**
     * Split the result string on a separator and return one or all parts.
     *
     * @param result the string to split
     * @param param
     * <pre>
     * - "separator" for the separator (defaults to ;),
     * - "keep" for the keep index, accepts numbers or the special string "all" (defaults to -1)
     *      if "keep" <= 0 returns the first part.
     *      if "keep" > number of splits return empty string.
     *      if "keep" == "all" return all parts
     * </pre>
     * @return
     */
    private List<String> opSplit(String result, Map<String, String> param) {
        // Split on a separator regex and keep one or all parts (first part by default)
        String separator = param.getOrDefault("separator", ";");
        String keep = param.getOrDefault("keep", "-1").toLowerCase();
        String[] parts = result.split(separator, -1);

        if (keep.equals("all")) {
            return Arrays.asList(parts);
        }

        int i = -1;
        try { i = Integer.parseInt(keep); } catch (NumberFormatException e) { /* handled below */ }
        if (i < 0) {
            warn("action 'split', parameter 'keep': must be at least 1");
            i = 0;
        }

        return Arrays.asList(i < parts.length ? parts[i] : "");
    }

    /**
     * Appends a constant value, or the value of a metadata field to the result string.
     *
     * @param result the input string
     * @param param
     * <pre>
     * - "separator" for the separator (defaults to " ")
     * - "field" for the metadata field whose value will be appended
     * - "value" for a constant value ("field" takes precedence if it exists)
     * </pre>
     * @return
     */
    private String opAppend(String result, Map<String, String> param) {
        String separator = param.containsKey("separator") ? param.get("separator") : " ";
        String field = param.get("field");
        String value;
        if (field != null)
            value = StringUtils.join(getMetadataField(field), separator);
        else
            value = param.get("value");
        if (value != null && value.length() > 0) {
            if (result.length() > 0)
                result += separator;
            result += value;
        }
        return result;
    }

    /**
     * Optionally replace an empty result with a constant value, or the value of a metadata field.
     *
     * @param result the input string
     * @param param
     * <pre>
     * - "field" for the metadata field whose value will be used
     * - "separator" to join the metadata field if it contains multiple values (defaults to ;)
     * - "value" for a constant value ("field" takes precedence if it exists)
     * </pre>
     * @return
     */
    private String opDefault(String result, Map<String, String> param) {
        if (result.length() == 0) {
            String field = param.get("field");
            String value;
            String sep = param.getOrDefault("separator", ";");
            if (field != null)
                value = StringUtils.join(getMetadataField(field), sep);
            else
                value = param.get("value");
            if (value != null)
                result = value;
        }
        return result;
    }

    /**
     * Perform a regex replace on result. Allows group references.
     *
     * @param result
     * @param param
     * <pre>
     * - "find" for the regex
     * - "replace" the replacement string
     * </pre>
     * @return
     */
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
            value = processString(value, f.getProcess(), f.getMapValues());
        }
        return value;
    }

}
