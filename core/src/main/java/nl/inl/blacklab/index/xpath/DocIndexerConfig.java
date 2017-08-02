package nl.inl.blacklab.index.xpath;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

import nl.inl.blacklab.index.complex.ComplexField;
import nl.inl.blacklab.index.complex.ComplexFieldProperty;
import nl.inl.blacklab.index.complex.ComplexFieldProperty.SensitivitySetting;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.indexstructure.IndexStructure;

/**
 * A DocIndexer configured using a ConfigInputFormat structure.
 */
public abstract class DocIndexerConfig extends DocIndexerBase {

    protected static String replaceDollarRefs(String pattern, List<String> replacements) {
        if (pattern != null) {
    	    int i = 1;
            for (String replacement: replacements) {
                pattern = pattern.replace("$" + i, replacement);
                i++;
            }
        }
        return pattern;
    }

    public static DocIndexerConfig fromConfig(ConfigInputFormat config) {
        DocIndexerConfig docIndexer;
        switch (config.getFileType()) {
        case XML: docIndexer = new DocIndexerXPath(); break;
        case TABULAR: docIndexer = new DocIndexerTabular(); break;
        case TEXT: docIndexer = new DocIndexerPlainText(); break;
        default: throw new InputFormatConfigException("Unknown file type: " + config.getFileType() + " (use xml or tabular)");
        }
        docIndexer.setConfigInputFormat(config);
        return docIndexer;
    }

    /** Our input format */
    protected ConfigInputFormat config;

    boolean inited = false;


    public DocIndexerConfig() {
        super();
    }

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
        String to = config.getIndexFieldAs().get(from);
        return to == null ? from : to;
    }

    @SuppressWarnings("deprecation")
    protected void init() {
        if (inited)
            return;
        inited = true;
        setStoreDocuments(config.shouldStore());
        for (ConfigAnnotatedField af: config.getAnnotatedFields().values()) {

            // Define the properties that make up our complex field
        	List<ConfigAnnotation> annotations = new ArrayList<>(af.getAnnotations().values());
        	if (annotations.size() == 0)
        		throw new InputFormatConfigException("No annotations defined for field " + af.getName());
        	ConfigAnnotation mainAnnotation = annotations.get(0);
            ComplexField complexField = new ComplexField(af.getName(), mainAnnotation.getName(), getSensitivitySetting(mainAnnotation), false);
            addComplexField(complexField);

            IndexStructure indexStructure;
            if (indexer != null) {
                indexStructure = indexer.getSearcher().getIndexStructure();
                indexStructure.registerComplexField(complexField.getName(), complexField.getMainProperty().getName());

                // If the indexmetadata file specified a list of properties that shouldn't get a forward
                // index, make the new complex field aware of this.
                Set<String> noForwardIndexProps = indexStructure.getComplexFieldDesc(complexField.getName()).getNoForwardIndexProps();
                complexField.setNoForwardIndexProps(noForwardIndexProps);
            }

            ComplexFieldProperty propStartTag = complexField.addProperty(ComplexFieldUtil.START_TAG_PROP_NAME, getSensitivitySetting(ComplexFieldUtil.START_TAG_PROP_NAME), true);
            propStartTag.setForwardIndex(false);

            // Create properties for the other annotations
            for (int i = 1; i < annotations.size(); i++) {
            	ConfigAnnotation annot = annotations.get(i);
            	complexField.addProperty(annot.getName(), getSensitivitySetting(annot), false);
            }
            for (ConfigStandoffAnnotations standoff: af.getStandoffAnnotations()) {
                for (ConfigAnnotation annot: standoff.getAnnotations().values()) {
                    complexField.addProperty(annot.getName(), getSensitivitySetting(annot), false);
                }
            }
            if (!complexField.hasProperty(ComplexFieldUtil.PUNCTUATION_PROP_NAME)) {
                // Hasn't been created yet. Create it now.
                complexField.addProperty(ComplexFieldUtil.PUNCTUATION_PROP_NAME, getSensitivitySetting(ComplexFieldUtil.PUNCTUATION_PROP_NAME), false);
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
        for (ConfigProcessStep step: process) {
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
                        String fieldValue = currentLuceneDoc.get(field);
                        if (fieldValue != null)
                            result = fieldValue;
                    }
                    break;
                }
                case "append": {
                    String separator = param.containsKey("separator") ? param.get("separator") : " ";
                    String field = param.get("field");
                    String fieldValue = currentLuceneDoc.get(field);
                    if (fieldValue != null && fieldValue.length() > 0) {
                        if (result.length() > 0)
                            result += separator;
                        result += fieldValue;
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

}