package nl.inl.blacklab.server;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.search.RegexpTooLargeException;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.requesthandlers.RequestHandler;
import nl.inl.blacklab.server.requesthandlers.Response;
import nl.inl.blacklab.server.requesthandlers.SearchParameters;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.util.ServletUtil;
import nl.inl.util.Json;

public class BlackLabServer extends HttpServlet {
	private static final Logger logger = LogManager.getLogger(BlackLabServer.class);

	static final Charset CONFIG_ENCODING = Charset.forName("utf-8");

	static final Charset OUTPUT_ENCODING = Charset.forName("utf-8");

	/** Manages all our searches */
	private SearchManager searchManager;

	@Override
	public void init() throws ServletException {
		// Default init if no log4j.properties found
		//LogUtil.initLog4jIfNotAlready(Level.DEBUG);

		logger.info("Starting BlackLab Server...");

		super.init();
		try (InputStream is = openConfigFile()) {
			searchManager = new SearchManager(Json.read(is, CONFIG_ENCODING));
		} catch (Exception e) {
			throw new ServletException("Error initializing SearchManager: " + e.getMessage(), e);
		}
		logger.info("BlackLab Server ready.");

	}

	private InputStream openConfigFile() throws ServletException {
		// Read JSON config file, either from the servlet context directory's parent,
		// from /etc/blacklab/ or from the classpath.
		String configFileName = "blacklab-server.json";
		String realPath = getServletContext().getRealPath(".");
		logger.debug("Running from dir: " + realPath);
		File configFile = new File(realPath + "/../" + configFileName);
		InputStream is;
		if (configFile.exists()) {
			// Read from webapps dir
			try {
				logger.debug("Reading configuration file " + configFile);
				is = new BufferedInputStream(new FileInputStream(configFile));
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		} else {
			configFile = new File("/etc/blacklab", configFileName);
			if (!configFile.exists())
				configFile = new File("/vol1/etc/blacklab", configFileName); // UGLY, will fix later
			if (configFile.exists()) {
				try {
					logger.debug("Reading configuration file " + configFile);
					is = new BufferedInputStream(new FileInputStream(configFile));
				} catch (FileNotFoundException e) {
					throw new RuntimeException(e);
				}
			} else {
				// Read from classpath
				logger.debug(configFileName + " not found in webapps dir; searching classpath...");
				is = getClass().getClassLoader().getResourceAsStream(configFileName);
				if (is == null) {
					logger.debug(configFileName + " not found on classpath either. Using internal defaults.");
					configFileName = "blacklab-server-defaults.json"; // internal defaults file
					is = getClass().getClassLoader().getResourceAsStream(configFileName);
					if (is == null)
						throw new ServletException("Could not find " + configFileName + "!");
				}
				logger.debug("Reading configuration file from classpath: " + configFileName);
			}
		}
		return is;
	}

	/**
	 * Process POST requests (add data to index)
	 * @throws ServletException
	 */
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse responseObject) throws ServletException {
		handleRequest(request, responseObject);
	}

	/**
	 * Process PUT requests (create index)
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

	private void handleRequest(HttpServletRequest request, HttpServletResponse responseObject) {

		// === Create RequestHandler object
		RequestHandler requestHandler = RequestHandler.create(this, request);

		// === Figure stuff out about the request
		boolean debugMode = searchManager.config().isDebugMode(request.getRemoteAddr());
		DataFormat outputType = requestHandler.getOverrideType();
		//DataFormat outputType = response.getOverrideType(); // some responses override the user's request (i.e. article XML)
		if (outputType == null) {
			outputType = ServletUtil.getOutputType(request, searchManager.config().defaultOutputType());
		}

		// Is this a JSONP request?
		String callbackFunction = ServletUtil.getParameter(request, "jsonp", "");
		boolean isJsonp = callbackFunction.length() > 0;

		int cacheTime = requestHandler.isCacheAllowed() ? searchManager.config().clientCacheTimeSec() : 0;

		boolean prettyPrint = ServletUtil.getParameter(request, "prettyprint", debugMode);

		String rootEl = requestHandler.omitBlackLabResponseRootElement() ? null : "blacklabResponse";

		// === Handle the request
		StringWriter buf = new StringWriter();
		PrintWriter out = new PrintWriter(buf);
		DataStream ds = DataStream.create(outputType, out, prettyPrint, callbackFunction);
		ds.startDocument(rootEl);
		StringWriter errorBuf = new StringWriter();
		PrintWriter errorOut = new PrintWriter(errorBuf);
		DataStream es = DataStream.create(outputType, errorOut, prettyPrint, callbackFunction);
		es.outputProlog();
		int errorBufLengthBefore = errorBuf.getBuffer().length();
		int httpCode;
		if (isJsonp && !callbackFunction.matches("[_a-zA-Z][_a-zA-Z0-9]+")) {
			// Illegal JSONP callback name
			httpCode = Response.badRequest(es, "JSONP_ILLEGAL_CALLBACK", "Illegal JSONP callback function name. Must be a valid Javascript name.");
			callbackFunction = "";
		} else {
			try {
				httpCode = requestHandler.handle(ds);
			} catch (InternalServerError e) {
				String msg = ServletUtil.internalErrorMessage(e, debugMode, e.getInternalErrorCode());
				httpCode = Response.error(es, e.getBlsErrorCode(), msg, e.getHttpStatusCode());
			} catch (BlsException e) {
				httpCode = Response.error(es, e.getBlsErrorCode(), e.getMessage(), e.getHttpStatusCode());
			} catch (InterruptedException e) {
				httpCode = Response.internalError(es, e, debugMode, 7);
			} catch (RegexpTooLargeException e) {
				httpCode = Response.badRequest(es, "REGEXP_TOO_LARGE", e.getMessage());
			} catch (RuntimeException e) {
				httpCode = Response.internalError(es, e, debugMode, 32);
			}
		}
		ds.endDocument(rootEl);

		// === Write the response headers

		// Write HTTP headers (status code, encoding, content type and cache)
		if (!isJsonp) // JSONP request always returns 200 OK because otherwise script doesn't load
			responseObject.setStatus(httpCode);
		responseObject.setCharacterEncoding(OUTPUT_ENCODING.name().toLowerCase());
		responseObject.setContentType(ServletUtil.getContentType(outputType));
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
		searchManager.cleanup();

		super.destroy();
	}

	/**
	 * Provides a short description of this servlet.
	 * @return the description
	 */
	@Override
	public String getServletInfo() {
		return "Provides corpus search services on one or more BlackLab indices.\n"
				+ "Source available at http://github.com/INL/BlackLab\n"
				+ "(C) 2013,2014-... Instituut voor Nederlandse Lexicologie.\n"
				+ "Licensed under the Apache License.\n";
	}

	/**
	 * Get the search-related parameteers from the request object.
	 *
	 * This ignores stuff like the requested output type, etc.
	 *
	 * Note also that the request type is not part of the SearchParameters, so from looking at these
	 * parameters alone, you can't always tell what type of search we're doing. The RequestHandler subclass
	 * will add a jobclass parameter when executing the actual search.
	 *
	 * @param isDocs is this a docs operation? influences how the "sort" parameter is interpreted
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
