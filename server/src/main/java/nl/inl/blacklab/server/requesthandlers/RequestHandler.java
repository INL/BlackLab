package nl.inl.blacklab.server.requesthandlers;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import nl.inl.blacklab.instrumentation.RequestInstrumentationProvider;
import nl.inl.blacklab.search.*;
import nl.inl.blacklab.search.results.*;
import nl.inl.blacklab.server.jobs.ContextSettings;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.lucene.document.Document;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.requestlogging.SearchLogger;
import nl.inl.blacklab.resultproperty.DocGroupProperty;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFields;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFieldGroup;
import nl.inl.blacklab.search.indexmetadata.MetadataFieldGroups;
import nl.inl.blacklab.search.indexmetadata.MetadataFields;
import nl.inl.blacklab.searches.SearchFacets;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.IndexNotFound;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.index.Index.IndexStatus;
import nl.inl.blacklab.server.index.IndexManager;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.util.ServletUtil;

/**
 * Base class for request handlers, to handle the different types of requests.
 * The static handle() method will dispatch the request to the appropriate
 * subclass.
 */
public abstract class RequestHandler {
    static final Logger logger = LogManager.getLogger(RequestHandler.class);

    private static final String METADATA_FIELD_CONTENT_VIEWABLE = "contentViewable";

    public static final int HTTP_OK = HttpServletResponse.SC_OK;

    protected RequestInstrumentationProvider instrumentationProvider;

    /** The available request handlers by name */
    static Map<String, Class<? extends RequestHandler>> availableHandlers;

    // Fill the map with all the handler classes
    static {
        availableHandlers = new HashMap<>();
        //availableHandlers.put("cache-info", RequestHandlerCacheInfo.class);
        availableHandlers.put("debug", RequestHandlerDebug.class);
        availableHandlers.put("docs", RequestHandlerDocs.class);
        availableHandlers.put("docs-grouped", RequestHandlerDocsGrouped.class);
        availableHandlers.put("docs-csv", RequestHandlerDocsCsv.class);
        availableHandlers.put("docs-grouped-csv", RequestHandlerDocsCsv.class);
        availableHandlers.put("doc-contents", RequestHandlerDocContents.class);
        availableHandlers.put("doc-snippet", RequestHandlerDocSnippet.class);
        availableHandlers.put("doc-info", RequestHandlerDocInfo.class);
        availableHandlers.put("fields", RequestHandlerFieldInfo.class);
        //availableHandlers.put("help", RequestHandlerBlsHelp.class);
        availableHandlers.put("hits", RequestHandlerHits.class);
        availableHandlers.put("hits-grouped", RequestHandlerHitsGrouped.class);
        availableHandlers.put("hits-csv", RequestHandlerHitsCsv.class);
        availableHandlers.put("hits-grouped-csv", RequestHandlerHitsCsv.class);
        availableHandlers.put("status", RequestHandlerIndexStatus.class);
        availableHandlers.put("termfreq", RequestHandlerTermFreq.class);
        availableHandlers.put("", RequestHandlerIndexMetadata.class);
        availableHandlers.put("explain", RequestHandlerExplain.class);
        availableHandlers.put("autocomplete", RequestHandlerAutocomplete.class);
        availableHandlers.put("sharing", RequestHandlerSharing.class);
    }

    /**
     * Handle a request by dispatching it to the corresponding subclass.
     *
     * @param servlet the servlet object
     * @param request the request object
     * @param debugMode debug mode request? Allows extra parameters to be used
     * @param outputType output type requested (XML, JSON or CSV)
     * @return the response data
     */
    public static RequestHandler create(BlackLabServer servlet, HttpServletRequest request, boolean debugMode,
            DataFormat outputType, RequestInstrumentationProvider instrumentationProvider) {

        // See if a user is logged in
        String requestId = instrumentationProvider.getRequestID(request).orElse("");
        ThreadContext.put("requestId", requestId);
        SearchManager searchManager = servlet.getSearchManager();
        User user = searchManager.getAuthSystem().determineCurrentUser(servlet, request);
        String debugHttpHeaderToken = searchManager.config().getAuthentication().getDebugHttpHeaderAuthToken();
        if (!user.isLoggedIn() && !StringUtils.isEmpty(debugHttpHeaderToken)) {
            if (request.getHeader("X-BlackLabAccessToken").equals(debugHttpHeaderToken)) {
                user = User.loggedIn(request.getHeader("X-BlackLabUserId"), request.getSession().getId());
            }
        }

        // Parse the URL
        String servletPath = StringUtils.strip(StringUtils.trimToEmpty(request.getPathInfo()), "/");
        String[] parts = servletPath.split("/", 3);
        String indexName = parts.length >= 1 ? parts[0] : "";
        RequestHandlerStaticResponse errorObj = new RequestHandlerStaticResponse(servlet, request, user, indexName,
                null, null);
        if (indexName.startsWith(":")) {
            if (!user.isLoggedIn())
                return errorObj.unauthorized("Log in to access your private index.");
            // Private index. Prefix with user id.
            indexName = user.getUserId() + indexName;
        }
        String urlResource = parts.length >= 2 ? parts[1] : "";
        String urlPathInfo = parts.length >= 3 ? parts[2] : "";
        boolean resourceOrPathGiven = urlResource.length() > 0 || urlPathInfo.length() > 0;
        boolean pathGiven = urlPathInfo.length() > 0;

        // Debug feature: sleep for x ms before carrying out the request
        if (debugMode && !doDebugSleep(request)) {
            return errorObj.error("ROUGH_AWAKENING", "I was taking a nice nap, but something disturbed me", HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }

        // If we're reading a private index, we must own it or be on the share list.
        // If we're modifying a private index, it must be our own.
        Index privateIndex = null;
        //logger.debug("Got indexName = \"" + indexName + "\" (len=" + indexName.length() + ")");
        if (indexName.contains(":")) {
            // It's a private index. Check if the logged-in user has access.
            if (!user.isLoggedIn())
                return errorObj.unauthorized("Log in to access a private index.");
            try {
                privateIndex = searchManager.getIndexManager().getIndex(indexName);
                if (!privateIndex.userMayRead(user))
                    return errorObj.unauthorized("You are not authorized to access this index.");
            } catch (IndexNotFound e) {
                // Ignore this here; this is either not an index name but some other request (e.g. cache-info)
                // or it is an index name but will trigger an error later.
            }
        }

        // Choose the RequestHandler subclass
        RequestHandler requestHandler = null;

        String method = request.getMethod();
        if (method.equals("DELETE")) {
            // Index given and nothing else?
            if (indexName.equals("input-formats")) {
                if (pathGiven)
                    return errorObj.methodNotAllowed("DELETE", null);
                requestHandler = new RequestHandlerDeleteFormat(servlet, request, user, indexName, urlResource,
                        urlPathInfo);
            } else {
                if (indexName.length() == 0 || resourceOrPathGiven) {
                    return errorObj.methodNotAllowed("DELETE", null);
                }
                if (privateIndex == null || !privateIndex.userMayDelete(user))
                    return errorObj.forbidden("You can only delete your own private indices.");
                requestHandler = new RequestHandlerDeleteIndex(servlet, request, user, indexName, null, null);
            }
        } else if (method.equals("PUT")) {
            return errorObj.methodNotAllowed("PUT", "Create new index with POST to /blacklab-server");
        } else {
            boolean postAsGet = false;
            if (method.equals("POST")) {
                if (indexName.length() == 0 && !resourceOrPathGiven) {
                    // POST to /blacklab-server/ : create private index
                    requestHandler = new RequestHandlerCreateIndex(servlet, request, user, indexName, urlResource,
                            urlPathInfo);
                } else if (indexName.equals("cache-clear")) {
                    // Clear the cache
                    if (resourceOrPathGiven) {
                        return errorObj.unknownOperation(indexName);
                    }
                    if (!debugMode) {
                        return errorObj
                                .unauthorized("You (" + request.getRemoteAddr() + ") are not authorized to do this.");
                    }
                    requestHandler = new RequestHandlerClearCache(servlet, request, user, indexName, urlResource,
                            urlPathInfo);
                } else if (indexName.equals("input-formats")) {
                    if (!user.isLoggedIn())
                        return errorObj.unauthorized("You must be logged in to add a format.");
                    requestHandler = new RequestHandlerAddFormat(servlet, request, user, indexName, urlResource,
                            urlPathInfo);
                } else if (ServletFileUpload.isMultipartContent(request)) {
                    // Add document to index
                    if (privateIndex == null || !privateIndex.userMayAddData(user))
                        return errorObj.forbidden("Can only POST to your own private indices.");
                    if (urlResource.equals("docs") && urlPathInfo.isEmpty()) {
                        if (!Index.isValidIndexName(indexName))
                            return errorObj.illegalIndexName(indexName);

                        // POST to /blacklab-server/indexName/docs/ : add data to index
                        requestHandler = new RequestHandlerAddToIndex(servlet, request, user, indexName, urlResource,
                                urlPathInfo);
                    } else if (urlResource.equals("sharing") && urlPathInfo.isEmpty()) {
                        if (!Index.isValidIndexName(indexName))
                            return errorObj.illegalIndexName(indexName);
                        // POST to /blacklab-server/indexName/sharing : set list of users to share with
                        requestHandler = new RequestHandlerSharing(servlet, request, user, indexName, urlResource,
                                urlPathInfo);
                    } else {
                        return errorObj.methodNotAllowed("POST", "You can only add new files at .../indexName/docs/");
                    }
                } else {
                    // Some other POST request; handle it as a GET.
                    // (this allows us to handle very large CQL queries that don't fit in a GET URL)
                    postAsGet = true;
                }
            }
            if (method.equals("GET") || (method.equals("POST") && postAsGet)) {
                if (indexName.equals("cache-info")) {
                    if (resourceOrPathGiven) {
                        return errorObj.unknownOperation(indexName);
                    }
                    if (!debugMode) {
                        return errorObj.unauthorized(
                                "You (" + request.getRemoteAddr() + ") are not authorized to see this information.");
                    }
                    requestHandler = new RequestHandlerCacheInfo(servlet, request, user, indexName, urlResource,
                            urlPathInfo);
                } else if (indexName.equals("help")) {
                    requestHandler = new RequestHandlerBlsHelp(servlet, request, user, indexName, urlResource,
                            urlPathInfo);
                } else if (indexName.equals("input-formats")) {
                    requestHandler = new RequestHandlerListInputFormats(servlet, request, user, indexName, urlResource,
                            urlPathInfo);
                } else if (indexName.length() == 0) {
                    // No index or operation given; server info
                    requestHandler = new RequestHandlerServerInfo(servlet, request, user, indexName, urlResource,
                            urlPathInfo);
                } else {
                    // Choose based on urlResource
                    try {
                        String handlerName = urlResource;

                        IndexStatus status = searchManager.getIndexManager().getIndex(indexName).getStatus();
                        if (status != IndexStatus.AVAILABLE && handlerName.length() > 0 && !handlerName.equals("debug")
                                && !handlerName.equals("fields") && !handlerName.equals("status")
                                && !handlerName.equals("sharing")) {
                            return errorObj.unavailable(indexName, status.toString());
                        }

                        if (debugMode && !handlerName.isEmpty()
                                && !Arrays.asList("hits", "hits-csv", "hits-grouped-csv", "docs",
                                        "docs-csv", "docs-grouped-csv", "fields", "termfreq",
                                        "status", "autocomplete", "sharing").contains(handlerName)) {
                            handlerName = "debug";
                        }

                        // HACK to avoid having a different url resource for
                        // the lists of (hit|doc) groups: instantiate a different
                        // request handler class in this case.
                        else if (handlerName.equals("docs") && urlPathInfo.length() > 0) {
                            handlerName = "doc-info";
                            String p = urlPathInfo;
                            if (p.endsWith("/"))
                                p = p.substring(0, p.length() - 1);
                            if (urlPathInfo.endsWith("/contents")) {
                                handlerName = "doc-contents";
                            } else if (urlPathInfo.endsWith("/snippet")) {
                                handlerName = "doc-snippet";
                            } else if (!p.contains("/")) {
                                // OK, retrieving metadata
                            } else {
                                return errorObj.unknownOperation(urlPathInfo);
                            }
                        } else if (handlerName.equals("hits") || handlerName.equals("docs")) {
                            if (!StringUtils.isBlank(request.getParameter("group"))) {
                                String viewgroup = request.getParameter("viewgroup");
                                if (StringUtils.isEmpty(viewgroup))
                                    handlerName += "-grouped"; // list of groups instead of contents
                            } else if (!StringUtils.isEmpty(request.getParameter("viewgroup"))) {
                                // "viewgroup" parameter without "group" parameter; error.

                                return errorObj.badRequest("ERROR_IN_GROUP_VALUE",
                                        "Parameter 'viewgroup' specified, but required 'group' parameter is missing.");
                            }
                            if (outputType == DataFormat.CSV)
                                handlerName += "-csv";
                        }

                        if (!availableHandlers.containsKey(handlerName))
                            return errorObj.unknownOperation(handlerName);

                        @SuppressWarnings("resource")
                        SearchLogger logger = servlet.getSearchManager().getLogDatabase().addRequest(indexName, handlerName, request.getParameterMap());
                        boolean succesfullyCreatedRequestHandler = false;
                        try {
                            Class<? extends RequestHandler> handlerClass = availableHandlers.get(handlerName);
                            Constructor<? extends RequestHandler> ctor = handlerClass.getConstructor(BlackLabServer.class,
                                    HttpServletRequest.class, User.class, String.class, String.class, String.class);
                            //servlet.getSearchManager().getSearcher(indexName); // make sure it's open
                            requestHandler = ctor.newInstance(servlet, request, user, indexName, urlResource, urlPathInfo);
                            requestHandler.setLogger(logger);
                            succesfullyCreatedRequestHandler = true;
                        } finally {
                            if (!succesfullyCreatedRequestHandler) {
                                // Operation didn't complete succesfully. Make sure logger gets closed cleanly.
                                // (if reqhandler *was* created succesfully, its cleanup() method will close the logger)
                                try {
                                    logger.close();
                                } catch (IOException e) {
                                    throw new InternalServerError("INTERR_CLOSING_LOGGER");
                                }
                            }
                        }
                    } catch (BlsException e) {
                        return errorObj.error(e.getBlsErrorCode(), e.getMessage(), e.getHttpStatusCode());
                    } catch (ReflectiveOperationException e) {
                        // (can only happen if the required constructor is not available in the RequestHandler subclass)
                        logger.error("Could not get constructor to create request handler", e);
                        return errorObj.internalError(e, debugMode, "INTERR_CREATING_REQHANDLER1");
                    } catch (IllegalArgumentException e) {
                        logger.error("Could not create request handler", e);
                        return errorObj.internalError(e, debugMode, "INTERR_CREATING_REQHANDLER2");
                    }
                }
            }
            if (requestHandler == null) {
                return errorObj.internalError("RequestHandler.create called with wrong method: " + method, debugMode,
                        "INTERR_WRONG_HTTP_METHOD");
            }
        }
        if (debugMode)
            requestHandler.setDebug(debugMode);

        requestHandler.setInstrumentationProvider(instrumentationProvider);
        requestHandler.setRequestId(requestId);

        return requestHandler;
    }

    protected SearchLogger searchLogger;

    private void setLogger(SearchLogger searchLogger) {
        this.searchLogger = searchLogger;
        if (searchParam != null)
            searchParam.setLogger(searchLogger);
    }

    private static boolean doDebugSleep(HttpServletRequest request) {
        String sleep = request.getParameter("sleep");
        if (sleep != null) {
            int sleepMs = Integer.parseInt(sleep);
            if (sleepMs > 0 || sleepMs <= 3600000) {
                try {
                    logger.debug("Debug sleep requested (" + sleepMs + "ms). Zzzzz...");
                    Thread.sleep(sleepMs);
                    logger.debug("Ahh, that was a nice snooze!");
                } catch (InterruptedException e) {
                    return false;
                }
            }
        }
        return true;
    }

    boolean debugMode;

    /** The servlet object */
    BlackLabServer servlet;

    /** The HTTP request object */
    HttpServletRequest request;

    /** Search parameters from request */
    SearchParameters searchParam;

    /**
     * The BlackLab index we want to access, e.g. "opensonar" for
     * "/opensonar/doc/1/content"
     */
    String indexName;

    /**
     * The type of REST resource we're accessing, e.g. "doc" for
     * "/opensonar/doc/1/content"
     */
    String urlResource;

    /**
     * The part of the URL path after the resource name, e.g. "1/content" for
     * "/opensonar/doc/1/content"
     */
    String urlPathInfo;

    /** The search manager, which executes and caches our searches */
    SearchManager searchMan;

    /** User id (if logged in) and/or session id */
    User user;

    protected IndexManager indexMan;

    private RequestInstrumentationProvider requestInstrumentation;

    private String requestId;

    RequestHandler(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource,
            String urlPathInfo) {
        this.servlet = servlet;
        this.request = request;
        searchMan = servlet.getSearchManager();
        indexMan = searchMan.getIndexManager();
        String pathAndQueryString = ServletUtil.getPathAndQueryString(request);

        if (!(this instanceof RequestHandlerStaticResponse) && !pathAndQueryString.startsWith("/cache-info")) { // annoying when monitoring
            logger.info(ServletUtil.shortenIpv6(request.getRemoteAddr()) + " " + user.uniqueIdShort() + " "
                    + request.getMethod() + " " + pathAndQueryString);
        }

        boolean isDocs = isDocsOperation();
        searchParam = servlet.getSearchParameters(isDocs, request, indexName);
        this.indexName = indexName;
        this.urlResource = urlResource;
        this.urlPathInfo = urlPathInfo;
        this.user = user;

    }

    protected void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public RequestInstrumentationProvider getInstrumentationProvider() {
        return instrumentationProvider;
    }

    public void setInstrumentationProvider(RequestInstrumentationProvider instrumentationProvider) {
        this.instrumentationProvider = instrumentationProvider;
    }

    public void cleanup() {
        try {
            if (searchLogger != null)
                searchLogger.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the response data type, if we want to override it.
     *
     * Used for returning doc contents, which are always XML, never JSON.
     *
     * @return the response data type to use, or null for the user requested one
     */
    public DataFormat getOverrideType() {
        return null;
    }

    /**
     * May the client cache the response of this operation?
     *
     * @return false if the client should not cache the response
     */
    public boolean isCacheAllowed() {
        return request.getMethod().equals("GET");
    }

    public boolean omitBlackLabResponseRootElement() {
        return false;
    }

    protected boolean isDocsOperation() {
        return false;
    }

    private void setDebug(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public void debug(Logger logger, String msg) {
        logger.debug(user.uniqueIdShort() + " " + msg);
    }

    public void warn(Logger logger, String msg) {
        logger.warn(user.uniqueIdShort() + " " + msg);
    }

    public void info(Logger logger, String msg) {
        logger.info(user.uniqueIdShort() + " " + msg);
    }

    public void error(Logger logger, String msg) {
        logger.error(user.uniqueIdShort() + " " + msg);
    }

    /**
     * Child classes should override this to handle the request.
     *
     * @param ds output stream
     * @return the response object
     *
     * @throws BlsException if the query can't be executed
     * @throws InterruptedSearch if the thread was interrupted
     */
    public abstract int handle(DataStream ds) throws BlsException, InvalidQuery;

    /**
     * Stream document information (metadata, contents authorization)
     *
     * @param ds where to stream information
     * @param index our index
     * @param document Lucene document
     * @param metadataFieldsToList fields to include in the document info
     */
    public void dataStreamDocumentInfo(DataStream ds, BlackLabIndex index, Document document, Set<MetadataField> metadataFieldsToList) {
        ds.startMap();
        for (MetadataField f: metadataFieldsToList) {
            if (f.name().equals("lengthInTokens") || f.name().equals("mayView")) {
                continue;
            }
            String[] values = document.getValues(f.name());
            if (values.length == 0) {
                continue;
            }

            ds.startEntry(f.name()).startList();
            for (String v : values) {
                ds.item("value", v);
            }
            ds.endList().endEntry();
        }

        int subtractClosingToken = 1;
        String tokenLengthField = index.mainAnnotatedField().tokenLengthField();

        if (tokenLengthField != null)
            ds.entry("lengthInTokens", Integer.parseInt(document.get(tokenLengthField)) - subtractClosingToken);
        ds.entry("mayView", mayView(index.metadata(), document))
                .endMap();

        dataStreamMetadataGroupInfo(ds,index);
    }

    protected static void dataStreamMetadataFieldDisplayNames(DataStream ds, IndexMetadata indexMetadata) {
        ds.startMap();
        for (MetadataField f: indexMetadata.metadataFields()) {
            String displayName = f.displayName();
            if (!f.name().equals("lengthInTokens") && !f.name().equals("mayView")) {
                ds.entry(f.name(),displayName);
            }
        }
        ds.endMap();
    }

    protected static void dataStreamMetadataGroupInfo(DataStream ds, BlackLabIndex index) {
        MetadataFieldGroups metaGroups = index.metadata().metadataFields().groups();
        synchronized (metaGroups) { // concurrent requests
            Set<MetadataField> metadataFieldsNotInGroups = new HashSet<>(index.metadata().metadataFields().stream().collect(Collectors.toSet()));
            for (MetadataFieldGroup metaGroup : metaGroups) {
                for (MetadataField field: metaGroup) {
                    metadataFieldsNotInGroups.remove(field);
                }
            }

            ds.startEntry("metadataFieldGroups").startList();
            boolean addedRemaining = false;
            for (MetadataFieldGroup metaGroup : metaGroups) {
                ds.startItem("metadataFieldGroup").startMap();
                ds.entry("name", metaGroup.name());
                ds.startEntry("fields").startList();
                for (MetadataField field: metaGroup) {
                    ds.item("field", field.name());
                }
                if (!addedRemaining && metaGroup.addRemainingFields()) {
                    addedRemaining = true;
                    List<MetadataField> rest = new ArrayList<>(metadataFieldsNotInGroups);
                    rest.sort( (a, b) -> a.name().toLowerCase().compareTo(b.name().toLowerCase()) );
                    for (MetadataField field: rest) {
                        ds.item("field", field.name());
                    }
                }
                ds.endList().endEntry();
                ds.endMap().endItem();
            }
            ds.endList().endEntry();
        }
    }

    /**
     * Returns the annotations to write out.
     *
     * By default, all annotations are returned.
     * Annotations are returned in requested order, or in their definition/display order.
     *
     * @return the annotations to write out, as specified by the (optional) "listvalues" query parameter.
     * @throws BlsException
     */
    public List<Annotation> getAnnotationsToWrite() throws BlsException {
        AnnotatedFields fields = this.blIndex().annotatedFields();
        Set<String> requestedAnnotations = searchParam.listValuesFor();

        List<Annotation> ret = new ArrayList<>();
        for (AnnotatedField f : fields) {
            for (Annotation a : f.annotations()) {
                if (requestedAnnotations.isEmpty() || requestedAnnotations.contains(a.name())) {
                    ret.add(a);
                }
            }
        }

        return ret;
    }

    /**
     * Returns a list of metadata fields to write out.
     *
     * By default, all metadata fields are returned.
     * Special fields (pidField, titleField, etc...) are always returned.
     *
     * @return a list of metadata fields to write out, as specified by the "listmetadatavalues" query parameter.
     * @throws BlsException
     */
    public Set<MetadataField> getMetadataToWrite() throws BlsException {
        MetadataFields fields = this.blIndex().metadataFields();
        Set<String> requestedFields = searchParam.listMetadataValuesFor();

        Set<MetadataField> ret = new HashSet<>();
        ret.add(fields.special(MetadataFields.AUTHOR));
        ret.add(fields.special(MetadataFields.DATE));
        ret.add(fields.special(MetadataFields.PID));
        ret.add(fields.special(MetadataFields.TITLE));
        for (MetadataField field  : fields) {
            if (requestedFields.isEmpty() || requestedFields.contains(field.name())) {
                ret.add(field);
            }
        }
        ret.remove(null); // for missing special fields.

        return ret;
    }

    /**
     * a document may be viewed when a contentViewable metadata field with a value
     * true is registered with either the document or with the index metadata.
     *
     * @param indexMetadata our index metadata
     * @param document document we want to view
     * @return true iff the content from documents in the index may be viewed
     */
    protected static boolean mayView(IndexMetadata indexMetadata, Document document) {
        if (indexMetadata.metadataFields().exists(METADATA_FIELD_CONTENT_VIEWABLE))
            return Boolean.parseBoolean(document.get(METADATA_FIELD_CONTENT_VIEWABLE));
        return indexMetadata.contentViewable();
    }

    protected void dataStreamFacets(DataStream ds, DocResults docsToFacet, SearchFacets facetDesc) throws InvalidQuery {

        Facets facets = facetDesc.execute();
        Map<DocProperty, DocGroups> counts = facets.countsPerFacet();

        ds.startMap();
        for (Entry<DocProperty, DocGroups> e : counts.entrySet()) {
            DocProperty facetBy = e.getKey();
            DocGroups facetCounts = e.getValue();
            facetCounts = facetCounts.sort(DocGroupProperty.size());
            ds.startAttrEntry("facet", "name", facetBy.name())
                    .startList();
            int n = 0, maxFacetValues = 10;
            int totalSize = 0;
            for (DocGroup count : facetCounts) {
                ds.startItem("item").startMap()
                        .entry("value", count.identity().toString())
                        .entry("size", count.size())
                        .endMap().endItem();
                totalSize += count.size();
                n++;
                if (n >= maxFacetValues)
                    break;
            }
            if (totalSize < facetCounts.sumOfGroupSizes()) {
                ds.startItem("item")
                        .startMap()
                        .entry("value", "[REST]")
                        .entry("size", facetCounts.sumOfGroupSizes() - totalSize)
                        .endMap()
                        .endItem();
            }
            ds.endList()
                    .endAttrEntry();
        }
        ds.endMap();
    }

    public static void dataStreamDocFields(DataStream ds, IndexMetadata indexMetadata) {
        ds.startMap();
        MetadataField pidField = indexMetadata.metadataFields().special(MetadataFields.PID);
        if (pidField != null)
            ds.entry("pidField", pidField.name());
        MetadataField titleField = indexMetadata.metadataFields().special(MetadataFields.TITLE);
        if (titleField != null)
            ds.entry("titleField", titleField.name());
        MetadataField authorField = indexMetadata.metadataFields().special(MetadataFields.AUTHOR);
        if (authorField != null)
            ds.entry("authorField", authorField.name());
        MetadataField dateField = indexMetadata.metadataFields().special(MetadataFields.DATE);
        if (dateField != null)
            ds.entry("dateField", dateField.name());
        ds.endMap();
    }

    protected BlackLabIndex blIndex() throws BlsException {
        return indexMan.getIndex(indexName).blIndex();
    }

    /**
     * Get the pid for the specified document
     *
     * @param index where we got this document from
     * @param luceneDocId Lucene document id
     * @param document the document object
     * @return the pid string (or Lucene doc id in string form if index has no pid
     *         field)
     */
    public static String getDocumentPid(BlackLabIndex index, int luceneDocId, Document document) {
        MetadataField pidField = index.metadataFields().special(MetadataFields.PID);
        String pid = pidField == null ? null : document.get(pidField.name());
        if (pid == null)
            return Integer.toString(luceneDocId);
        return pid;
    }

    /**
     * Output most of the fields of the search summary.
     *
     * @param ds where to output XML/JSON
     * @param searchParam original search parameters
     * @param searchTime time the search took
     * @param countTime time the count took
     * @param groups information about groups, if we were grouping
     * @param window our viewing window
     * @throws BlsException
     */
    protected <T> void addSummaryCommonFields(
            DataStream ds,
            SearchParameters searchParam,
            long searchTime,
            long countTime,
            ResultGroups<T> groups,
            WindowStats window
            ) throws BlsException {

        // Our search parameters
        ds.startEntry("searchParam");
        searchParam.dataStream(ds);
        ds.endEntry();

        IndexStatus status = indexMan.getIndex(searchParam.getIndexName()).getStatus();
        if (status != IndexStatus.AVAILABLE) {
            ds.entry("indexStatus", status.toString());
        }

        // Information about hit sampling
        SampleParameters sample = searchParam.getSampleSettings();
        if (sample != null) {
            ds.entry("sampleSeed", sample.seed());
            if (sample.isPercentage())
                ds.entry("samplePercentage", Math.round(sample.percentageOfHits() * 100 * 100) / 100.0);
            else
                ds.entry("sampleSize", sample.numberOfHitsSet());
        }

        // Information about search progress
        ds.entry("searchTime", searchTime);
        if (countTime != 0)
            ds.entry("countTime", countTime);

        // Information about grouping operation
        if (groups != null) {
            ds.entry("numberOfGroups", groups.size())
                    .entry("largestGroupSize", groups.largestGroupSize());
        }

        // Information about our viewing window
        if (window != null) {
            ds.entry("windowFirstResult", window.first())
                    .entry("requestedWindowSize", window.requestedWindowSize())
                    .entry("actualWindowSize", window.windowSize())
                    .entry("windowHasPrevious", window.hasPrevious())
                    .entry("windowHasNext", window.hasNext());
        }
    }

    protected void addNumberOfResultsSummaryTotalHits(DataStream ds, ResultsStats hitsStats, ResultsStats docsStats, boolean countFailed, CorpusSize subcorpusSize) {
        // Information about the number of hits/docs, and whether there were too many to retrieve/count
        // We have a hits object we can query for this information
        ds.entry("stillCounting", !hitsStats.done());
        ds.entry("numberOfHits", countFailed ? -1 : hitsStats.countedSoFar())
                .entry("numberOfHitsRetrieved", hitsStats.processedSoFar())
                .entry("stoppedCountingHits", hitsStats.maxStats().hitsCountedExceededMaximum())
                .entry("stoppedRetrievingHits", hitsStats.maxStats().hitsProcessedExceededMaximum());
        ds.entry("numberOfDocs", countFailed ? -1 : docsStats.countedSoFar())
                .entry("numberOfDocsRetrieved", docsStats.processedSoFar());
        if (subcorpusSize != null) {
            addSubcorpusSize(ds, subcorpusSize);
        }
    }

    static void addSubcorpusSize(DataStream ds, CorpusSize subcorpusSize) {
        ds.startEntry("subcorpusSize").startMap()
            .entry("documents", subcorpusSize.getDocuments());
        if (subcorpusSize.hasTokenCount())
            ds.entry("tokens", subcorpusSize.getTokens());
        ds.endMap().endEntry();
    }

    protected void addNumberOfResultsSummaryDocResults(DataStream ds, boolean isViewDocGroup, DocResults docResults, boolean countFailed, CorpusSize subcorpusSize) {
        // Information about the number of hits/docs, and whether there were too many to retrieve/count
        ds.entry("stillCounting", false);
        if (isViewDocGroup) {
            // Viewing single group of documents, possibly based on a hits search.
            // group.getResults().getOriginalHits() returns null in this case,
            // so we have to iterate over the DocResults and sum up the hits ourselves.
            int numberOfHits = 0;
            for (DocResult dr : docResults) {
                numberOfHits += dr.size();
            }
            ds.entry("numberOfHits", numberOfHits)
                    .entry("numberOfHitsRetrieved", numberOfHits);

            int numberOfDocsCounted = docResults.size();
            if (countFailed)
                numberOfDocsCounted = -1;
            ds.entry("numberOfDocs", numberOfDocsCounted)
                    .entry("numberOfDocsRetrieved", docResults.size());
        } else {
            // Documents-only search (no hits). Get the info from the DocResults.
            ds.entry("numberOfDocs", docResults.size())
                    .entry("numberOfDocsRetrieved", docResults.size());
        }
        if (subcorpusSize != null) {
            addSubcorpusSize(ds, subcorpusSize);
        }
    }

    public User getUser() {
        return user;
    }

    private static ArrayList<String> temp = new ArrayList<>();

    private static synchronized void writeRow(CSVPrinter printer, int numColumns, Object... values) {
        for (Object o : values)
            temp.add(o.toString());
        for (int i = temp.size(); i < numColumns; ++i)
            temp.add("");
        try {
            printer.printRecord(temp);
        } catch (IOException e) {
            throw new RuntimeException("Cannot write response");
        }
        temp.clear();
    }

    /**
     * Output most of the fields of the search summary.
     *
     * @param numColumns number of columns to output per row, minimum 2
     * @param printer the output printer
     * @param searchParam original search parameters
     * @param groups (optional) if results are grouped, the groups
     * @param subcorpusSize global sub corpus information (i.e. inter-group)
     */
    // TODO tidy up csv handling
    private static <T> void addSummaryCsvCommon(
        CSVPrinter printer,
        int numColumns,
        SearchParameters searchParam,
        ResultGroups<T> groups,
        CorpusSize subcorpusSize
    ) {
        for (Entry<String, String> param : searchParam.getParameters().entrySet()) {
            if (param.getKey().equals("listvalues") || param.getKey().equals("listmetadatavalues"))
                continue;
            writeRow(printer, numColumns, "summary.searchParam."+param.getKey(), param.getValue());
        }

        writeRow(printer, numColumns, "summary.subcorpusSize.documents", subcorpusSize.getDocuments());
        writeRow(printer, numColumns, "summary.subcorpusSize.tokens", subcorpusSize.getTokens());

        if (groups != null) {
            writeRow(printer, numColumns, "summary.numberOfGroups", groups.size());
            writeRow(printer, numColumns, "summary.largestGroupSize", groups.largestGroupSize());
        }

        SampleParameters sample = searchParam.getSampleSettings();
        if (sample != null) {
            writeRow(printer, numColumns, "summary.sampleSeed", sample.seed());
            if (sample.isPercentage())
                writeRow(printer, numColumns, "summary.samplePercentage", Math.round(sample.percentageOfHits() * 100 * 100) / 100.0);
            else
                writeRow(printer, numColumns, "summary.sampleSize", sample.numberOfHitsSet());
        }
    }

    /**
     *
     * @param printer
     * @param numColumns
     * @param hits
     * @param groups (optional) if grouped
     * @param subcorpusSize (optional) if available
     */
    protected void addSummaryCsvHits(CSVPrinter printer, int numColumns, Hits hits, ResultGroups<Hit> groups, CorpusSize subcorpusSize) {
        addSummaryCsvCommon(printer, numColumns, searchParam, groups, subcorpusSize);
        writeRow(printer, numColumns, "summary.numberOfHits", hits.size());
        writeRow(printer, numColumns, "summary.numberOfDocs", hits.docsStats().countedSoFar());
    }

    /**
     * @param printer
     * @param numColumns
     * @param docResults all docs as the input for groups, or contents of a specific group (viewgroup)
     * @param groups (optional) if grouped
     */
    protected void addSummaryCsvDocs(
            CSVPrinter printer,
            int numColumns,
            DocResults docResults,
            DocGroups groups,
            CorpusSize subcorpusSize
            ) {
        addSummaryCsvCommon(printer, numColumns, searchParam, groups, subcorpusSize);

        writeRow(printer, numColumns, "summary.numberOfDocs", docResults.size());
        writeRow(printer, numColumns, "summary.numberOfHits", docResults.stream().collect(Collectors.summingInt(r -> r.size())));
    }

    protected static BlsException translateSearchException(Exception e) {
        if (e instanceof InterruptedException) {
            throw new InterruptedSearch((InterruptedException) e);
        } else {
            try {
                throw e.getCause();
            } catch (BlackLabException e1) {
                return new BadRequest("INVALID_QUERY", "Invalid query: " + e1.getMessage());
            } catch (BlsException e1) {
                return e1;
            } catch (Throwable e1) {
                return new InternalServerError("Internal error while searching", "INTERR_WHILE_SEARCHING", e1);
            }
        }
    }

    public void writeHits(DataStream ds, Hits hits, Map<Integer, String> pids,
                                 ContextSettings contextSettings) throws BlsException {
        BlackLabIndex index = hits.index();

        Concordances concordances = null;
        Kwics kwics = null;
        if (contextSettings.concType() == ConcordanceType.CONTENT_STORE)
            concordances = hits.concordances(contextSettings.size(), ConcordanceType.CONTENT_STORE);
        else
            kwics = hits.kwics(contextSettings.size());

        ds.startEntry("hits").startList();
        Set<Annotation> annotationsToList = new HashSet<>(getAnnotationsToWrite());
        for (Hit hit : hits) {
            ds.startItem("hit").startMap();

            // Find pid
            String pid = pids.get(hit.doc());
            if (pid == null) {
                Document document = index.doc(hit.doc()).luceneDoc();
                pid = getDocumentPid(index, hit.doc(), document);
                pids.put(hit.doc(), pid);
            }

            // TODO: use RequestHandlerDocSnippet.getHitOrFragmentInfo()

            // Add basic hit info
            ds.entry("docPid", pid);
            ds.entry("start", hit.start());
            ds.entry("end", hit.end());

            if (hits.hasCapturedGroups()) {
                Map<String, Span> capturedGroups = hits.capturedGroups().getMap(hit);
                if (capturedGroups != null) {
                    ds.startEntry("captureGroups").startList();

                    for (Entry<String, Span> capturedGroup : capturedGroups.entrySet()) {
                        if (capturedGroup.getValue() != null) {
                            ds.startItem("group").startMap();
                            ds.entry("name", capturedGroup.getKey());
                            ds.entry("start", capturedGroup.getValue().start());
                            ds.entry("end", capturedGroup.getValue().end());
                            ds.endMap().endItem();
                        }
                    }

                    ds.endList().endEntry();
                } else {
                    logger.warn("MISSING CAPTURE GROUP: " + pid + ", query: " + searchParam.getString("patt"));
                }
            }

            ContextSize contextSize = searchParam.getContextSettings().size();
            boolean includeContext = contextSize.left() > 0 || contextSize.right() > 0;
            if (contextSettings.concType() == ConcordanceType.CONTENT_STORE) {
                // Add concordance from original XML
                Concordance c = concordances.get(hit);
                if (includeContext) {
                    ds.startEntry("left").plain(c.left()).endEntry()
                            .startEntry("match").plain(c.match()).endEntry()
                            .startEntry("right").plain(c.right()).endEntry();
                } else {
                    ds.startEntry("match").plain(c.match()).endEntry();
                }
            } else {
                // Add KWIC info
                Kwic c = kwics.get(hit);
                if (includeContext) {
                    ds.startEntry("left").contextList(c.annotations(), annotationsToList, c.left()).endEntry()
                            .startEntry("match").contextList(c.annotations(), annotationsToList, c.match()).endEntry()
                            .startEntry("right").contextList(c.annotations(), annotationsToList, c.right()).endEntry();
                } else {
                    ds.startEntry("match").contextList(c.annotations(), annotationsToList, c.match()).endEntry();
                }
            }
            ds.endMap().endItem();
        }
        ds.endList().endEntry();
    }
}
