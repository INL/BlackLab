package nl.inl.blacklab.tools;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import nl.inl.blacklab.search.Searcher;

/**
 * Represents the backend for one tab in which the QueryTool page is open.
 * Note that one user may be running multiple sessions (multiple tabs pointing
 * to the same page); these sessions are independent of one another.
 */
public class QueryToolSession {

	/** Sessions time out after an hour of inactivity. */
	private static final int TIMEOUT_SEC = 3600;

	/** This session's QueryTool object */
	private QueryTool queryTool;

	/** Output buffer (used to reroute output to a string) */
	StringBuilder buf;

    /** Output writer */
	private PrintWriter out;

	/** Time of last activity */
	private long lastActivityTime = System.currentTimeMillis();

	/** Our servlet */
	@SuppressWarnings("unused")
	private QueryToolServlet servlet;

	/** If first command is not "help", print "session timed out" msg */
	private boolean firstCommand = true;

	public QueryToolSession(QueryToolServlet servlet, Searcher searcher) {
		this.servlet = servlet;

		try {
			// Route QueryTool output into StringBuilder
			buf = new StringBuilder();
			out = new PrintWriter(new Writer() {
				@Override
				public void write(char[] cbuf, int off, int len) {
					buf.append(cbuf, off, len);
				}

				@Override
				public void flush() {
					// NOP
				}

				@Override
				public void close() {
					// NOP
				}
			}, true);
			queryTool = new QueryTool(searcher, null, out);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void handleRequest(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		// Get the user's command
		String command = req.getParameter("command");
		if (command == null)
			command = "";

		// Clear the output buffer
		buf.setLength(0);

		// See if we've timed out, and print a message if so
		if (firstCommand && !command.equals("help")) {
			out.println("\n*** SESSION RESET (TIMED OUT?) ***\n");
		}
		firstCommand = false;

		// Execute the command
		queryTool.processCommand(command);

		// Keep track of last activity in this session,
		// so we know when it times out.
		lastActivityTime = System.currentTimeMillis();

		// Print the response
		resp.setContentType("text/plain");
		resp.setCharacterEncoding("utf-8");
		resp.getWriter().append(buf.toString());

	}

	/**
	 * Has this session not seen any activity for a long time?
	 * @return true iff we timed out
	 */
	public boolean timedOut() {
		return System.currentTimeMillis() - lastActivityTime >= TIMEOUT_SEC * 1000;
	}

}
