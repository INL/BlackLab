package nl.inl.blacklab.server;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import nl.inl.blacklab.instrumentation.MetricsProvider;
import nl.inl.blacklab.instrumentation.RequestInstrumentationProvider;
import nl.inl.blacklab.instrumentation.impl.PrometheusMetricsProvider;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.ConfigurationException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.requesthandlers.RequestHandler;
import nl.inl.blacklab.server.requesthandlers.Response;
import nl.inl.blacklab.server.requesthandlers.SearchParameters;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.util.ServletUtil;

public class BlackLabServer extends HttpServlet {

    /**
     * Root element to use for XML responses.
     */
    public static final String BLACKLAB_RESPONSE_ROOT_ELEMENT = "blacklabResponse";

    private static final Logger logger = LogManager.getLogger(BlackLabServer.class);

    static final Charset CONFIG_ENCODING = Charset.forName("utf-8");

    static final Charset OUTPUT_ENCODING = Charset.forName("utf-8");

    /** Manages all our searches */
    private SearchManager searchManager;

    private boolean configRead = false;

    private RequestInstrumentationProvider requestInstrumentationProvider = null;

    @Override
    public void init() throws ServletException {
        // Default init if no log4j.properties found
        //LogUtil.initLog4jIfNotAlready(Level.DEBUG);

        logger.info("Starting BlackLab Server...");
        super.init();
        logger.info("BlackLab Server ready.");
    }

    @SuppressWarnings("deprecation")
    private void readConfig() throws BlsException {
        try {

            File servletPath = new File(getServletContext().getRealPath("."));
            logger.debug("Running from dir: " + servletPath);

            String configFileName = "blacklab-server";

            List<File> searchDirs = new ArrayList<>();
            searchDirs.add(servletPath.getAbsoluteFile().getParentFile().getCanonicalFile());
            searchDirs.addAll(BlackLab.defaultConfigDirs());
            ConfigFileReader configFile = new ConfigFileReader(searchDirs, configFileName);
            BLSConfig config = configFile.getConfig();
            // load blacklab's internal config before doing anything
            // It's important we do this as early as possible as some things are loaded depending on the config (such as plugins)
            BlackLab.setConfig(config.getBLConfig());

            if (config.getProtocol().isUseOldElementNames())
                logger.warn("IMPORTANT: Found deprecated setting useOldElementNames. This setting doesn't do anything anymore and will eventually be removed.");
            searchManager = new SearchManager(config);

            // Set default parameter settings from config
            SearchParameters.setDefaults(config.getParameters());
            setMetricsProvider(config);
            this.requestInstrumentationProvider = getRequestInstrumentationProvider(config);

        } catch (JsonProcessingException e) {
            throw new ConfigurationException("Invalid JSON in configuration file", e);
        } catch (IOException e) {
            throw new ConfigurationException("Error reading configuration file", e);
        }
    }

    private void setMetricsProvider(BLSConfig config) throws ConfigurationException {
        String registryProviderClassName = config.getDebug().getMetricsProvider();
        if (StringUtils.isBlank(registryProviderClassName)) {
            return;
        }

        String fqClassName = registryProviderClassName.startsWith("nl.inl.blacklab.instrumentation")
            ? registryProviderClassName
            : String.format("nl.inl.blacklab.instrumentation.impl.%s", registryProviderClassName);

        try {
            MetricsProvider meterRegistryProvider = (MetricsProvider)
                Class.forName(fqClassName).getDeclaredConstructor().newInstance();
            MeterRegistry registry = meterRegistryProvider.getRegistry();
            Metrics.addRegistry(registry);
        } catch (Exception ex) {
            throw new ConfigurationException("Can not create metrics provider with class" + fqClassName);
        }
    }
    private RequestInstrumentationProvider getRequestInstrumentationProvider(BLSConfig config) throws ConfigurationException {
        if (requestInstrumentationProvider != null) {
            return requestInstrumentationProvider;
        }

        String provider = config.getDebug().getRequestInstrumentationProvider();
        if ( StringUtils.isBlank(provider)) {
            return RequestInstrumentationProvider.noOpProvider();
        }

        String fqClassName = provider.startsWith("nl.inl.blacklab.instrumentation")
            ? provider
            : String.format("nl.inl.blacklab.instrumentation.impl.%s", provider);

        try {
            RequestInstrumentationProvider instrumentationProvider = (RequestInstrumentationProvider)
                Class.forName(fqClassName).getDeclaredConstructor().newInstance();
            return instrumentationProvider;

        } catch (Exception ex) {
            throw new ConfigurationException("Can not create request instrumentation provider with class" + fqClassName);
        }
    }


    /**
     * Process POST requests (add data to index)
     *
     * @throws ServletException
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse responseObject) throws ServletException {
        handleRequest(request, responseObject);
    }

    /**
     * Process PUT requests (create index)
     *
     * @throws ServletException
     */
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse responseObject) throws ServletException {
        handleRequest(request, responseObject);
    }

    /**
     * Process DELETE requests (create a index, add data to one)
     *
     * @throws ServletException
     */
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse responseObject) throws ServletException {
        handleRequest(request, responseObject);
    }

    /**
     * Process GET requests (information retrieval)
     *
     * @param request HTTP request object
     * @param responseObject where to write our response
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse responseObject) {
        handleRequest(request, responseObject);
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doOptions(req, resp);
        String allowOrigin = searchManager == null ? "*" : searchManager.config().getProtocol().getAccessControlAllowOrigin();
        if (allowOrigin != null) {
        	resp.addHeader("Access-Control-Allow-Origin", allowOrigin);
        	resp.addHeader("Access-Control-Allow-Headers", req.getHeader("Access-Control-Request-Headers"));
        	resp.addHeader("Access-Control-Allow-Methods", "GET, HEAD, POST, PUT, DELETE, TRACE, OPTIONS");
        }
    }

	private void handleRequest(HttpServletRequest request, HttpServletResponse responseObject) {
        try {
            request.setCharacterEncoding("utf-8");
        } catch (UnsupportedEncodingException ex) {
            logger.warn(ex.getMessage(),ex);
        }

        try {
            request.setCharacterEncoding("utf-8");
        } catch (UnsupportedEncodingException ex) {
            logger.error(ex);
        }

        synchronized (this) {
            if (!configRead) {
                try {
                    readConfig();
                    configRead = true;
                } catch (BlsException e) {
                    // Write HTTP headers (status code, encoding, content type and cache)
                    responseObject.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    responseObject.setCharacterEncoding(OUTPUT_ENCODING.name().toLowerCase());
                    responseObject.setContentType("text/xml");
                    String allowOrigin = searchManager == null ? "*"
                            : searchManager.config().getProtocol().getAccessControlAllowOrigin();
                    if (allowOrigin != null)
                        responseObject.addHeader("Access-Control-Allow-Origin", allowOrigin);
                    ServletUtil.writeCacheHeaders(responseObject, 0);

                    // === Write the response that was captured in buf
                    try {
                        Writer realOut = new OutputStreamWriter(responseObject.getOutputStream(), OUTPUT_ENCODING);
                        realOut.write("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                                "<blacklabResponse><error><code>INTERNAL_ERROR</code><message><![CDATA[ "
                                + e.getMessage() + " ]]></message></error></blacklabResponse>");
                        realOut.flush();
                    } catch (IOException e2) {
                        // Client cancelled the request midway through.
                        // This is okay, don't raise the alarm.
                        logger.debug("(couldn't send response, client probably cancelled the request)");
                    }
                    return;
                }
            }
        }

        if (PrometheusMetricsProvider.handlePrometheus(Metrics.globalRegistry, request, responseObject, OUTPUT_ENCODING.name())) {
            return;
        }

        // === Create RequestHandler object
        boolean debugMode = searchManager.config().getDebug().isDebugMode(request.getRemoteAddr());

        // The outputType handling is a bit iffy:
        // For some urls the dataType is required to determined the correct RequestHandler to instance (the /docs/ and /hits/)
        // For some other urls, the RequestHandler can only output a single type of data
        // and for the rest of the urls, it doesn't matter, so we should just use the default if no explicit type was requested.
        // As long as we're careful not to have urls in multiple of these categories there is never any ambiguity about which handler to use
        // TODO "outputtype"="csv" is broken on the majority of requests, the outputstream will swallow the majority of the printed data
        DataFormat outputType = ServletUtil.getOutputType(request);
        RequestHandler requestHandler = RequestHandler.create(this, request, debugMode, outputType, this.requestInstrumentationProvider);
        if (outputType == null)
            outputType = requestHandler.getOverrideType();
        if (outputType == null)
            outputType = ServletUtil.getOutputTypeFromString(searchManager.config().getProtocol().getDefaultOutputType(), DataFormat.XML);

        // For some auth systems, we need to persist the logged-in user, e.g. by setting a cookie
        searchManager.getAuthSystem().persistUser(this, request, responseObject, requestHandler.getUser());

        // Is this a JSONP request?
        String callbackFunction = ServletUtil.getParameter(request, "jsonp", "");
        boolean isJsonp = callbackFunction.length() > 0;

        int cacheTime = requestHandler.isCacheAllowed() ? searchManager.config().getCache().getClientCacheTimeSec() : 0;

        boolean prettyPrint = ServletUtil.getParameter(request, "prettyprint", debugMode);

        String rootEl = requestHandler.omitBlackLabResponseRootElement() ? null : BLACKLAB_RESPONSE_ROOT_ELEMENT;

        // === Handle the request
        StringWriter buf = new StringWriter();
        PrintWriter out = new PrintWriter(buf);
        DataStream ds = DataStream.create(outputType, out, prettyPrint, callbackFunction);
        ds.setOmitEmptyProperties(searchManager.config().getProtocol().isOmitEmptyProperties());
        ds.startDocument(rootEl);
        StringWriter errorBuf = new StringWriter();
        PrintWriter errorOut = new PrintWriter(errorBuf);
        DataStream es = DataStream.create(outputType, errorOut, prettyPrint, callbackFunction);
        es.outputProlog();
        int errorBufLengthBefore = errorBuf.getBuffer().length();
        int httpCode;
        if (isJsonp && !callbackFunction.matches("[_a-zA-Z][_a-zA-Z0-9]+")) {
            // Illegal JSONP callback name
            httpCode = Response.badRequest(es, "JSONP_ILLEGAL_CALLBACK",
                    "Illegal JSONP callback function name. Must be a valid Javascript name.");
            callbackFunction = "";
        } else {
            try {
                httpCode = requestHandler.handle(ds);
            } catch (InvalidQuery e) {
                httpCode = Response.error(es, "INVALID_QUERY", e.getMessage(), HttpServletResponse.SC_BAD_REQUEST);
            } catch (InternalServerError e) {
                String msg = ServletUtil.internalErrorMessage(e, debugMode, e.getInternalErrorCode());
                httpCode = Response.error(es, e.getBlsErrorCode(), msg, e.getHttpStatusCode());
            } catch (BlsException e) {
                httpCode = Response.error(es, e.getBlsErrorCode(), e.getMessage(), e.getHttpStatusCode());
            } catch (InterruptedSearch e) {
                httpCode = Response.error(es, "INTERRUPTED", e.getMessage(), HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            } catch (RuntimeException e) {
                httpCode = Response.internalError(es, e, debugMode, "INTERR_HANDLING_REQUEST");
            } finally {
                requestHandler.cleanup(); // close logger
            }
        }
        ds.endDocument(rootEl);

        // === Write the response headers

        // Write HTTP headers (status code, encoding, content type and cache)
        if (!isJsonp) // JSONP request always returns 200 OK because otherwise script doesn't load
            responseObject.setStatus(httpCode);
        responseObject.setCharacterEncoding(OUTPUT_ENCODING.name().toLowerCase());
        responseObject.setContentType(ServletUtil.getContentType(outputType));
        String allowOrigin = searchManager.config().getProtocol().getAccessControlAllowOrigin();
        if (allowOrigin != null)
            responseObject.addHeader("Access-Control-Allow-Origin", allowOrigin);
        ServletUtil.writeCacheHeaders(responseObject, cacheTime);

        // === Write the response that was captured in buf
        try {
            Writer realOut = new OutputStreamWriter(responseObject.getOutputStream(), OUTPUT_ENCODING);
            boolean errorOccurred = errorBuf.getBuffer().length() > errorBufLengthBefore;
            StringWriter writeWhat = errorOccurred ? errorBuf : buf;
            realOut.write(writeWhat.toString());
            realOut.flush();
        } catch (IOException e) {
            // Client cancelled the request midway through.
            // This is okay, don't raise the alarm.
            logger.debug("(couldn't send response, client probably cancelled the request)");
            return;
        }
    }

    @Override
    public void destroy() {

        // Stops the load management thread
        if (searchManager != null)
            searchManager.cleanup();

        super.destroy();
    }

    /**
     * Provides a short description of this servlet.
     *
     * @return the description
     */
    @Override
    public String getServletInfo() {
        return "Provides corpus search services on one or more BlackLab indices.\n"
                + "Source available at http://github.com/INL/BlackLab\n"
                + "(C) 2013-" + Calendar.getInstance().get(Calendar.YEAR)
                + " Dutch Language Institute (http://ivdnt.org/)\n"
                + "Licensed under the Apache License v2.\n";
    }

    /**
     * Get the search-related parameteers from the request object.
     *
     * This ignores stuff like the requested output type, etc.
     *
     * Note also that the request type is not part of the SearchParameters, so from
     * looking at these parameters alone, you can't always tell what type of search
     * we're doing. The RequestHandler subclass will add a jobclass parameter when
     * executing the actual search.
     *
     * @param isDocs is this a docs operation? influences how the "sort" parameter
     *            is interpreted
     * @param request the HTTP request
     * @param indexName the index to search
     * @return the unique key
     */
    public SearchParameters getSearchParameters(boolean isDocs, HttpServletRequest request, String indexName) {
        return SearchParameters.get(searchManager, isDocs, indexName, request);
    }

    public SearchManager getSearchManager() {
        return searchManager;
    }

}
