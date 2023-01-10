package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.index.DocIndexerFactory.Format;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.results.DStream;
import nl.inl.blacklab.server.lib.results.ResultInputFormat;
import nl.inl.blacklab.server.lib.results.ResultListInputFormats;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;

/**
 * Get information about supported input formats.
 */
public class RequestHandlerListInputFormats extends RequestHandler {

    private final boolean isXsltRequest;

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

    @Override
    public int handle(DataStream ds) throws BlsException {
        if (urlResource != null && urlResource.length() > 0) {
            // Specific input format: either format information or XSLT request
            ResultInputFormat result = WebserviceOperations.inputFormat(urlResource);
            if (!isXsltRequest) {
                dstreamFormatResponse(ds, result);
            } else {
                dstreamFormatXsltResponse(ds, result);
            }
        } else {
            // Show list of supported input formats (for current user)
            ResultListInputFormats result = WebserviceOperations.listInputFormats(params);
            dstreamListFormatsResponse(ds, result);
        }
        return HTTP_OK;
    }

    private static void dstreamFormatResponse(DataStream ds, ResultInputFormat result) {
        ds.startMap()
                .entry("formatName", result.getConfig().getName())
                .entry("configFileType", result.getConfig().getConfigFileType())
                .entry("configFile", result.getFileContents())
                .endMap();
    }

    private static void dstreamFormatXsltResponse(DataStream ds, ResultInputFormat result) {
        ds.plain(result.getXslt());
    }

    private static void dstreamListFormatsResponse(DataStream ds, ResultListInputFormats result) {

        ds.startMap();
        {
            DStream.userInfo(ds, result.getUserInfo());

            // List supported input formats
            // Formats from other users are hidden in the master list, but are considered public for all other purposes (if you know the name)
            ds.startEntry("supportedInputFormats").startMap();
            for (Format format: result.getFormats()) {
                ds.startAttrEntry("format", "name", format.getId());
                {
                    ds.startMap();
                    {
                        ds.entry("displayName", format.getDisplayName())
                                .entry("description", format.getDescription())
                                .entry("helpUrl", format.getHelpUrl())
                                .entry("configurationBased", format.isConfigurationBased())
                                .entry("isVisible", format.isVisible());
                    }
                    ds.endMap();
                }
                ds.endAttrEntry();
            }
            ds.endMap().endEntry();
        }
        ds.endMap();
    }

}
