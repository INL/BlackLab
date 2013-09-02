package nl.inl.blacklab.tools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import nl.inl.blacklab.search.Searcher;
import nl.inl.util.LogUtil;
import nl.inl.util.PropertiesUtil;

/**
 * The QueryTool servlet, which gives you command-driven access to
 * a BlackLab index (same as the nl.inl.blacklab.tools.QueryTool console
 * application).
 */
public class QueryToolServlet extends HttpServlet {

	/** The opened Searcher objects (shared by all users) */
	Map<String, Searcher> searchers = new HashMap<String, Searcher>();

	/** The default Searcher object (first corpus to activate) */
	Searcher defaultSearcher;

	/** The active user sessions */
	Map<String, QueryToolSession> sessions = new HashMap<String, QueryToolSession>();

	/** The available indices */
	private String[] indexNames;

	private Properties prop;

	public QueryToolServlet() {

		LogUtil.initLog4jIfNotAlready();

		// We'd like our forward indices nice and warm, please.
		Searcher.setAutoWarmForwardIndices(true);

		// Read which index to open from the properties file.
		try {
			prop = PropertiesUtil.getFromResource("QueryToolServlet.properties");
			indexNames = prop.getProperty("indexNames", "").trim().split("\\s+");
			if (indexNames.length > 0) {
				// Open default index in advance
				openIndex(indexNames[0]);
			} else {
				// No properties file; try a default value
				File indexDir = new File("/vol1/molechaser/data/gmc/index");
				if (indexDir.exists()) {
					defaultSearcher = Searcher.open(indexDir);
					searchers.put("gmc", defaultSearcher);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	private void openIndex(String indexName) {
		File indexDir = PropertiesUtil.getFileProp(prop, indexName + "_dir");
		if (indexDir == null || !indexDir.exists())
			return;
		try {
			Searcher searcher = Searcher.open(indexDir);
			searchers.put(indexName, searcher);
			if (defaultSearcher == null)
				defaultSearcher = searcher;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	Searcher getSearcher(String name) {
		if (!searchers.containsKey(name))
			openIndex(name);
		return searchers.get(name);
	}

	/**
	 * Dispatch request to the appropriate session.
	 * @param req request object
	 * @param resp response object
	 * @throws IOException
	 */
	private void handleRequest(HttpServletRequest req, HttpServletResponse resp) throws IOException {

		// See if there are any timed out sessions and remove them.
		cleanupOldSessions();

		// Get the session id sent by the client.
		// NOTE: the client generates a session id because this is fundamentally a
		// stateful application (being a console application), and we need to be able
		// to distinguish between two tabs in the same browser running this application.
		// "Normal" page-based web applications generally strive to be stateless,
		// so this approach is not necessarily one to follow in your own applications.
		String sessionId = req.getParameter("sessionId");
		if (sessionId == null) {
			resp.setStatus(500);
			resp.setContentType("text/plain");
			resp.getOutputStream().println("Error, no session id");
			return;
		}

		// See if this session already exists, or it's a new session.
		QueryToolSession session;
		if (sessions.containsKey(sessionId)) {
			session = sessions.get(sessionId);
		} else {
			if (defaultSearcher == null) {
				// No indices available! Give error message.
				errorNoIndices(resp);
				return;
			}

			// Start a new session with the default index
			session = new QueryToolSession(this, defaultSearcher);
			sessions.put(sessionId, session);
		}

		// Let the session object handle the request
		session.handleRequest(req, resp);
	}

	private void errorNoIndices(HttpServletResponse resp) {
		resp.setContentType("text/plain");
		try {
			ServletOutputStream os = resp.getOutputStream();
			os.print(
				"CONFIGURATION ERROR: no indices available!\n\n" +
				"For this application to work, there should be a file named\n" +
				"QueryToolServlet.properties on the classpath (e.g. in\n" +
				"$TOMCAT/shared/classes) containing a property indexNames\n" +
				"(a space-separated list of names) and for each name, a property\n" +
				"<name>_dir containing the index directory.\n");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/** Remove timed out sessions. */
	private void cleanupOldSessions() {
		List<String> toRemove = new ArrayList<String>();
		for (Map.Entry<String, QueryToolSession> e: sessions.entrySet()) {
			QueryToolSession session = e.getValue();
			if (session.timedOut()) {
				toRemove.add(e.getKey());
			}
		}
		for (String id: toRemove) {
			sessions.remove(id);
		}
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		handleRequest(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		handleRequest(req, resp);
	}


}
