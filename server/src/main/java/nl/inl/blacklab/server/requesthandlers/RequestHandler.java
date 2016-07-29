package nl.inl.blacklab.server.requesthandlers;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.perdocument.DocCount;
import nl.inl.blacklab.perdocument.DocCounts;
import nl.inl.blacklab.perdocument.DocGroupProperty;
import nl.inl.blacklab.perdocument.DocProperty;
import nl.inl.blacklab.perdocument.DocPropertyMultiple;
import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.ServletUtil;
import nl.inl.blacklab.server.dataobject.DataObjectList;
import nl.inl.blacklab.server.dataobject.DataObjectMapAttribute;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.search.ParseUtil;
import nl.inl.blacklab.server.search.User;

import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;

/**
 * Base class for request handlers, to handle the different types of
 * requests. The static handle() method will dispatch the request to the
 * appropriate subclass.
 */
public abstract class RequestHandler {
	static final Logger logger = Logger.getLogger(RequestHandler.class);

	/** The available request handlers by name */
	static Map<String, Class<? extends RequestHandler>> availableHandlers;

	// Fill the map with all the handler classes
	static {
		availableHandlers = new HashMap<>();
		//availableHandlers.put("cache-info", RequestHandlerCacheInfo.class);
		availableHandlers.put("debug", RequestHandlerDebug.class);
		availableHandlers.put("docs", RequestHandlerDocs.class);
		availableHandlers.put("docs-grouped", RequestHandlerDocsGrouped.class);
		availableHandlers.put("doc-contents", RequestHandlerDocContents.class);
		availableHandlers.put("doc-snippet", RequestHandlerDocSnippet.class);
		availableHandlers.put("doc-info", RequestHandlerDocInfo.class);
		availableHandlers.put("fields", RequestHandlerFieldInfo.class);
		//availableHandlers.put("help", RequestHandlerBlsHelp.class);
		availableHandlers.put("hits", RequestHandlerHits.class);
		availableHandlers.put("hits-grouped", RequestHandlerHitsGrouped.class);
		availableHandlers.put("status", RequestHandlerIndexStatus.class);
		availableHandlers.put("termfreq", RequestHandlerTermFreq.class);
		availableHandlers.put("", RequestHandlerIndexStructure.class);
	}

	/**
	 * Handle a request by dispatching it to the corresponding subclass.
	 *
	 * @param servlet the servlet object
	 * @param request the request object
	 * @return the response data
	 */
	public static Response handle(BlackLabServer servlet, HttpServletRequest request) {
		boolean debugMode = servlet.getSearchManager().isDebugMode(request.getRemoteAddr());

		// See if a user is logged in
		SearchManager searchManager = servlet.getSearchManager();
		User user = searchManager.determineCurrentUser(servlet, request);

		// Parse the URL
		String servletPath = request.getServletPath();
		if (servletPath == null)
			servletPath = "";
		if (servletPath.startsWith("/"))
			servletPath = servletPath.substring(1);
		if (servletPath.endsWith("/"))
			servletPath = servletPath.substring(0, servletPath.length() - 1);
		String[] parts = servletPath.split("/", 3);
		String indexName = parts.length >= 1 ? parts[0] : "";
		if (indexName.startsWith(":")) {
			if (!user.isLoggedIn())
				return Response.unauthorized("Log in to access your private indices.");
			// Private index. Prefix with user id.
			indexName = user.getUserId() + indexName;
		}
		String urlResource = parts.length >= 2 ? parts[1] : "";
		String urlPathInfo = parts.length >= 3 ? parts[2] : "";
		boolean resourceOrPathGiven = urlResource.length() > 0 || urlPathInfo.length() > 0;

		// If we're doing something with a private index, it must be our own.
		boolean isPrivateIndex = false;
		//logger.debug("Got indexName = \"" + indexName + "\" (len=" + indexName.length() + ")");
		String shortName = indexName;
		if (indexName.contains(":")) {
			isPrivateIndex = true;
			String[] userAndIndexName = indexName.split(":");
			if (userAndIndexName.length > 1)
				shortName = userAndIndexName[1];
			else
				return Response.illegalIndexName("");
			if (!user.isLoggedIn())
				return Response.unauthorized("Log in to access your private indices.");
			if (!user.getUserId().equals(userAndIndexName[0]))
				return Response.unauthorized("You cannot access another user's private indices.");
		}

		// Choose the RequestHandler subclass
		RequestHandler requestHandler;

		String method = request.getMethod();
		if (method.equals("DELETE")) {
			// Index given and nothing else?
			if (indexName.length() == 0 || resourceOrPathGiven) {
				return Response.methodNotAllowed("DELETE", null);
			}
			if (!isPrivateIndex)
				return Response.forbidden("You can only delete your own private indices.");
			requestHandler = new RequestHandlerDeleteIndex(servlet, request, user, indexName, null, null);
		} else if (method.equals("PUT")) {
			return Response.methodNotAllowed("PUT", "Create new index with POST to /blacklab-server");
		} else {
			if (method.equals("POST")) {
				if (indexName.length() == 0 && !resourceOrPathGiven) {
					// POST to /blacklab-server/ : create private index
					requestHandler = new RequestHandlerCreateIndex(servlet, request, user, indexName, urlResource, urlPathInfo);
				} else if (indexName.equals("cache-clear")) {
					if (resourceOrPathGiven) {
						return Response.badRequest("UNKNOWN_OPERATION", "Unknown operation. Check your URL.");
					}
					if (!debugMode) {
						return Response.unauthorized("You are not authorized to do this.");
					}
					requestHandler = new RequestHandlerClearCache(servlet, request, user, indexName, urlResource, urlPathInfo);
				} else {
					if (!isPrivateIndex)
						return Response.forbidden("Can only POST to private indices.");
					if (urlResource.equals("docs") && urlPathInfo.length() == 0) {
						if (!SearchManager.isValidIndexName(indexName))
							return Response.illegalIndexName(shortName);

						// POST to /blacklab-server/indexName/docs/ : add data to index
						requestHandler = new RequestHandlerAddToIndex(servlet, request, user, indexName, urlResource, urlPathInfo);
					} else {
						return Response.methodNotAllowed("POST", "Note that retrieval can only be done using GET.");
					}
				}
			} else if (method.equals("GET")) {
				if (indexName.equals("cache-info")) {
					if (resourceOrPathGiven) {
						return Response.badRequest("UNKNOWN_OPERATION", "Unknown operation. Check your URL.");
					}
					if (!debugMode) {
						return Response.unauthorized("You are not authorized to see this information.");
					}
					requestHandler = new RequestHandlerCacheInfo(servlet, request, user, indexName, urlResource, urlPathInfo);
				} else if (indexName.equals("help")) {
					requestHandler = new RequestHandlerBlsHelp(servlet, request, user, indexName, urlResource, urlPathInfo);
				} else if (indexName.length() == 0) {
					// No index or operation given; server info
					requestHandler = new RequestHandlerServerInfo(servlet, request, user, indexName, urlResource, urlPathInfo);
				} else {
					// Choose based on urlResource
					try {
						String handlerName = urlResource;

						String status = searchManager.getIndexStatus(indexName);
						if (!status.equals("available") && handlerName.length() > 0 && !handlerName.equals("debug") && !handlerName.equals("fields") && !handlerName.equals("status")) {
							return Response.unavailable(indexName, status);
						}

						if (debugMode && handlerName.length() > 0 && !handlerName.equals("hits") && !handlerName.equals("docs") && !handlerName.equals("fields") && !handlerName.equals("termfreq") && !handlerName.equals("status")) {
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
								return Response.badRequest("UNKNOWN_OPERATION", "Unknown operation. Check your URL.");
							}
						}
						else if (handlerName.equals("hits") || handlerName.equals("docs")) {
							if (request.getParameter("group") != null) {
								String viewgroup = request.getParameter("viewgroup");
								if (viewgroup == null || viewgroup.length() == 0)
									handlerName += "-grouped"; // list of groups instead of contents
							}
						}

						if (!availableHandlers.containsKey(handlerName))
							return Response.badRequest("UNKNOWN_OPERATION", "Unknown operation. Check your URL.");
						Class<? extends RequestHandler> handlerClass = availableHandlers.get(handlerName);
						Constructor<? extends RequestHandler> ctor = handlerClass.getConstructor(BlackLabServer.class, HttpServletRequest.class, User.class, String.class, String.class, String.class);
						//servlet.getSearchManager().getSearcher(indexName); // make sure it's open
						requestHandler = ctor.newInstance(servlet, request, user, indexName, urlResource, urlPathInfo);
					} catch (BlsException e) {
						return Response.error(e.getBlsErrorCode(), e.getMessage(), e.getHttpStatusCode());
					} catch (NoSuchMethodException e) {
						// (can only happen if the required constructor is not available in the RequestHandler subclass)
						logger.error("Could not get constructor to create request handler", e);
						return Response.internalError(e, debugMode, 2);
					} catch (IllegalArgumentException e) {
						logger.error("Could not create request handler", e);
						return Response.internalError(e, debugMode, 3);
					} catch (InstantiationException e) {
						logger.error("Could not create request handler", e);
						return Response.internalError(e, debugMode, 4);
					} catch (IllegalAccessException e) {
						logger.error("Could not create request handler", e);
						return Response.internalError(e, debugMode, 5);
					} catch (InvocationTargetException e) {
						logger.error("Could not create request handler", e);
						return Response.internalError(e, debugMode, 6);
					}
				}
			} else {
				return Response.internalError("RequestHandler.doGetPost called with wrong method: " + method, debugMode, 10);
			}
		}
		if (debugMode)
			requestHandler.setDebug(debugMode);

		// Handle the request
		try {
			return requestHandler.handle();
		} catch (InternalServerError e) {
			String msg = ServletUtil.internalErrorMessage(e, debugMode, e.getInternalErrorCode());
			return Response.error(e.getBlsErrorCode(), msg, e.getHttpStatusCode());
		} catch (BlsException e) {
			return Response.error(e.getBlsErrorCode(), e.getMessage(), e.getHttpStatusCode());
		} catch (InterruptedException e) {
			return Response.internalError(e, debugMode, 7);
		}
	}

	boolean debugMode;

	/** The servlet object */
	BlackLabServer servlet;

	/** The HTTP request object */
	HttpServletRequest request;

	/** Search parameters from request */
	SearchParameters searchParam;

	/** The BlackLab index we want to access, e.g. "opensonar" for "/opensonar/doc/1/content" */
	String indexName;

	/** The type of REST resource we're accessing, e.g. "doc" for "/opensonar/doc/1/content" */
	String urlResource;

	/** The part of the URL path after the resource name, e.g. "1/content" for "/opensonar/doc/1/content" */
	String urlPathInfo;

	/** The search manager, which executes and caches our searches */
	SearchManager searchMan;

	/** User id (if logged in) and/or session id */
	User user;

	RequestHandler(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathInfo) {
		this.servlet = servlet;
		this.request = request;
		searchMan = servlet.getSearchManager();
		searchParam = servlet.getSearchParameters(request, indexName);
		this.indexName = indexName;
		this.urlResource = urlResource;
		this.urlPathInfo = urlPathInfo;
		this.user = user;

		String pathAndQueryString = ServletUtil.getPathAndQueryString(request);
		if (!pathAndQueryString.startsWith("/cache-info")) // annoying when monitoring
			logger.info(ServletUtil.shortenIpv6(request.getRemoteAddr()) + " " + user.uniqueIdShort() + " " + request.getMethod() + " " + pathAndQueryString);
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
	 * @return the response object
	 * @throws BlsException if the query can't be executed
	 * @throws InterruptedException if the thread was interrupted
	 */
	public abstract Response handle() throws BlsException, InterruptedException;

	/**
	 * Get a string parameter.
	 *
	 * Illegal values will return 0 and log a debug message.
	 *
	 * If the parameter was not specified, the default value will be used.
	 *
	 * @param paramName parameter name
	 * @return the integer value
	 */
	public String getStringParameter(String paramName) {
		return ServletUtil.getParameter(request, paramName, servlet.getSearchManager().getParameterDefaultValue(paramName));
	}

	/**
	 * Get an integer parameter.
	 *
	 * Illegal values will return 0 and log a debug message.
	 *
	 * If the parameter was not specified, the default value will be used.
	 *
	 * @param paramName parameter name
	 * @return the integer value
	 */
	public int getIntParameter(String paramName) {
		String str = getStringParameter(paramName);
		try {
			return ParseUtil.strToInt(str);
		} catch (IllegalArgumentException e) {
			debug(logger, "Illegal integer value for parameter '" + paramName + "': " + str);
			return 0;
		}
	}

	/**
	 * Get a boolean parameter.
	 *
	 * Valid values are: true, false, 1, 0, yes, no, on, off.
	 *
	 * Other values will return false and log a debug message.
	 *
	 * If the parameter was not specified, the default value will be used.
	 *
	 * @param paramName parameter name
	 * @return the boolean value
	 */
	protected boolean getBoolParameter(String paramName) {
		String str = getStringParameter(paramName).toLowerCase();
		try {
			return ParseUtil.strToBool(str);
		} catch (IllegalArgumentException e) {
			debug(logger, "Illegal boolean value for parameter '" + paramName + "': " + str);
			return false;
		}
	}

	/**
	 * Get document information (metadata, contents authorization)
	 *
	 * @param searcher our index
	 * @param document Lucene document
	 * @return the document information
	 */
	public DataObjectMapElement getDocumentInfo(Searcher searcher, Document document) {
		DataObjectMapElement docInfo = new DataObjectMapElement();
		IndexStructure struct = searcher.getIndexStructure();
		for (String metadataFieldName: struct.getMetadataFields()) {
			String value = document.get(metadataFieldName);
			if (value != null)
				docInfo.put(metadataFieldName, value);
		}
		int subtractFromLength = struct.alwaysHasClosingToken() ? 1 : 0;
		String tokenLengthField = struct.getMainContentsField().getTokenLengthField();
		if (tokenLengthField != null)
			docInfo.put("lengthInTokens", Integer.parseInt(document.get(tokenLengthField)) - subtractFromLength);
		docInfo.put("mayView", struct.contentViewable());
		return docInfo;
	}

	protected DataObjectMapAttribute getFacets(DocResults docsToFacet, String facetSpec) {
		DataObjectMapAttribute doFacets;
		DocProperty propMultipleFacets = DocProperty.deserialize(facetSpec);
		List<DocProperty> props = new ArrayList<>();
		if (propMultipleFacets instanceof DocPropertyMultiple) {
			// Multiple facets requested
			for (DocProperty prop: (DocPropertyMultiple)propMultipleFacets) {
				props.add(prop);
			}
		} else {
			// Just a single facet requested
			props.add(propMultipleFacets);
		}

		doFacets = new DataObjectMapAttribute("facet", "name");
		for (DocProperty facetBy: props) {
			DocCounts facetCounts = docsToFacet.countBy(facetBy);
			facetCounts.sort(DocGroupProperty.size());
			DataObjectList doFacet = new DataObjectList("item");
			int n = 0, maxFacetValues = 10;
			int totalSize = 0;
			for (DocCount count: facetCounts) {
				DataObjectMapElement doItem = new DataObjectMapElement();
				doItem.put("value", count.getIdentity().toString());
				doItem.put("size", count.size());
				doFacet.add(doItem);
				totalSize += count.size();
				n++;
				if (n >= maxFacetValues)
					break;
			}
			if (totalSize < facetCounts.getTotalResults()) {
				DataObjectMapElement doItem = new DataObjectMapElement();
				doItem.put("value", "[REST]");
				doItem.put("size", facetCounts.getTotalResults() - totalSize);
				doFacet.add(doItem);
			}
			doFacets.put(facetBy.getName(), doFacet);
		}
		return doFacets;
	}

	protected Searcher getSearcher() throws BlsException {
		return searchMan.getSearcher(indexName);
	}

	public static DataObjectMapElement getDocFields(IndexStructure struct) {
		DataObjectMapElement docFields = new DataObjectMapElement();
		if (struct.pidField() != null)
			docFields.put("pidField", struct.pidField());
		if (struct.titleField() != null)
			docFields.put("titleField", struct.titleField());
		if (struct.authorField() != null)
			docFields.put("authorField", struct.authorField());
		if (struct.dateField() != null)
			docFields.put("dateField", struct.dateField());
		return docFields;
	}

	/**
	 * Get the pid for the specified document
	 *
	 * @param searcher where we got this document from
	 * @param luceneDocId
	 *            Lucene document id
	 * @param document
	 *            the document object
	 * @return the pid string (or Lucene doc id in string form if index has no
	 *         pid field)
	 */
	public static String getDocumentPid(Searcher searcher, int luceneDocId,
			Document document) {
		String pidField = searcher.getIndexStructure().pidField(); //getIndexParam(indexName, user).getPidField();
		if (pidField == null || pidField.length() == 0)
			return "" + luceneDocId;
		return document.get(pidField);
	}

}
