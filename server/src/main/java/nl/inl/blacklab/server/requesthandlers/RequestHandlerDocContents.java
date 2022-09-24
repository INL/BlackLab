package nl.inl.blacklab.server.requesthandlers;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.DocUtil;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.datastream.DataStreamXml;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotAuthorized;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.lib.SearchCreator;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.util.BlsUtils;

/**
 * Show (part of) the original contents of a document.
 */
public class RequestHandlerDocContents extends RequestHandler {

    /** Show (part of) the original contents of a document. */
    static class ReqDocContents {

        public static final Pattern XML_DECL = Pattern.compile("^\\s*<\\?xml\\s+version\\s*=\\s*([\"'])\\d\\.\\d\\1" +
                "(?:\\s+encoding\\s*=\\s*([\"'])[A-Za-z][A-Za-z0-9._-]*\\2)?" +
                "(?:\\s+standalone\\s*=\\s*([\"'])(?:yes|no)\\3)?\\s*\\?>\\s*");

        /** xmlns:namespace="...." on root */
        public static final Pattern NAMED_NAMESPACE = Pattern.compile(" xmlns:[^=]+=\"[^\"]+\"");

        /** xmls="" on root */
        public static final Pattern ANON_NAMESPACE = Pattern.compile("xmlns=\"([^ ]+)\"");

        /** an xml namespace prefix (for collecting all of the prefixes used in the document) */
        public static final Pattern NAMESPACE_PREFIX = Pattern.compile("<([a-z]+):[^ ]+ |<([a-z]+):[^>]+>| ([a-z]+):[^=]+=\"");


        private final SearchCreator params;

        private final String docPid;

        /** was the full document requested? */
        private boolean isFullDocument;

        /** Should an XML declaration be prepended to the results? */
        private boolean mustOutputXmlDeclaration;

        private String content;

        private Set<String> namespaces;

        private Set<String> anonNamespaces;

        public ReqDocContents(SearchCreator params, String docPid) throws BlsException, InvalidQuery {
            this.params = params;
            this.docPid = docPid;
            handle();
        }

        public boolean isFullDocument() {
            return isFullDocument;
        }

        public boolean needsXmlDeclaration() {
            return mustOutputXmlDeclaration;
        }

        public String getContent() {
            return content;
        }

        public Set<String> getNamespaces() {
            return namespaces;
        }

        public Set<String> getAnonNamespaces() {
            return anonNamespaces;
        }

        public void handle() throws BlsException, InvalidQuery {
            if (docPid.length() == 0)
                throw new BadRequest("NO_DOC_ID", "Specify document pid.");

            isFullDocument = false;
            int startAtWord = params.getWordStart();
            int endAtWord = params.getWordEnd();
            if (startAtWord < -1 || endAtWord < -1 || (endAtWord >= 0 && endAtWord <= startAtWord)) {
                // Illegal value. Error will be thrown.
                throw new BadRequest("ILLEGAL_BOUNDARIES", "Illegal word boundaries specified. Please check parameters.");
            } else {
                if (startAtWord == -1 && endAtWord == -1) {
                    // Full document; no need for another root element
                    isFullDocument = true;
                }
            }

            BlackLabIndex blIndex = params.blIndex();
            int docId = BlsUtils.getDocIdFromPid(blIndex, docPid);
            if (!blIndex.docExists(docId))
                throw new NotFound("DOC_NOT_FOUND", "Document with pid '" + docPid + "' not found.");
            Document document = blIndex.luceneDoc(docId);
            if (document == null)
                throw new InternalServerError("Couldn't fetch document with pid '" + docPid + "'.", "INTERR_FETCHING_DOCUMENT_CONTENTS");
            if (!mayView(blIndex.metadata(), document))
                throw new NotAuthorized("Viewing the full contents of this document is not allowed. For more information, read about 'contentViewable': https://inl.github.io/BlackLab/how-to-configure-indexing.html.");

            Hits hits = null;
            if (params.hasPattern()) {
                params.setFilterByDocumentPid(docPid);
                hits = params.hitsSample().execute();
            }

            // Note: we use the highlighter regardless of whether there's hits because
            // it makes sure our document fragment is well-formed.
            Hits hitsInDoc;
            if (hits == null) {
                hitsInDoc = Hits.empty(QueryInfo.create(blIndex));
            } else {
                hitsInDoc = hits.getHitsInDoc(docId);
            }
            content = DocUtil.highlightContent(params.blIndex(), docId, hitsInDoc, startAtWord, endAtWord);

            Matcher m = XML_DECL.matcher(content);
            boolean hasXmlDeclaration = m.find();
            mustOutputXmlDeclaration = false;
            if (hasXmlDeclaration && !isFullDocument) {
                // We don't want another XML declaration; strip it
                content = content.substring(m.end());
            } else if (!hasXmlDeclaration && isFullDocument) {
                // Make sure there's an XML declaration
                mustOutputXmlDeclaration = true;
            }

            if (!isFullDocument) {

                Matcher cm = NAMESPACE_PREFIX.matcher(content);
                Set<String> prefixes = new HashSet<>(2);
                while(cm.find()) {
                    // collect unique prefixes that need to be bound
                    String prefix = cm.group(1) == null ? cm.group(2) == null ? cm.group(3) : cm.group(2) : cm.group(1);
                    if (!prefixes.contains(prefix) && !prefix.equals("xml")) { // ignore special xml prefix
                        prefixes.add(prefix);
                    }
                }
                // here we may need to include namespace declarations
                // retrieve the first bit of the document, try to find namespaces
                String root = DocUtil.contentsByCharPos(params.blIndex(), docId, document, params.blIndex().mainAnnotatedField(), 0, 1024);
                m = NAMED_NAMESPACE.matcher(root);
                namespaces = new HashSet<>();
                while (m.find()) {
                    //collect namespaces that bind prefixes
                    namespaces.add(m.group());
                }
                // see if a prefix isn't bound
                if (prefixes.stream().noneMatch(s -> namespaces.stream().anyMatch(s1 -> s1.startsWith(" xmlns:" + s)))) {
                    String msg = String.format("some namespace prefixes (%s) in doc %s are not declared on the document root element, only %s.", prefixes,docPid, namespaces);
                    logger.warn(msg);
                    //throw new InternalServerError(msg);
                }
                // Handle any anonymous namespace on the root
                m = ANON_NAMESPACE.matcher(root);
                anonNamespaces = new HashSet<>();
                if (m.find()) {
                    anonNamespaces.add(m.group());
                }
            }
        }
    }

    public RequestHandlerDocContents(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public DataFormat getOverrideType() {
        // Application expects this MIME type, don't disappoint
        return DataFormat.XML;
    }

    @Override
    public boolean omitBlackLabResponseRootElement() {
        return true;
    }

    @Override
    public int handle(DataStream dso) throws BlsException, InvalidQuery {
        // Find the document pid
        int i = urlPathInfo.indexOf('/');
        String docPid = i >= 0 ? urlPathInfo.substring(0, i) : urlPathInfo;

        ReqDocContents opGetDocContents = new ReqDocContents(params, docPid);

        DataStreamXml ds = (DataStreamXml)dso;

        if (opGetDocContents.needsXmlDeclaration()) {
            // We haven't outputted an XML declaration yet, and there's none in the document. Do so now.
            ds.outputProlog();
        }

        // Output root element and namespaces if necessary
        // (i.e. when we're not returning the full document, only part of it)
        if (!opGetDocContents.isFullDocument()) {
            // Surround with root element and make sure it has the required namespaces
            ds.outputProlog();
            ds.startOpenEl(BlackLabServer.BLACKLAB_RESPONSE_ROOT_ELEMENT);
            for (String ns: opGetDocContents.getNamespaces()) {
                ds.plain(" ").plain(ns);
            }
            for (String anon: opGetDocContents.getAnonNamespaces()) {
                ds.plain(" ").plain(anon);
            }
            ds.endOpenEl();
        }

        // Output (part of) the document
        ds.plain(opGetDocContents.getContent());

        if (!opGetDocContents.isFullDocument()) {
            // Close the root el we opened
            ds.closeEl();
        }
        return HTTP_OK;
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }
}
