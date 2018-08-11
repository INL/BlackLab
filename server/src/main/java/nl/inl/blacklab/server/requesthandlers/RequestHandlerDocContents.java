package nl.inl.blacklab.server.requesthandlers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Doc;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.jobs.JobWithHits;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.util.BlsUtils;

/**
 * Get information about the structure of an index.
 */
public class RequestHandlerDocContents extends RequestHandler {

    boolean surroundWithRootElement;

    Pattern XML_DECL = Pattern.compile("^\\s*<\\?xml\\s+version\\s*=\\s*([\"'])\\d\\.\\d\\1" +
            "(?:\\s+encoding\\s*=\\s*([\"'])[A-Za-z][A-Za-z0-9._-]*\\2)?" +
            "(?:\\s+standalone\\s*=\\s*([\"'])(?:yes|no)\\3)?\\s*\\?>\\s*");

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
        return !surroundWithRootElement;
    }

    @Override
    public int handle(DataStream ds) throws BlsException {
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
            throw new InternalServerError("Couldn't fetch document with pid '" + docPid + "'.", 9);
        if (!mayView(blIndex.metadata(), document)) {
            return Response.unauthorized(ds, "Viewing the full contents of this document is not allowed.");
        }

        Hits hits = null;
        if (searchParam.hasPattern()) {
            //@@@ TODO: filter on document!
            searchParam.put("docpid", docPid);
            JobWithHits search;
            search = (JobWithHits) searchMan.search(user, searchParam.hits(), true);
            try {
                hits = search.getHits();
            } finally {
                search.decrRef();
                search = null;
            }
        }

        String content;
        int startAtWord = searchParam.getInteger("wordstart");
        int endAtWord = searchParam.getInteger("wordend");
        if (startAtWord < -1 || endAtWord < -1 || (startAtWord >= 0 && endAtWord >= 0 && endAtWord <= startAtWord)) {
            throw new BadRequest("ILLEGAL_BOUNDARIES", "Illegal word boundaries specified. Please check parameters.");
        }

        // Note: we use the highlighter regardless of whether there's hits because
        // it makes sure our document fragment is well-formed.
        Hits hitsInDoc = hits == null ? Hits.emptyList(blIndex, blIndex.mainAnnotatedField(), null) : hits.getHitsInDoc(docId);
        content = doc.highlightContent(hitsInDoc, startAtWord, endAtWord);

        boolean outputXmlDeclaration = true;
        if (surroundWithRootElement) {
            // We've already outputted the XML declaration; don't do so again
            outputXmlDeclaration = false;
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
        return HTTP_OK;
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }
}
