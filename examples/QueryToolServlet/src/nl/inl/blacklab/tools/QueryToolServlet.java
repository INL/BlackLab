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

public class QueryToolServlet extends HttpServlet {

	File indexDir;

	Searcher searcher;

	Map<String, QueryToolSession> sessions = new HashMap<String, QueryToolSession>();

	public QueryToolServlet() {

		LogUtil.initLog4jIfNotAlready();

		//indexDir = new File("D:\\dev\\blacklab\\brown\\index");

		Properties prop;
		try {
			try {
				prop = PropertiesUtil.getFromResource("QueryToolServlet.properties");
				indexDir = PropertiesUtil.getFileProp(prop, "indexDir");
			} catch (RuntimeException e) {
				// Not found
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
	 * Dispatch request to the appropriate session
	 * @param req request object
	 * @param resp response object
	 * @throws IOException
	 */
	private void handleRequest(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		cleanupOldSessions();

		String sessionId = req.getParameter("sessionId");
		if (sessionId == null) {
			resp.setStatus(500);
			resp.setContentType("text/plain");
			resp.getOutputStream().println("Error, no session id");
			return;
		}

		QueryToolSession session;
		if (sessions.containsKey(sessionId)) {
			session = sessions.get(sessionId);
		} else {
			session = new QueryToolSession(this, searcher);
			sessions.put(sessionId, session);
		}

		session.handleRequest(req, resp);
	}

	/**
	 * Remove timed out sessions.
	 */
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
