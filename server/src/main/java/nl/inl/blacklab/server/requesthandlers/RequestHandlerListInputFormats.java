package nl.inl.blacklab.server.requesthandlers;

import java.io.BufferedReader;
import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;

import nl.inl.blacklab.index.DocIndexerFactory.Format;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.index.DocIndexerFactoryUserFormats;
import nl.inl.blacklab.server.index.DocIndexerFactoryUserFormats.IllegalUserFormatIdentifier;
import nl.inl.blacklab.server.index.IndexManager;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.requests.XslGenerator;

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
            return handleFormatRequest(ds, urlResource, isXsltRequest);
        }

        // List all available input formats
        dstreamListFormatsResponse(ds, user, indexMan);
        return HTTP_OK;
    }

    private static int handleFormatRequest(DataStream ds, String formatName, boolean isXsltRequest) {
        Format format = DocumentFormats.getFormat(formatName);
        if (format == null)
            throw new NotFound("NOT_FOUND", "The format '" + formatName + "' does not exist.");
        if (!format.isConfigurationBased())
            throw new NotFound("NOT_FOUND", "The format '" + formatName
                    + "' is not configuration-based, and therefore cannot be displayed.");

        ConfigInputFormat config = format.getConfig();
        if (isXsltRequest) {
            String xslt = XslGenerator.generateXsltFromConfig(config);
            ds.plain(xslt);
            return HTTP_OK;
        }

        String fileContents;
        try (BufferedReader reader = config.getFormatFile()) {
            fileContents = IOUtils.toString(reader);
        } catch (IOException e1) {
            throw new RuntimeException(e1);
        }
        dstreamGetFormatResponse(ds, formatName, config, fileContents);
        return HTTP_OK;
    }

    private static void dstreamListFormatsResponse(DataStream ds, User user, IndexManager indexMan) {
        if (user.isLoggedIn() && indexMan.getUserFormatManager() != null)
            indexMan.getUserFormatManager().loadUserFormats(user.getUserId());

        ds.startMap();
        DStream.userInfo(ds, user.isLoggedIn(), user.getUserId(), indexMan.canCreateIndex(user));

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
    }

    private static void dstreamGetFormatResponse(DataStream ds, String formatName, ConfigInputFormat config, String fileContents) {
        ds.startMap()
                .entry("formatName", formatName)
                .entry("configFileType", config.getConfigFileType())
                .entry("configFile", fileContents)
                .endMap();
    }

}
