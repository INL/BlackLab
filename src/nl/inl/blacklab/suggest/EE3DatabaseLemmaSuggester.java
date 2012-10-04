/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.blacklab.suggest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Suggester using the EE3 database to suggest lemmas given a word form
 */
public class EE3DatabaseLemmaSuggester extends Suggester {
	private Connection conn;

	public EE3DatabaseLemmaSuggester(Connection connection) {
		conn = connection;
	}

	@Override
	public void addSuggestions(String original, Suggestions sugg) {
		try {
			final String fullQuery = "SELECT DISTINCT modern_lemma FROM lemmata, simple_analyzed_wordforms, "
					+ "wordforms WHERE lemmata.lemma_id = simple_analyzed_wordforms.lemma_id AND "
					+ "simple_analyzed_wordforms.wordform_id = wordforms.wordform_id AND "
					+ "wordforms.wordform = ?";
			PreparedStatement stmt = conn.prepareStatement(fullQuery);
			try {
				stmt.setString(1, original);
				ResultSet rs = stmt.executeQuery();
				while (rs.next()) {
					sugg.addSuggestion("spellvar", rs.getString(1));
				}
			} finally {
				stmt.close();
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public static void main(String[] args) throws ClassNotFoundException, SQLException {
		// Register the JDBC driver for MySQL.
		String url = "jdbc:mysql://impactdb.inl.loc:3306/EE3?useUnicode=true&characterEncoding=utf8&autoReconnect=true";
		Class.forName("com.mysql.jdbc.Driver");
		Connection conn = DriverManager.getConnection(url, "impact", "impact");
		try {
			Suggester dbs = new EE3DatabaseLemmaSuggester(conn);
			String[] w = new String[] { "de", "appel", "peer" };
			for (String word : w) {
				System.out.println(word + ": " + dbs.suggest(word));
			}
		} finally {
			conn.close();
		}

	}

}
