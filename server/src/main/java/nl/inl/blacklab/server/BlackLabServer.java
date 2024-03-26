package nl.inl.blacklab.server;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
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

import com.fasterxml.jackson.core.JacksonException;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
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
import nl.inl.blacklab.server.datastream.DataStreamAbstract;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.ConfigurationException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.lib.Response;
import nl.inl.blacklab.server.lib.results.ApiVersion;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.requesthandlers.RequestHandler;
import nl.inl.blacklab.server.requesthandlers.UserRequestBls;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.util.ServletUtil;
import nl.inl.blacklab.server.util.WebserviceUtil;
import nl.inl.blacklab.webservice.WebserviceParameter;

public class BlackLabServer extends HttpServlet {

    private static final Logger logger = LogManager.getLogger(BlackLabServer.class);

    private static final Charset REQUEST_ENCODING = StandardCharsets.UTF_8;

    private static final Charset OUTPUT_ENCODING = StandardCharsets.UTF_8;

    private static final String CONFIG_FILE_NAME = "blacklab-server";

    /** Manages all our searches */
    private SearchManager searchManager;

    private RequestInstrumentationProvider requestInstrumentationProvider = null;

    /** Default output type to use if none given. */
    private DataFormat defaultOutputType;

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
                searchManager = new SearchManager(config, true);

                // Set defaults from config in ParameterDefaults
                config.getParameters().setParameterDefaults();

                // Configure metrics provider (e.g Prometheus)
                setMetricsProvider(config);
                getInstrumentationProvider(); // ensure creation

                // Determine default output type.
                defaultOutputType = DataFormat.fromString(searchManager.config().getProtocol().getDefaultOutputType(),
                        DataFormat.XML);
            } catch (JacksonException e) {
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

    public synchronized RequestInstrumentationProvider getInstrumentationProvider() {
        if (requestInstrumentationProvider == null) {
            BLSConfig config = searchManager.config();
            requestInstrumentationProvider = WebserviceUtil.createInstrumentationProvider(config);
        }
        return requestInstrumentationProvider;
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
        DataFormat outputType = ServletUtil.getOutputType(request);
        try {
            request.setCharacterEncoding(REQUEST_ENCODING.name());
        } catch (UnsupportedEncodingException ex) {
            logger.error(ex);
        }

        try {
            ensureSearchManagerAvailable();
        } catch (BlackLabRuntimeException | BlsException e) {
            boolean prettyPrint = ServletUtil.getParameter(request, "prettyprint", true);
            String strApiVersion = ServletUtil.getParameter(request, WebserviceParameter.API_VERSION.value(),
                    ApiVersion.CURRENT.toString());
            ApiVersion apiVersion = ApiVersion.fromValue(strApiVersion);
            initializationErrorResponse(responseObject, e, outputType, apiVersion, prettyPrint);
            return;
        }

        if (PrometheusMetricsProvider.handlePrometheus(Metrics.globalRegistry, request, responseObject, OUTPUT_ENCODING.name())) {
            return;
        }

        // === Create RequestHandler object

        // The outputType handling is a bit iffy:
        // For some urls the dataType is required to determined the correct RequestHandler to instance
        // (the /docs/ and /hits/, because of how CSV is handled)
        // For some other urls, the RequestHandler can only output a single type of data
        // and for the rest of the urls, it doesn't matter, so we should just use the default if no explicit type
        // was requested.
        // As long as we're careful not to have urls in multiple of these categories there is never any ambiguity
        // about which handler to use
        // Note that only some requests support CSV output (hits/docs); requesting it should return an error on
        // requests that don't support it.
        UserRequestBls userRequest = new UserRequestBls(this, request, responseObject);
        RequestHandler requestHandler = RequestHandler.create(userRequest, outputType);
        if (outputType == null)
            outputType = requestHandler.getOverrideType();
        if (outputType == null)
            outputType = defaultOutputType;

        // For some auth systems, we need to persist the logged-in user, e.g. by setting a cookie
        searchManager.getAuthSystem().persistUser(userRequest, requestHandler.getUser());

        int cacheTime = requestHandler.isCacheAllowed() ? searchManager.config().getCache().getClientCacheTimeSec() : 0;

        String rootEl = requestHandler.omitBlackLabResponseRootElement() ? null : ResponseStreamer.BLACKLAB_RESPONSE_ROOT_ELEMENT;

        // === Handle the request
        boolean prettyPrint = ServletUtil.getParameter(request, "prettyprint", userRequest.isDebugMode());
        ApiVersion api = requestHandler.apiCompatibility();
        DataStream ds = DataStreamAbstract.create(outputType, prettyPrint, api);
        ds.setOmitEmptyAnnotations(searchManager.config().getProtocol().isOmitEmptyProperties());
        ds.startDocument(rootEl);
        ResponseStreamer dstream = ResponseStreamer.get(ds, api);
        DataStream es = DataStreamAbstract.create(outputType, prettyPrint, api);
        es.outputProlog();
        ResponseStreamer errorWriter = ResponseStreamer.get(es, api);
        int errorBufLengthBefore = es.length();
        int httpCode;
        try {
            httpCode = requestHandler.handle(dstream);
        } catch (ErrorOpeningIndex e) {
            httpCode = Response.internalError(errorWriter, e, userRequest.isDebugMode(), "ERROR_OPENING_INDEX");
        } catch (InvalidQuery e) {
            httpCode = Response.error(errorWriter, "INVALID_QUERY", e.getMessage(), HttpServletResponse.SC_BAD_REQUEST);
        } catch (InternalServerError e) {
            String msg = WebserviceUtil.internalErrorMessage(e, userRequest.isDebugMode(), e.getInternalErrorCode());
            httpCode = Response.error(errorWriter, e.getBlsErrorCode(), msg, e.getHttpStatusCode(), e);
        } catch (BlsException e) {
            httpCode = Response.error(errorWriter, e.getBlsErrorCode(), e.getMessage(), e.getHttpStatusCode());
        } catch (InterruptedSearch e) {
            httpCode = Response.error(errorWriter, "INTERRUPTED", e.getMessage(), HttpServletResponse.SC_SERVICE_UNAVAILABLE, e);
        } catch (RuntimeException e) {
            httpCode = Response.internalError(errorWriter, e, userRequest.isDebugMode(), "INTERR_HANDLING_REQUEST");
        } finally {
            requestHandler.cleanup(); // close logger
        }
        ds.endDocument();

        // === Write the response headers

        // Write HTTP headers (status code, encoding, content type and cache)
        responseObject.setStatus(httpCode);
        responseObject.setCharacterEncoding(OUTPUT_ENCODING.name().toLowerCase());
        responseObject.setContentType(outputType.getContentType());
        optAddAllowOriginHeader(responseObject);
        ServletUtil.writeCacheHeaders(responseObject, cacheTime);

        // === Write the response that was captured in buf
        try {
            Writer realOut = new OutputStreamWriter(responseObject.getOutputStream(), OUTPUT_ENCODING);
            if (es.length() > errorBufLengthBefore) {
                // an error occurred
                realOut.write(es.getOutput());
            } else {
                realOut.write(ds.getOutput());
            }
            realOut.flush();
        } catch (IOException e) {
            // Client cancelled the request midway through.
            // This is okay, don't raise the alarm.
            logger.debug("(couldn't send response, client probably cancelled the request)");
        }
    }

    private void initializationErrorResponse(HttpServletResponse responseObject, Exception e, DataFormat outputType,
            ApiVersion api, boolean prettyPrint) {
        // Write HTTP headers (status code, encoding, content type and cache)
        responseObject.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        responseObject.setCharacterEncoding(OUTPUT_ENCODING.name().toLowerCase());
        responseObject.setContentType(outputType.getContentType());
        optAddAllowOriginHeader(responseObject);
        ServletUtil.writeCacheHeaders(responseObject, 0);

        // === Write the response that was captured in buf
        try {
            DataStream es = DataStreamAbstract.create(outputType, prettyPrint, api);
            es.outputProlog();
            es.error("INTERNAL_ERROR", e.getMessage(), e);
            Writer realOut = new OutputStreamWriter(responseObject.getOutputStream(), OUTPUT_ENCODING);
            realOut.write(es.getOutput());
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
