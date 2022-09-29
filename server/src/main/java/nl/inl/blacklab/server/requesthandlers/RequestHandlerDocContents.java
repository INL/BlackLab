package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.datastream.DataStreamXml;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.requests.ResultDocContents;
import nl.inl.blacklab.server.lib.User;

/**
 * Show (part of) the original contents of a document.
 */
public class RequestHandlerDocContents extends RequestHandler {

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
    public int handle(DataStream ds) throws BlsException, InvalidQuery {
        // Find the document pid
        int i = urlPathInfo.indexOf('/');
        String docPid = i >= 0 ? urlPathInfo.substring(0, i) : urlPathInfo;
        ResultDocContents resultDocContents = ResultDocContents.get(params, docPid);
        dstreamDocContents((DataStreamXml)ds, resultDocContents);
        return HTTP_OK;
    }

    private void dstreamDocContents(DataStreamXml ds, ResultDocContents resultDocContents) {
        if (resultDocContents.needsXmlDeclaration()) {
            // We haven't outputted an XML declaration yet, and there's none in the document. Do so now.
            ds.outputProlog();
        }

        // Output root element and namespaces if necessary
        // (i.e. when we're not returning the full document, only part of it)
        if (!resultDocContents.isFullDocument()) {
            // Surround with root element and make sure it has the required namespaces
            ds.outputProlog();
            ds.startOpenEl(BlackLabServer.BLACKLAB_RESPONSE_ROOT_ELEMENT);
            for (String ns: resultDocContents.getNamespaces()) {
                ds.plain(" ").plain(ns);
            }
            for (String anon: resultDocContents.getAnonNamespaces()) {
                ds.plain(" ").plain(anon);
            }
            ds.endOpenEl();
        }

        // Output (part of) the document
        ds.plain(resultDocContents.getContent());

        if (!resultDocContents.isFullDocument()) {
            // Close the root el we opened
            ds.closeEl();
        }
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }
}
