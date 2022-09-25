package nl.inl.blacklab.server.requesthandlers;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.lucene.document.Document;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.instrumentation.RequestInstrumentationProvider;
import nl.inl.blacklab.resultproperty.DocGroupProperty;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexAbstract;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFields;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFieldGroup;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.DocGroup;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.DocResult;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Facets;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.ResultGroups;
import nl.inl.blacklab.search.results.ResultsStats;
import nl.inl.blacklab.search.results.ResultsStatsStatic;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.search.results.WindowStats;
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
import nl.inl.blacklab.server.lib.ConcordanceContext;
import nl.inl.blacklab.server.lib.IndexUtil;
import nl.inl.blacklab.server.lib.ResultMetadataGroupInfo;
import nl.inl.blacklab.server.lib.SearchCreator;
import nl.inl.blacklab.server.lib.SearchTimings;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.util.WebserviceUtil;

/**
 * Base class for request handlers, to handle the different types of requests.
 * The static handle() method will dispatch the request to the appropriate
 * subclass.
 */
public abstract class RequestHandler {
    static final Logger logger = LogManager.getLogger(RequestHandler.class);

    public static final int HTTP_OK = HttpServletResponse.SC_OK;

    protected RequestInstrumentationProvider instrumentationProvider;

    /** The available request handlers by name */
    static final Map<String, Class<? extends RequestHandler>> availableHandlers;

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
            String xBlackLabAccessToken = request.getHeader("X-BlackLabAccessToken");
            if (xBlackLabAccessToken != null && xBlackLabAccessToken.equals(debugHttpHeaderToken)) {
                user = User.loggedIn(request.getHeader("X-BlackLabUserId"), request.getSession().getId());
            }
        }

        // Parse the URL
        String servletPath = StringUtils.strip(StringUtils.trimToEmpty(request.getPathInfo()), "/");
        String[] parts = servletPath.split("/", 3);
        String indexName = parts.length >= 1 ? parts[0] : "";
        RequestHandlerStaticResponse errorObj = new RequestHandlerStaticResponse(servlet, request, user, indexName
        );
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
        if (IndexUtil.isUserIndex(indexName)) {
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
                                .unauthorized("You (" + ServletUtil.getOriginatingAddress(request) + ") are not authorized to do this.");
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
                                "You (" + ServletUtil.getOriginatingAddress(request) + ") are not authorized to see this information.");
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

                        Class<? extends RequestHandler> handlerClass = availableHandlers.get(handlerName);
                        Constructor<? extends RequestHandler> ctor = handlerClass.getConstructor(BlackLabServer.class,
                                HttpServletRequest.class, User.class, String.class, String.class, String.class);
                        //servlet.getSearchManager().getSearcher(indexName); // make sure it's open
                        requestHandler = ctor.newInstance(servlet, request, user, indexName, urlResource, urlPathInfo);
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
            requestHandler.setDebug();

        requestHandler.setInstrumentationProvider(instrumentationProvider);
        requestHandler.setRequestId(requestId);

        return requestHandler;
    }

    private static boolean doDebugSleep(HttpServletRequest request) {
        String sleep = request.getParameter("sleep");
        if (sleep != null) {
            int sleepMs = Integer.parseInt(sleep);
            if (sleepMs > 0 && sleepMs <= 3600000) {
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

    protected boolean debugMode;

    /** The servlet object */
    protected BlackLabServer servlet;

    /** The HTTP request object */
    protected HttpServletRequest request;

    /** Interprets parameters to create searches. */
    protected SearchCreator params;

    /**
     * The BlackLab index we want to access, e.g. "opensonar" for
     * "/opensonar/doc/1/content"
     */
    protected String indexName;

    /**
     * The type of REST resource we're accessing, e.g. "doc" for
     * "/opensonar/doc/1/content"
     */
    protected String urlResource;

    /**
     * The part of the URL path after the resource name, e.g. "1/content" for
     * "/opensonar/doc/1/content"
     */
    protected String urlPathInfo;

    /** The search manager, which executes and caches our searches */
    protected SearchManager searchMan;

    /** User id (if logged in) and/or session id */
    protected User user;

    protected IndexManager indexMan;

    @SuppressWarnings("unused")
    private RequestInstrumentationProvider requestInstrumentation;

    RequestHandler(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource,
            String urlPathInfo) {
        this.servlet = servlet;
        this.request = request;
        searchMan = servlet.getSearchManager();
        indexMan = searchMan.getIndexManager();
        String pathAndQueryString = ServletUtil.getPathAndQueryString(request);

        if (!(this instanceof RequestHandlerStaticResponse) && !pathAndQueryString.startsWith("/cache-info")) { // annoying when monitoring
            logger.info(WebserviceUtil.shortenIpv6(ServletUtil.getOriginatingAddress(request)) + " " + user.uniqueIdShort() + " "
                    + request.getMethod() + " " + pathAndQueryString);
        }

        boolean isDocs = isDocsOperation();
        boolean isDebugMode = searchMan.isDebugMode(ServletUtil.getOriginatingAddress(request));
        BlackLabServerParams blsParams = new BlackLabServerParams(indexName, request);
        params = SearchCreator.get(searchMan, isDocs, isDebugMode, blsParams);
        this.indexName = indexName;
        this.urlResource = urlResource;
        this.urlPathInfo = urlPathInfo;
        this.user = user;

    }

    /**
     * Add info about the current logged-in user (if any) to the response.
     *
     * @param ds output stream
     * @param loggedIn is user logged in?
     * @param userId user id (if logged in)
     * @param canCreateIndex is the user allowed to create another index?
     */
    static void datastreamUserInfo(DataStream ds, boolean loggedIn, String userId, boolean canCreateIndex) {
        ds.startEntry("user").startMap();
        ds.entry("loggedIn", loggedIn);
        if (loggedIn)
            ds.entry("id", userId);
        ds.entry("canCreateIndex", loggedIn && canCreateIndex);
        ds.endMap().endEntry();
    }

    /**
     * Add info about metadata fields to hits and docs results.
     *
     * Note that this information can be retrieved using different requests,
     * and it is redundant to send it with every query response. We may want
     * to deprecate this in the future.
     *
     * @param ds output stream
     * @param index our index
     */
    static void datastreamMetadataFieldInfo(DataStream ds, BlackLabIndex index) {
        ds.startEntry("docFields");
        dataStreamDocFields(ds, index.metadata());
        ds.endEntry();

        ds.startEntry("metadataFieldDisplayNames");
        dataStreamMetadataFieldDisplayNames(ds, index.metadata());
        ds.endEntry();
    }

    public static void dataStream(SearchCreator searchParameters, DataStream ds) {
        ds.startMap();
        for (Entry<String, String> e : searchParameters.getParameters().entrySet()) {
            ds.entry(e.getKey(), e.getValue());
        }
        ds.endMap();
    }

    protected void setRequestId(String requestId) {
    }

    @SuppressWarnings("unused")
    public RequestInstrumentationProvider getInstrumentationProvider() {
        return instrumentationProvider;
    }

    public void setInstrumentationProvider(RequestInstrumentationProvider instrumentationProvider) {
        this.instrumentationProvider = instrumentationProvider;
    }

    public void cleanup() {
        // (no op)
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

    private void setDebug() {
        this.debugMode = true;
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
     * @param luceneDocs Lucene documents to stream
     * @param metadataFieldsToList fields to include in the document info
     */
    static void datastreamDocInfos(DataStream ds, BlackLabIndex index, Map<Integer, Document> luceneDocs, Set<MetadataField> metadataFieldsToList) {
        ds.startEntry("docInfos").startMap();
        for (Entry<Integer, Document> e: luceneDocs.entrySet()) {
            Integer docId = e.getKey();
            Document luceneDoc = e.getValue();
            String pid = getDocumentPid(index, docId, luceneDoc);
            ds.startAttrEntry("docInfo", "pid", pid);
            dataStreamDocumentInfo(ds, index, luceneDoc, metadataFieldsToList);
            ds.endAttrEntry();
        }
        ds.endMap().endEntry();
    }

    /**
     * Stream document information (metadata, contents authorization)
     *
     * @param ds where to stream information
     * @param index our index
     * @param document Lucene document
     * @param metadataFieldsToList fields to include in the document info
     */
    static void dataStreamDocumentInfo(DataStream ds, BlackLabIndex index, Document document, Set<MetadataField> metadataFieldsToList) {
        Map<String, List<String>> metadata = new LinkedHashMap<>();
        for (MetadataField f: metadataFieldsToList) {
            if (f.name().equals("lengthInTokens") || f.name().equals("mayView"))
                continue;
            String[] values = document.getValues(f.name());
            if (values.length == 0)
                continue;
            metadata.put(f.name(), List.of(values));
        }
        String tokenLengthField = index.mainAnnotatedField().tokenLengthField();
        Integer lengthInTokens = null;
        if (tokenLengthField != null) {
            lengthInTokens =
                    Integer.parseInt(document.get(tokenLengthField)) - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
        }
        boolean mayView = index.mayView(document);

        dataStreamDocumentInfo(ds, metadata, lengthInTokens, mayView);
    }

    public static void dataStreamDocumentInfo(DataStream ds, nl.inl.blacklab.server.lib.ResultDocInfo docInfo) {
        dataStreamDocumentInfo(ds, docInfo.getMetadata(), docInfo.getLengthInTokens(), docInfo.isMayView());
    }

    public static void dataStreamDocumentInfo(DataStream ds, Map<String, List<String>> metadata, Integer lengthInTokens,
            boolean mayView) {
        ds.startMap();
        {
            for (Entry<String, List<String>> e: metadata.entrySet()) {
                ds.startEntry(e.getKey()).startList();
                {
                    for (String v: e.getValue()) {
                        ds.item("value", v);
                    }
                }
                ds.endList().endEntry();
            }
            if (lengthInTokens != null)
                ds.entry("lengthInTokens", lengthInTokens);
            ds.entry("mayView", mayView);
        }
        ds.endMap();
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

    public static void dataStreamMetadataGroupInfo(DataStream ds, ResultMetadataGroupInfo info) {
        ds.startEntry("metadataFieldGroups").startList();
        boolean addedRemaining = false;
        for (MetadataFieldGroup metaGroup : info.getMetaGroups().values()) {
            ds.startItem("metadataFieldGroup").startMap();
            ds.entry("name", metaGroup.name());
            ds.startEntry("fields").startList();
            for (String field: metaGroup) {
                ds.item("field", field);
            }
            if (!addedRemaining && metaGroup.addRemainingFields()) {
                addedRemaining = true;
                List<MetadataField> rest = new ArrayList<>(info.getMetadataFieldsNotInGroups());
                rest.sort(Comparator.comparing(a -> a.name().toLowerCase()));
                for (MetadataField field: rest) {
                    ds.item("field", field.name());
                }
            }
            ds.endList().endEntry();
            ds.endMap().endItem();
        }
        ds.endList().endEntry();
    }

    /**
     * Returns the annotations to write out.
     *
     * By default, all annotations are returned.
     * Annotations are returned in requested order, or in their definition/display order.
     *
     * @return the annotations to write out, as specified by the (optional) "listvalues" query parameter.
     */
    public List<Annotation> getAnnotationsToWrite() throws BlsException {
        AnnotatedFields fields = this.blIndex().annotatedFields();
        Set<String> requestedAnnotations = params.getListValuesFor();

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

    protected void dataStreamFacets(DataStream ds, SearchFacets facetDesc) throws InvalidQuery {

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
        MetadataField pidField = indexMetadata.metadataFields().pidField();
        if (pidField != null)
            ds.entry("pidField", pidField.name());
        for (String propName: List.of("titleField", "authorField", "dateField")) {
            String fieldName = indexMetadata.custom().get(propName, "");
            if (!fieldName.isEmpty())
                ds.entry(propName, fieldName);
        }
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
        MetadataField pidField = index.metadataFields().pidField();
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
     * @param timings various timings related to this request
     * @param groups information about groups, if we were grouping
     * @param window our viewing window
     */
    protected <T> void datastreamSummaryCommonFields(
            DataStream ds,
            SearchCreator searchParam,
            SearchTimings timings,
            ResultGroups<T> groups,
            WindowStats window
            ) throws BlsException {

        // Our search parameters
        ds.startEntry("searchParam");
        dataStream(searchParam, ds);
        ds.endEntry();

        IndexStatus status = indexMan.getIndex(searchParam.getIndexName()).getStatus();
        if (status != IndexStatus.AVAILABLE) {
            ds.entry("indexStatus", status.toString());
        }

        // Information about hit sampling
        SampleParameters sample = searchParam.sampleSettings();
        if (sample != null) {
            ds.entry("sampleSeed", sample.seed());
            if (sample.isPercentage())
                ds.entry("samplePercentage", Math.round(sample.percentageOfHits() * 100 * 100) / 100.0);
            else
                ds.entry("sampleSize", sample.numberOfHitsSet());
        }

        // Information about search progress
        ds.entry("searchTime", timings.getProcessingTime());
        ds.entry("countTime", timings.getCountTime());

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

    protected void datastreamNumberOfResultsSummaryTotalHits(DataStream ds, ResultsStats hitsStats, ResultsStats docsStats, boolean waitForTotal, boolean countFailed, CorpusSize subcorpusSize) {
        // Information about the number of hits/docs, and whether there were too many to retrieve/count
        // We have a hits object we can query for this information

        if (hitsStats == null)
            hitsStats = ResultsStatsStatic.INVALID;
        long hitsCounted = countFailed ? -1 : (waitForTotal ? hitsStats.countedTotal() : hitsStats.countedSoFar());
        long hitsProcessed = waitForTotal ? hitsStats.processedTotal() : hitsStats.processedSoFar();
        if (docsStats == null)
            docsStats = ResultsStatsStatic.INVALID;
        long docsCounted = countFailed ? -1 : (waitForTotal ? docsStats.countedTotal() : docsStats.countedSoFar());
        long docsProcessed = waitForTotal ? docsStats.processedTotal() : docsStats.processedSoFar();

        ds.entry("stillCounting", !hitsStats.done());
        ds.entry("numberOfHits", hitsCounted)
                .entry("numberOfHitsRetrieved", hitsProcessed)
                .entry("stoppedCountingHits", hitsStats.maxStats().hitsCountedExceededMaximum())
                .entry("stoppedRetrievingHits", hitsStats.maxStats().hitsProcessedExceededMaximum());
        ds.entry("numberOfDocs", docsCounted)
                .entry("numberOfDocsRetrieved", docsProcessed);
        if (subcorpusSize != null) {
            datastreamSubcorpusSize(ds, subcorpusSize);
        }
    }

    static void datastreamSubcorpusSize(DataStream ds, CorpusSize subcorpusSize) {
        ds.startEntry("subcorpusSize").startMap()
            .entry("documents", subcorpusSize.getDocuments());
        if (subcorpusSize.hasTokenCount())
            ds.entry("tokens", subcorpusSize.getTokens());
        ds.endMap().endEntry();
    }

    protected void datastreamNumberOfResultsSummaryDocResults(DataStream ds, boolean isViewDocGroup, DocResults docResults, boolean countFailed, CorpusSize subcorpusSize) {
        // Information about the number of hits/docs, and whether there were too many to retrieve/count
        ds.entry("stillCounting", false);
        if (isViewDocGroup) {
            // Viewing single group of documents, possibly based on a hits search.
            // group.getResults().getOriginalHits() returns null in this case,
            // so we have to iterate over the DocResults and sum up the hits ourselves.
            long numberOfHits = 0;
            for (DocResult dr : docResults) {
                numberOfHits += dr.size();
            }
            ds.entry("numberOfHits", numberOfHits)
                    .entry("numberOfHitsRetrieved", numberOfHits);

            long numberOfDocsCounted = docResults.size();
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
            datastreamSubcorpusSize(ds, subcorpusSize);
        }
    }

    public User getUser() {
        return user;
    }

    protected static BlsException translateSearchException(Exception e) {
        if (e instanceof InterruptedException) {
            throw new InterruptedSearch(e);
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

    public void datastreamHits(DataStream ds, Hits hits, ConcordanceContext concordanceContext, Map<Integer, Document> luceneDocs) throws BlsException {
        BlackLabIndex index = hits.index();

        ds.startEntry("hits").startList();
        Set<Annotation> annotationsToList = new HashSet<>(getAnnotationsToWrite());
        for (Hit hit : hits) {
            ds.startItem("hit").startMap();

            // Collect Lucene docs (for writing docInfos later) and find pid
            Document document = luceneDocs.get(hit.doc());
            if (document == null) {
                document = index.luceneDoc(hit.doc());
                luceneDocs.put(hit.doc(), document);
            }
            String pid = getDocumentPid(index, hit.doc(), document);

            // TODO: use RequestHandlerDocSnippet.getHitOrFragmentInfo()

            // Add basic hit info
            ds.entry("docPid", pid);
            ds.entry("start", hit.start());
            ds.entry("end", hit.end());

            if (hits.hasCapturedGroups()) {
                Map<String, Span> capturedGroups = hits.capturedGroups().getMap(hit, params.omitEmptyCapture());
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
                    logger.warn("MISSING CAPTURE GROUP: " + pid + ", query: " + params.getPattern());
                }
            }

            ContextSize contextSize = params.contextSettings().size();
            boolean includeContext = contextSize.left() > 0 || contextSize.right() > 0;
            if (concordanceContext.isConcordances()) {
                // Add concordance from original XML
                Concordance c = concordanceContext.getConcordance(hit);
                if (includeContext) {
                    ds.startEntry("left").xmlFragment(c.left()).endEntry()
                            .startEntry("match").xmlFragment(c.match()).endEntry()
                            .startEntry("right").xmlFragment(c.right()).endEntry();
                } else {
                    ds.startEntry("match").xmlFragment(c.match()).endEntry();
                }
            } else {
                // Add KWIC info
                Kwic c = concordanceContext.getKwic(hit);
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
