package nl.inl.blacklab.server.requesthandlers;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;

import nl.inl.blacklab.resultproperty.DocGroupProperty;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.nint.MetadataField;
import nl.inl.blacklab.search.indexmetadata.nint.MetadataFields;
import nl.inl.blacklab.search.results.DocCount;
import nl.inl.blacklab.search.results.DocCounts;
import nl.inl.blacklab.search.results.DocOrHitGroups;
import nl.inl.blacklab.search.results.DocResult;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.HitsSample;
import nl.inl.blacklab.search.results.ResultsWindow;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.IndexNotFound;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.index.Index.IndexStatus;
import nl.inl.blacklab.server.index.IndexManager;
import nl.inl.blacklab.server.jobs.JobDescription;
import nl.inl.blacklab.server.jobs.JobFacets;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.util.ParseUtil;
import nl.inl.blacklab.server.util.ServletUtil;

/**
 * Base class for request handlers, to handle the different types of requests.
 * The static handle() method will dispatch the request to the appropriate
 * subclass.
 */
public abstract class RequestHandler {
    private static final String METADATA_FIELD_CONTENT_VIEWABLE = "contentViewable";

    static final Logger logger = LogManager.getLogger(RequestHandler.class);

    public static final int HTTP_OK = HttpServletResponse.SC_OK;

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
            DataFormat outputType) {

        // See if a user is logged in
        SearchManager searchManager = servlet.getSearchManager();
        User user = searchManager.getAuthSystem().determineCurrentUser(servlet, request);

        // Parse the URL
        String servletPath = StringUtils.strip(StringUtils.trimToEmpty(request.getServletPath()), "/");
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

        // If we're reading a private index, we must own it or be on the share list.
        // If we're modifying a private index, it must be our own.
        boolean isYourPrivateIndex = false;
        //logger.debug("Got indexName = \"" + indexName + "\" (len=" + indexName.length() + ")");
        if (indexName.contains(":")) {
            // It's a private index. Check if the logged-in user has access.
            if (!user.isLoggedIn())
                return errorObj.unauthorized("Log in to access a private index.");
            try {
                Index index = searchManager.getIndexManager().getIndex(indexName);
                if (!index.userMayRead(user.getUserId()))
                    return errorObj.unauthorized("You are not authorized to access this index.");
                isYourPrivateIndex = user.getUserId().equals(index.getUserId());
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
                if (!isYourPrivateIndex)
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
                    if (!isYourPrivateIndex)
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
                            if (request.getParameter("group") != null) {
                                String viewgroup = request.getParameter("viewgroup");
                                if (viewgroup == null || viewgroup.length() == 0)
                                    handlerName += "-grouped"; // list of groups instead of contents
                            } else if (request.getParameter("viewgroup") != null) {
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
                    } catch (NoSuchMethodException e) {
                        // (can only happen if the required constructor is not available in the RequestHandler subclass)
                        logger.error("Could not get constructor to create request handler", e);
                        return errorObj.internalError(e, debugMode, 2);
                    } catch (IllegalArgumentException e) {
                        logger.error("Could not create request handler", e);
                        return errorObj.internalError(e, debugMode, 3);
                    } catch (InstantiationException e) {
                        logger.error("Could not create request handler", e);
                        return errorObj.internalError(e, debugMode, 4);
                    } catch (IllegalAccessException e) {
                        logger.error("Could not create request handler", e);
                        return errorObj.internalError(e, debugMode, 5);
                    } catch (InvocationTargetException e) {
                        logger.error("Could not create request handler", e);
                        return errorObj.internalError(e, debugMode, 6);
                    }
                }
            }
            if (requestHandler == null) {
                return errorObj.internalError("RequestHandler.create called with wrong method: " + method, debugMode,
                        10);
            }
        }
        if (debugMode)
            requestHandler.setDebug(debugMode);
        return requestHandler;
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
     * @throws InterruptedException if the thread was interrupted
     */
    public abstract int handle(DataStream ds) throws BlsException, InterruptedException;

    /**
     * Stream document information (metadata, contents authorization)
     *
     * @param ds where to stream information
     * @param searcher our index
     * @param document Lucene document
     */
    public void dataStreamDocumentInfo(DataStream ds, Searcher searcher, Document document) {
        ds.startMap();
        IndexMetadata indexMetadata = searcher.getIndexMetadata();
        for (MetadataField f: indexMetadata.metadataFields()) {
            String value = document.get(f.name());
            if (value != null && !value.equals("lengthInTokens") && !value.equals("mayView"))
                ds.entry(f.name(), value);
        }
        int subtractFromLength = indexMetadata.alwaysHasClosingToken() ? 1 : 0;
        String tokenLengthField = indexMetadata.getMainContentsField().tokenLengthField();

        if (tokenLengthField != null)
            ds.entry("lengthInTokens", Integer.parseInt(document.get(tokenLengthField)) - subtractFromLength);
        ds.entry("mayView", mayView(indexMetadata, document))
                .endMap();
    }

    /**
     * a document may be viewed when a contentViewable metadata field with a value
     * true is registered with either the document or with the index metadata.
     * 
     * @param indexMetadata our index metadata
     * @param document document we want to view
     * @return true iff the content from documents in the index may be viewed
     */
    protected boolean mayView(IndexMetadata indexMetadata, Document document) {
        if (indexMetadata.metadataFields().exists(METADATA_FIELD_CONTENT_VIEWABLE))
            return Boolean.parseBoolean(document.get(METADATA_FIELD_CONTENT_VIEWABLE));
        return indexMetadata.contentViewable();
    }

    protected void dataStreamFacets(DataStream ds, DocResults docsToFacet, JobDescription facetDesc)
            throws BlsException {

        JobFacets facets = (JobFacets) searchMan.search(user, facetDesc, true);
        Map<String, DocCounts> counts = facets.getCounts();

        ds.startMap();
        for (Entry<String, DocCounts> e : counts.entrySet()) {
            String facetBy = e.getKey();
            DocCounts facetCounts = e.getValue();
            facetCounts.sort(DocGroupProperty.size());
            ds.startAttrEntry("facet", "name", facetBy)
                    .startList();
            int n = 0, maxFacetValues = 10;
            int totalSize = 0;
            for (DocCount count : facetCounts) {
                ds.startItem("item").startMap()
                        .entry("value", count.getIdentity().toString())
                        .entry("size", count.size())
                        .endMap().endItem();
                totalSize += count.size();
                n++;
                if (n >= maxFacetValues)
                    break;
            }
            if (totalSize < facetCounts.getTotalResults()) {
                ds.startItem("item")
                        .startMap()
                        .entry("value", "[REST]")
                        .entry("size", facetCounts.getTotalResults() - totalSize)
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

    protected Searcher getSearcher() throws BlsException {
        return indexMan.getIndex(indexName).getSearcher();
    }

    protected boolean isBlockingOperation() {
        String str = ServletUtil.getParameter(request, "block", "yes").toLowerCase();
        try {
            return ParseUtil.strToBool(str);
        } catch (IllegalArgumentException e) {
            debug(logger, "Illegal boolean value for parameter '" + "block" + "': " + str);
            return false;
        }
    }

    /**
     * Get the pid for the specified document
     *
     * @param searcher where we got this document from
     * @param luceneDocId Lucene document id
     * @param document the document object
     * @return the pid string (or Lucene doc id in string form if index has no pid
     *         field)
     */
    public static String getDocumentPid(Searcher searcher, int luceneDocId,
            Document document) {
        MetadataField pidField = searcher.getIndexMetadata().metadataFields().special(MetadataFields.PID); //getIndexParam(indexName, user).getPidField();
        if (pidField == null)
            return Integer.toString(luceneDocId);
        return document.get(pidField.name());
    }

    /**
     * Output most of the fields of the search summary.
     *
     * @param ds where to output XML/JSON
     * @param searchParam original search parameters
     * @param searchTime time the search took
     * @param countTime time the count took
     * @param hits hits found (may be null for certain searches)
     * @param totalHits hits instance used for calculating total (unfortunately, may
     *            be different instance than hits because of how cache works now -
     *            should be improved)
     * @param isViewDocGroup are we viewing single document group?
     * @param docResults document results, if this is a document search
     * @param groups information about groups, if we were grouping
     * @param window our viewing window
     * @throws BlsException
     */
    protected void addSummaryCommonFields(DataStream ds, SearchParameters searchParam, double searchTime,
            double countTime,
            Hits hits, Hits totalHits, boolean isViewDocGroup, DocResults docResults, DocOrHitGroups groups,
            ResultsWindow window) throws BlsException {

        if (hits == null && docResults != null) {
            hits = docResults.getOriginalHits();
            totalHits = hits;
        }

        // Our search parameters
        ds.startEntry("searchParam");
        searchParam.dataStream(ds);
        ds.endEntry();

        IndexStatus status = indexMan.getIndex(searchParam.getIndexName()).getStatus();
        if (status != IndexStatus.AVAILABLE) {
            ds.entry("indexStatus", status.toString());
        }

        // Information about search progress
        ds.entry("searchTime", (int) (searchTime * 1000));
        boolean countFailed = countTime < 0;
        if (countTime != 0)
            ds.entry("countTime", (int) (countTime * 1000));
        ds.entry("stillCounting", totalHits == null ? false : !totalHits.doneFetchingHits());

        // Information about the number of hits/docs, and whether there were too many to retrieve/count
        if (totalHits != null) {
            // We have a hits object we can query for this information
            ds.entry("numberOfHits", countFailed ? -1 : totalHits.countSoFarHitsCounted())
                    .entry("numberOfHitsRetrieved", totalHits.countSoFarHitsRetrieved())
                    .entry("stoppedCountingHits", totalHits.maxHitsCounted())
                    .entry("stoppedRetrievingHits", totalHits.maxHitsRetrieved());
            ds.entry("numberOfDocs", countFailed ? -1 : totalHits.countSoFarDocsCounted())
                    .entry("numberOfDocsRetrieved", totalHits.countSoFarDocsRetrieved());
        } else if (isViewDocGroup) {
            // Viewing single group of documents, possibly based on a hits search.
            // group.getResults().getOriginalHits() returns null in this case,
            // so we have to iterate over the DocResults and sum up the hits ourselves.
            int numberOfHits = 0;
            for (DocResult dr : docResults) {
                numberOfHits += dr.getNumberOfHits();
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
            ds.entry("numberOfDocs", docResults.countSoFarDocsCounted())
                    .entry("numberOfDocsRetrieved", docResults.countSoFarDocsRetrieved());
        }

        // Information about grouping operation
        if (groups != null) {
            ds.entry("numberOfGroups", groups.numberOfGroups())
                    .entry("largestGroupSize", groups.getLargestGroupSize());
        }

        // Information about our viewing window
        if (window != null) {
            ds.entry("windowFirstResult", window.first())
                    .entry("requestedWindowSize", window.requestedWindowSize())
                    .entry("actualWindowSize", window.size())
                    .entry("windowHasPrevious", window.hasPrevious())
                    .entry("windowHasNext", window.hasNext());
        }

        // Information about hit sampling
        if (hits instanceof HitsSample) {
            HitsSample sample = ((HitsSample) hits);
            ds.entry("sampleSeed", sample.seed());
            if (sample.exactNumberGiven())
                ds.entry("sampleSize", sample.numberOfHitsToSelect());
            else
                ds.entry("samplePercentage", Math.round(sample.ratio() * 100 * 100) / 100.0);
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
     * @param format csv fomat/printer to write output to, note that the format must
     *            contain at least 2 columns.
     * @param printer
     * @param searchParam original search parameters
     */
    // TODO tidy up csv handling
    protected void addSummaryCommonFieldsCSV(CSVFormat format, CSVPrinter printer, SearchParameters searchParam) {
        final int numColumns = format.getHeader().length;
        if (numColumns < 2)
            throw new IllegalArgumentException("Csv must contain at least 2 columns");

        // Our search parameters
        writeRow(printer, numColumns, "searchParam");
        for (Entry<String, String> e : searchParam.getParameters().entrySet()) {
            // ugly -- mimic normal summaryCommonFields
            if ("samplenum".equals(e.getKey()))
                writeRow(printer, numColumns, "sampleSize", e.getValue());
            else if ("sample".equals(e.getKey()))
                writeRow(printer, numColumns, "samplePercentage", e.getValue());
            else
                writeRow(printer, numColumns, e.getKey(), e.getValue());
        }
        writeRow(printer, numColumns, "");
    }
}
