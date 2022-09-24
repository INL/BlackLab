package nl.inl.blacklab.server;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.instrumentation.MetricsProvider;
import nl.inl.blacklab.instrumentation.RequestInstrumentationProvider;
import nl.inl.blacklab.instrumentation.impl.PrometheusMetricsProvider;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.config.ConfigFileReader;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.ConfigurationException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.requesthandlers.RequestHandler;
import nl.inl.blacklab.server.requesthandlers.Response;
import nl.inl.blacklab.server.requesthandlers.BlackLabServerParams;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.requesthandlers.ServletUtil;
import nl.inl.blacklab.server.util.WebserviceUtil;

public class BlackLabServer extends HttpServlet {

    private static final Logger logger = LogManager.getLogger(BlackLabServer.class);

    /** Root element to use for XML responses. */
    public static final String BLACKLAB_RESPONSE_ROOT_ELEMENT = "blacklabResponse";

    private static final Charset REQUEST_ENCODING = StandardCharsets.UTF_8;

    private static final Charset OUTPUT_ENCODING = StandardCharsets.UTF_8;

    private static final String CONFIG_FILE_NAME = "blacklab-server";

    /** Manages all our searches */
    private SearchManager searchManager;

    private RequestInstrumentationProvider requestInstrumentationProvider = null;

    @Override
    public void init() throws ServletException {
        logger.info("Starting BlackLab Server...");
        super.init();
        logger.info("BlackLab Server ready.");
    }

    private synchronized void ensureSearchManagerAvailable() throws BlsException {
        if (searchManager == null) {
            try {
                BLSConfig config = readConfig();

                // Create our search manager (main webservice class)
                searchManager = new SearchManager(config);

                // Set default parameter settings from config
                BlackLabServerParams.setDefaults(config.getParameters());

                // Configure metrics provider (e.g Prometheus)
                setMetricsProvider(config);
                this.requestInstrumentationProvider = getRequestInstrumentationProvider(config);

            } catch (JsonProcessingException e) {
                throw new ConfigurationException("Invalid JSON in configuration file", e);
            } catch (IOException e) {
                throw new ConfigurationException("Error reading configuration file", e);
            }
        }
    }

    private BLSConfig readConfig() throws IOException, ConfigurationException {
        File servletPath = new File(getServletContext().getRealPath("."));
        logger.debug("Running from dir: " + servletPath);
        List<File> searchDirs = new ArrayList<>();
        searchDirs.add(servletPath.getAbsoluteFile().getParentFile().getCanonicalFile());
        searchDirs.addAll(BlackLab.defaultConfigDirs());
        return ConfigFileReader.getBlsConfig(searchDirs, CONFIG_FILE_NAME);
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
            return (RequestInstrumentationProvider)
                Class.forName(fqClassName).getDeclaredConstructor().newInstance();

        } catch (Exception ex) {
            throw new ConfigurationException("Can not create request instrumentation provider with class" + fqClassName);
        }
    }


    /**
     * Process POST requests (add data to index)
     *
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse responseObject) {
        handleRequest(request, responseObject);
    }

    /**
     * Process PUT requests (create index)
     *
     */
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse responseObject) {
        handleRequest(request, responseObject);
    }

    /**
     * Process DELETE requests (create a index, add data to one)
     *
     */
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse responseObject) {
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
    protected void doOptions(HttpServletRequest request, HttpServletResponse responseObject)
            throws ServletException, IOException {
        super.doOptions(request, responseObject);
        String allowOrigin = optAddAllowOriginHeader(responseObject);
        if (allowOrigin != null) {
            responseObject.addHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"));
        	responseObject.addHeader("Access-Control-Allow-Methods", "GET, HEAD, POST, PUT, DELETE, TRACE, OPTIONS");
        }
    }

    private String optAddAllowOriginHeader(HttpServletResponse responseObject) {
        String allowOrigin = searchManager == null ? "*" : searchManager.config().getProtocol().getAccessControlAllowOrigin();
        if (allowOrigin != null)
            responseObject.addHeader("Access-Control-Allow-Origin", allowOrigin);
        return allowOrigin;
    }

    private void handleRequest(HttpServletRequest request, HttpServletResponse responseObject) {
        try {
            request.setCharacterEncoding(REQUEST_ENCODING.name());
        } catch (UnsupportedEncodingException ex) {
            logger.error(ex);
        }

        try {
            ensureSearchManagerAvailable();
        } catch (BlsException e) {
            initializationErrorResponse(responseObject, e);
            return;
        }

        if (PrometheusMetricsProvider.handlePrometheus(Metrics.globalRegistry, request, responseObject, OUTPUT_ENCODING.name())) {
            return;
        }

        // === Create RequestHandler object

        boolean debugMode = searchManager.isDebugMode(ServletUtil.getOriginatingAddress(request));

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
            outputType = DataFormat.fromString(searchManager.config().getProtocol().getDefaultOutputType(), DataFormat.XML);

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
        ds.setOmitEmptyAnnotations(searchManager.config().getProtocol().isOmitEmptyProperties());
        ds.startDocument(rootEl);
        StringWriter errorBuf = new StringWriter();
        PrintWriter errorOut = new PrintWriter(errorBuf);
        DataStream es = DataStream.create(outputType, errorOut, prettyPrint, callbackFunction);
        es.outputProlog();
        int errorBufLengthBefore = errorBuf.getBuffer().length();
        int httpCode;
        if (isJsonp && !callbackFunction.matches("[_a-zA-Z]\\w+")) {
            // Illegal JSONP callback name
            httpCode = Response.badRequest(es, "JSONP_ILLEGAL_CALLBACK",
                    "Illegal JSONP callback function name. Must be a valid Javascript name.");
        } else {
            try {
                httpCode = requestHandler.handle(ds);
            } catch (InvalidQuery e) {
                httpCode = Response.error(es, "INVALID_QUERY", e.getMessage(), HttpServletResponse.SC_BAD_REQUEST);
            } catch (InternalServerError e) {
                String msg = WebserviceUtil.internalErrorMessage(e, debugMode, e.getInternalErrorCode());
                httpCode = Response.error(es, e.getBlsErrorCode(), msg, e.getHttpStatusCode(), e);
            } catch (BlsException e) {
                httpCode = Response.error(es, e.getBlsErrorCode(), e.getMessage(), e.getHttpStatusCode());
            } catch (InterruptedSearch e) {
                httpCode = Response.error(es, "INTERRUPTED", e.getMessage(), HttpServletResponse.SC_SERVICE_UNAVAILABLE, e);
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
        responseObject.setContentType(outputType.getContentType());
        optAddAllowOriginHeader(responseObject);
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
        }
    }

    private void initializationErrorResponse(HttpServletResponse responseObject, BlsException e) {
        // Write HTTP headers (status code, encoding, content type and cache)
        responseObject.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        responseObject.setCharacterEncoding(OUTPUT_ENCODING.name().toLowerCase());
        responseObject.setContentType("text/xml");
        optAddAllowOriginHeader(responseObject);
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
                + "Source available at https://github.com/INL/BlackLab\n"
                + "(C) 2013-" + Calendar.getInstance().get(Calendar.YEAR)
                + " Dutch Language Institute (https://ivdnt.org/)\n"
                + "Licensed under the Apache License v2.\n";
    }

    public SearchManager getSearchManager() {
        return searchManager;
    }

}
