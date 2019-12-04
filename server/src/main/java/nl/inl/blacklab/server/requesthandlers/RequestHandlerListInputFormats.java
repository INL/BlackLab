package nl.inl.blacklab.server.requesthandlers;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.index.DocIndexerFactory.Format;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.indexers.config.ConfigAnnotatedField;
import nl.inl.blacklab.indexers.config.ConfigAnnotation;
import nl.inl.blacklab.indexers.config.ConfigInlineTag;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.index.DocIndexerFactoryUserFormats;
import nl.inl.blacklab.server.index.DocIndexerFactoryUserFormats.IllegalUserFormatIdentifier;
import nl.inl.blacklab.server.jobs.User;

/**
 * Get information about supported input formats.
 */
public class RequestHandlerListInputFormats extends RequestHandler {

    private boolean isXsltRequest;

    public RequestHandlerListInputFormats(BlackLabServer servlet, HttpServletRequest request, User user,
            String indexName, String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
        isXsltRequest = urlResource != null && urlResource.length() > 0 && urlPathInfo != null
                && urlPathInfo.equals("xslt");
    }

    @Override
    public boolean isCacheAllowed() {
        return false; // You can create/delete formats, don't cache the list
    }

    @Override
    public DataFormat getOverrideType() {
        // Application expects this MIME type, don't disappoint
        if (isXsltRequest)
            return DataFormat.XML;
        return super.getOverrideType();
    }

    @Override
    public boolean omitBlackLabResponseRootElement() {
        return isXsltRequest;
    }

    private static class XslGenerator {
        public static String applyTemplates(String selector) {
            selector = StringUtils.stripStart(selector, ".");

            return "<xsl:apply-templates select=\"" + selector + "\" />";
        }

        public static String beginTemplate(String match) {
            match = StringUtils.stripStart(match, ".");
            return "<xsl:template match=\"" + match + "\">";
        }

        public static final String endTemplate = "</xsl:template>";

        /*
         * NOTE: leaves start as-is, but strips ./ if needed (//a/b will work)
         * handles nulls
         */
        public static String joinXpath(String a, String b) {

            // 	a 			-> a
            //	/a			-> a
            // 	./a			-> a
            // 	//a 		-> //a
            // 	.//a		-> //a

            // 	b			-> /b
            // 	/b			-> /b
            // 	./b			-> /b
            // 	//b			-> //b
            // .//b			-> //b

            // return a+/+b

            // strip any leading . (since it's implicit)
            a = normalizeXpath(a);
            b = normalizeXpath(b);

            // split and explode into cartesian product...
            // a|b c -> a/c | b/c
            // because a/(b|c) is not valid xpath
            String[] asplit = StringUtils.split(a, "|");
            String[] bsplit = StringUtils.split(b, "|");
            List<String> result = new ArrayList<>();
            if (asplit != null && asplit.length > 0) {
                for (String _a : asplit) {
                    if (bsplit != null && bsplit.length > 0) {
                        for (String _b : bsplit) {
                            if (_b.startsWith("/"))
                                result.add(StringUtils.join(_a, _b));
                            else
                                result.add(StringUtils.join(new String[] { _a, _b }, '/'));
                        }
                    } else {
                        result.add(_a);
                    }
                }
            } else if (bsplit != null && bsplit.length > 0) {
                for (String _b : bsplit)
                    result.add(_b);
            }

            String joined = StringUtils.join(result, "|");
            if (joined.isEmpty())
                return ".";

            return joined;
        }

        // Strip leading and trailing (./)
        // leading // is preserved
        // normalizeXpath(null) -> null
        public static String normalizeXpath(String xpath) {
            xpath = StringUtils.stripStart(xpath, ".");
            if (!StringUtils.startsWith(xpath, "//"))
                xpath = StringUtils.stripStart(xpath, "/");

            xpath = StringUtils.stripEnd(xpath, "./");
            return xpath;
        }

        public static String joinXpath(String... strings) {
            // accumulate over all strings
            String result = null;
            for (String s : strings)
                result = joinXpath(result, s);

            return result;
        }

        @SuppressWarnings("unused")
        private static String joinIgnoreNull(String[] values, char separator) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                if (values[i] == null || values[i].isEmpty())
                    continue;

                if (sb.length() > 0)
                    sb.append(separator);
                sb.append(values[i]);
            }
            return sb.toString();
        }
    }

    @Override
    public int handle(DataStream ds) throws BlsException {

        if (urlResource != null && urlResource.length() > 0) {
            Format format = DocumentFormats.getFormat(urlResource);
            if (format == null)
                throw new NotFound("NOT_FOUND", "The format '" + urlResource + "' does not exist.");

            if (!format.isConfigurationBased())
                throw new NotFound("NOT_FOUND", "The format '" + urlResource
                        + "' is not configuration-based, and therefore cannot be displayed.");

            ConfigInputFormat config = format.getConfig();
            if (isXsltRequest)
                return handleXsltRequest(ds, config);

            try (BufferedReader reader = config.getFormatFile()) {
                ds.startMap()
                        .entry("formatName", urlResource)
                        .entry("configFileType", FilenameUtils.getExtension(config.getReadFromFile().getName()).toLowerCase())
                        .entry("configFile", IOUtils.toString(reader))
                        .endMap();
                return HTTP_OK;
            } catch (IOException e1) {
                throw new RuntimeException(e1);
            }
        }

        if (user.isLoggedIn() && indexMan.getUserFormatManager() != null)
            indexMan.getUserFormatManager().loadUserFormats(user.getUserId());

        ds.startMap();
        ds.startEntry("user").startMap();
        ds.entry("loggedIn", user.isLoggedIn());
        if (user.isLoggedIn())
            ds.entry("id", user.getUserId());
        boolean canCreateIndex = user.isLoggedIn() ? indexMan.canCreateIndex(user) : false;
        ds.entry("canCreateIndex", canCreateIndex);
        ds.endMap().endEntry();

        // List supported input formats
        // Formats from other users are hidden in the master list, but are considered public for all other purposes (if you know the name)
        ds.startEntry("supportedInputFormats").startMap();
        for (Format format : DocumentFormats.getFormats()) {
            try {
                String userId = DocIndexerFactoryUserFormats.getUserIdOrFormatName(format.getId(), false);
                // Other user's formats are not explicitly enumerated (but should still be considered public)
                if (!userId.equals(user.getUserId()))
                    continue;
            } catch (IllegalUserFormatIdentifier e) {
                // Alright, it's evidently not a user format, that means it's public. List it.
            }

            ds.startAttrEntry("format", "name", format.getId()).startMap()
                    .entry("displayName", format.getDisplayName())
                    .entry("description", format.getDescription())
                    .entry("helpUrl", format.getHelpUrl())
                    .entry("configurationBased", format.isConfigurationBased())
                    .entry("isVisible", format.isVisible())
                    .endMap().endAttrEntry();
        }
        ds.endMap().endEntry();
        ds.endMap();

        return HTTP_OK;
    }

    /**
     * Generate an xslt document that can transform documents into a basic html view
     * with some minor formatting for highlights.
     *
     * @param ds
     * @param config
     * @return http code
     * @throws NotFound if the config does not pertain to an XML-based format (tsv,
     *             plaintext, etc)
     */
    private static int handleXsltRequest(DataStream ds, ConfigInputFormat config) throws NotFound {
        if (!ConfigInputFormat.FileType.XML.equals(config.getFileType()))
            throw new NotFound("NOT_FOUND", "The format '" + config.getName()
                    + "' does not apply to XML-type documents, and cannot be converted to XSLT.");

        // We want an XSLT from this config.
        StringBuilder xslt = new StringBuilder();
        StringBuilder nameSpaces = new StringBuilder();
        StringBuilder excludeResultPrefixes = new StringBuilder();

        String optDefaultNameSpace = "";
        for (Entry<String, String> e : config.getNamespaces().entrySet()) {
            if (e.getKey().isEmpty())
                optDefaultNameSpace = "xpath-default-namespace=\"" + e.getValue() + "\"";
            else {
                nameSpaces.append(" xmlns:" + e.getKey() + "=\"" + e.getValue() + "\"");
                excludeResultPrefixes.append(" " + e.getKey());
            }
        }
        xslt.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<xsl:stylesheet version=\"2.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                        + optDefaultNameSpace + nameSpaces + " exclude-result-prefixes=\"" + excludeResultPrefixes
                        + "\">")
                .append("<xsl:output encoding=\"utf-8\" method=\"html\" omit-xml-declaration=\"yes\" />");

        // swallow everything not explicitly matched.
        xslt.append("<xsl:template match='text()' priority='-10' >")
        // enable these to show any removed text between a pair of brackets
//			.append("<xsl:text>[</xsl:text>")
//			.append("<xsl:value-of select='.'/>")
//			.append("<xsl:text>]</xsl:text>")
                .append(XslGenerator.endTemplate);

        // transform inserted <hl> tags into spans
        // use local-name to sidestep namespaces (we can do this because the hl tags are inserted by blacklab, so we know the data in this case)
        xslt.append(XslGenerator.beginTemplate("*[local-name(.)='hl']"))
                .append("<span class=\"hl\">")
                .append(XslGenerator.applyTemplates("node()")) // we don't know what level we're at, so explicitly match everything else
                .append("</span>")
                .append(XslGenerator.endTemplate);

        // generate the templates for all words
        // NOTE: 'annotation' here refers to a linguistic annotation - not an xml attribute/annotation!
        for (ConfigAnnotatedField f : config.getAnnotatedFields().values()) {
            if (f.getAnnotations().isEmpty())
                continue; // No annotations for this word at all? can't display anything...

            // By default attempt to display the annotations of the word named "lemma" and "word"
            ConfigAnnotation wordAnnot = f.getAnnotations().get("word");
            ConfigAnnotation lemmaAnnot = f.getAnnotations().get("lemma");

            // Since the annotation can be named anything there is no guarantee there is a "word" annotation
            // (it's just a reasonable default guess), so just attempt to display whatever is first in the list otherwise
            // This ought to be correct - see DocIndexerConfig::init(), it sets the main annotation as the first annotation in the list
            // As for the "lemma" annotation that's used to generate hover tooltips, it's just a reasonable guess
            if (wordAnnot == null)
                wordAnnot = f.getAnnotations().values().iterator().next();

            // Begin word template
            // TODO: take containerPath into account too (optional, goes between documentPath and wordPath)
            String wordBase = XslGenerator.joinXpath(config.getDocumentPath(), f.getContainerPath(), f.getWordsPath());
            xslt.append(XslGenerator.beginTemplate(wordBase))
                    .append("<span class=\"word\">");
            
            // Extract lemma
            if (lemmaAnnot != null && lemmaAnnot != wordAnnot && lemmaAnnot.getValuePath() != null) {
                xslt.append("<xsl:attribute name=\"data-toggle\" select=\"'tooltip'\"/>");
                xslt.append("<xsl:attribute name=\"data-lemma\">")
                        .append("<xsl:value-of select='"
                                + XslGenerator.joinXpath(lemmaAnnot.getBasePath(), lemmaAnnot.getValuePath()).replace("'", "&apos;") + "'/>")
                        .append("</xsl:attribute>");
            }

            // extract word
            xslt.append("<xsl:value-of select=\""
                    + XslGenerator.joinXpath(wordAnnot.getBasePath(), wordAnnot.getValuePath()).replace("'", "&apos;") + "\"/>");

            // end word template
            xslt.append("</span>")
                .append("<xsl:text> </xsl:text>") // space between words.
                .append(XslGenerator.endTemplate);

            // Generate rules for inline tags
            for (ConfigInlineTag inlineTag : f.getInlineTags()) {
                String inlineTagPath = XslGenerator.joinXpath(config.getDocumentPath(), f.getContainerPath(),
                        inlineTag.getPath());
                String cssClass;
                if (!inlineTag.getDisplayAs().isEmpty())
                    cssClass = inlineTag.getDisplayAs();
                else
                    cssClass = inlineTag.getPath().replaceAll("\\b\\w+:", "").replaceAll("\\W+", " ").trim()
                            .replace(" ", "-");
                xslt.append(XslGenerator.beginTemplate(inlineTagPath))
                        .append("<span class=\"" + cssClass + "\">")
                        .append(XslGenerator.applyTemplates("node()"))
                        .append("</span>")
                        .append(XslGenerator.endTemplate);
            }

            /*
             * Output a warning when not a single word can be matched within the document.
             * Why do we do this? Well, xslt is very strict about namespaces.
             * When a default namespace is provided we set the xpath-default-namespace in the xslt
             * This means that EVERY SINGLE TEMPLATE will ONLY match nodes within that namespace (so only children of, or the node that declares xmlns='http://my.default.namespace/')
             * When a default namespace is provided, but some node within our generated xpath to the word element is not within that namespace,
             * nothing will be matched.
             *
             * An example:
             * <documentRoot>
             * 		<textContainer xmlns="my-fancy-namespace">
             * 			<word lemma="blah">blah</word>
             * 		</textContainer>
             * </documentRoot>
             *
             * Now in our config documentPath = "//documentRoot"
             *  and wordPath = ".//word" (relative to the documentPath)
             * The xpath to get the words is generated by concatenating the documentPath and wordPath into "//documentRoot//word"
             * Because documentRoot is not within the "my-fancy-namespace" that was declared as default namespace, nothing will ever be matched.
             *
             * To warn the user of this, we test if any words exist, and output a warning if we can't find any.
             *
             * Edit 05-jul-2018 (Koen)
             * --------------------
             * This comment is no longer strictly true, as we now strip namespaces from the source document altogether in some cases.
             * but it helps explain the reasoning behind stripping namespaces.
             */
            String namespaceWarningMessage = "No words have been found within this entire document. " +
                    "This usually happens when your document contains namespaces, but the format you used to index the document doesn't use any namespaces. "
                    +
                    "When the format doesn't use namespaces, blacklab will ignore them during indexing, but the xslt used to turn the document into an html snippet doesn't, "
                    +
                    "so the words can't be found. " +
                    "To fix this, you will need to add the namespaces in your format and use them in the xpaths. " +
                    "If your document contains a default namespace, you can declare it as an empty name (\"\": \"http://my-default-namespace.site/namespace\"). "
                    +
                    "This may also happen if the generated xslt is applied to a partial document, such as when using the wordstart and wordend parameters when requesting its contents from blacklab-server.";

            // Entry point (after optional namespace removal)
            xslt.append(
                    "<xsl:template match=\"/\" mode=\"pass2\">" +
                            "<xsl:choose>" +
                            "<xsl:when test=\"" + wordBase.replace("\"", "\"\"") + "\">" +
                            "<xsl:apply-templates/>" +
                            "</xsl:when>" +
                            "<xsl:otherwise>" +
                            "<xsl:text>" + namespaceWarningMessage + "</xsl:text>" +
                            "</xsl:otherwise>" +
                            "</xsl:choose>" +
                            "</xsl:template>");

            // Strip namespaces before delegating to entry
            // A preprocessing step that (optionally) strips namespaces from the document before passing it on to the template above.
            // (This happens when there are no namespaces declared - which in blacklab means to ignore all namespaces, this is impossible in xslt without this workaround)
            if (config.getNamespaces().isEmpty()) {
                xslt.append(XslGenerator.beginTemplate("/"))
                        .append("<!-- Since no namespaces are used in the source document format, namespaces are stripped from the document before processing it -->")
                        .append("<xsl:variable name=\"withoutNamespaces\"><xsl:apply-templates select=\".\" mode=\"remove-namespaces\"/></xsl:variable>")
                        .append("<xsl:apply-templates select=\"$withoutNamespaces\" mode=\"pass2\"/>")
                        .append(XslGenerator.endTemplate);
            } else {
                xslt.append(XslGenerator.beginTemplate("/"))
                        .append("<xsl:apply-templates select=\".\" mode=\"pass2\"/>")
                        .append(XslGenerator.endTemplate);
            }

            // templates to strip namespaces
            xslt.append(
                    "<xsl:template match=\"*\" mode=\"remove-namespaces\">" +
                            "<xsl:element name=\"{local-name()}\">" +
                            "<xsl:apply-templates select=\"@* | node()\" mode=\"remove-namespaces\"/>" +
                            "</xsl:element>" +
                            "</xsl:template>" +
                            "<xsl:template match=\"@*\" mode=\"remove-namespaces\">" +
                            "<xsl:attribute name=\"{local-name()}\">" +
                            "<xsl:value-of select=\".\"/>" +
                            "</xsl:attribute>" +
                            "</xsl:template>" +
                            "<xsl:template match=\"comment() | text() | processing-instruction()\" mode=\"remove-namespaces\">"
                            +
                            "<xsl:copy/>" +
                            "</xsl:template>");
        }
        xslt.append("</xsl:stylesheet>");
        ds.plain(xslt.toString());
        return HTTP_OK;
    }

}
