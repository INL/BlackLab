package nl.inl.blacklab.tools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import nl.inl.blacklab.search.Searcher;
import nl.inl.util.LogUtil;
import nl.inl.util.PropertiesUtil;

import org.apache.lucene.index.CorruptIndexException;

/**
 * The QueryTool servlet, which gives you command-driven access to
 * a BlackLab index (same as the nl.inl.blacklab.tools.QueryTool console
 * application).
 */
public class QueryToolServlet extends HttpServlet {

	/** The index to search */
	File indexDir;

	/** The Searcher object (shared by all users) */
	Searcher searcher;

	/** The active user sessions */
	Map<String, QueryToolSession> sessions = new HashMap<String, QueryToolSession>();

	public QueryToolServlet() {

		LogUtil.initLog4jIfNotAlready();

		// Read which index to open from the properties file.
		Properties prop;
		try {
			try {
				prop = PropertiesUtil.getFromResource("QueryToolServlet.properties");
				indexDir = PropertiesUtil.getFileProp(prop, "indexDir");
			} catch (RuntimeException e) {
				// Not found; try a default value.
				indexDir = new File("/vol1/molechaser/data/gmc/index");
			}
			searcher = Searcher.open(indexDir);
		} catch (CorruptIndexException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

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
			// New session
			session = new QueryToolSession(this, searcher);
			sessions.put(sessionId, session);
		}

		// Let the session object handle the request
		session.handleRequest(req, resp);
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
