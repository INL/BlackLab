package nl.inl.blacklab.server.requesthandlers;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Doc;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.datastream.DataStreamXml;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.util.BlsUtils;

/**
 * Get information about the structure of an index.
 */
public class RequestHandlerDocContents extends RequestHandler {

    private boolean surroundWithRootElement;

    public static final Pattern XML_DECL = Pattern.compile("^\\s*<\\?xml\\s+version\\s*=\\s*([\"'])\\d\\.\\d\\1" +
            "(?:\\s+encoding\\s*=\\s*([\"'])[A-Za-z][A-Za-z0-9._-]*\\2)?" +
            "(?:\\s+standalone\\s*=\\s*([\"'])(?:yes|no)\\3)?\\s*\\?>\\s*");
    public static final Pattern NAMESPACE = Pattern.compile(" xmlns:[^=]+=\"[^\"]+\""); // xmlns:namespace="...." on root
    public static final Pattern ANONNAMESPACE = Pattern.compile("xmlns=\"([^ ]+)\""); // xmls="" on root
    public static final Pattern PREFIX = Pattern.compile("<([a-z]+):[^ ]+ |<([a-z]+):[^>]+>| ([a-z]+):[^=]+=\"");

    public RequestHandlerDocContents(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);

        int startAtWord = searchParam.getInteger("wordstart");
        int endAtWord = searchParam.getInteger("wordend");
        if (startAtWord < -1 || endAtWord < -1 || (startAtWord >= 0 && endAtWord >= 0 && endAtWord <= startAtWord)) {
            // Illegal value. Error will be thrown, so we'll need a root element.
            surroundWithRootElement = true;
        } else {
            if (startAtWord == -1 && endAtWord == -1) {
                // Full document; no need for another root element
                surroundWithRootElement = false;
            } else {
                // Part of document; surround with root element so we know for sure we'll have a single one
                surroundWithRootElement = true;
            }
        }
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
        DataStreamXml ds = (DataStreamXml)dso;
        int i = urlPathInfo.indexOf('/');
        String docPid = i >= 0 ? urlPathInfo.substring(0, i) : urlPathInfo;
        if (docPid.length() == 0)
            throw new BadRequest("NO_DOC_ID", "Specify document pid.");

        BlackLabIndex blIndex = blIndex();
        int docId = BlsUtils.getDocIdFromPid(blIndex, docPid);
        if (!blIndex.docExists(docId))
            throw new NotFound("DOC_NOT_FOUND", "Document with pid '" + docPid + "' not found.");
        Doc doc = blIndex.doc(docId);
        Document document = doc.luceneDoc(); //searchMan.getDocumentFromPid(indexName, docId);
        if (document == null)
            throw new InternalServerError("Couldn't fetch document with pid '" + docPid + "'.", "INTERR_FETCHING_DOCUMENT_CONTENTS");
        if (!mayView(blIndex.metadata(), document)) {
            return Response.unauthorized(ds, "Viewing the full contents of this document is not allowed.");
        }

        Hits hits = null;
        if (searchParam.hasPattern()) {
            //@@@ TODO: filter on document!
            searchParam.put("docpid", docPid);
            hits = searchParam.hits().execute();
        }

        String content;
        int startAtWord = searchParam.getInteger("wordstart");
        int endAtWord = searchParam.getInteger("wordend");
        if (startAtWord < -1 || endAtWord < -1 || (startAtWord >= 0 && endAtWord >= 0 && endAtWord <= startAtWord)) {
            throw new BadRequest("ILLEGAL_BOUNDARIES", "Illegal word boundaries specified. Please check parameters.");
        }

        // Note: we use the highlighter regardless of whether there's hits because
        // it makes sure our document fragment is well-formed.
        Hits hitsInDoc;
        if (hits == null) {
            hitsInDoc = Hits.immutableEmptyList(QueryInfo.create(blIndex));
        } else {
            hitsInDoc = hits.getHitsInDoc(docId);
        }
        content = doc.highlightContent(hitsInDoc, startAtWord, endAtWord);

        boolean outputXmlDeclaration = true;
        if (surroundWithRootElement) {
            ds.outputProlog();
            ds.startOpenEl(BlackLabServer.BLACKLAB_RESPONSE_ROOT_ELEMENT);
            outputXmlDeclaration = false;
            Matcher cm = PREFIX.matcher(content);
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
            String root = doc.contentsByCharPos(doc.index().mainAnnotatedField(), 0, 1024);
            Matcher m = NAMESPACE.matcher(root);
            Set<String> namespaces = new HashSet<>(2);
            while (m.find()) {
                //collect namespaces that bind prefixes
                namespaces.add(m.group());
                ds.plain(" ").plain(m.group());
            }
            // see if a prefix isn't bound
            if (prefixes.stream().noneMatch(s -> namespaces.stream().anyMatch(s1 -> s1.startsWith(" xmlns:" + s)))) {
                String msg = String.format("some namespace prefixes (%s) in doc %s are not declared on the document root element, only %s.",prefixes.toString(),docPid, namespaces.toString());
                logger.warn(msg);
                //throw new InternalServerError(msg);
            }

            // Handle any anonymous namespace on the root
            m = ANONNAMESPACE.matcher(root);
            if (m.find()) {
                ds.plain(" ").plain(m.group());
            }

            ds.endOpenEl();

        }
        Matcher m = XML_DECL.matcher(content);
        boolean hasXmlDeclaration = m.find();
        if (hasXmlDeclaration && !outputXmlDeclaration) {
            // We don't want another XML declaration; strip it
            content = content.substring(m.end());
        }
        if (!hasXmlDeclaration && outputXmlDeclaration) {
            // We haven't outputted an XML declaration yet, and there's none in the document. Do so now.
            ds.outputProlog();
        }
        ds.plain(content);
        if (surroundWithRootElement) {
            ds.closeEl();
        }
        return HTTP_OK;
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }
}
