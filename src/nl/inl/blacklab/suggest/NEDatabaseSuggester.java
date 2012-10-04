/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.suggest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import nl.inl.util.Utilities;

/**
 * Suggester using the NE database to suggest word form variants given a word form
 */
public class NEDatabaseSuggester extends Suggester {
	private Connection conn;

	public NEDatabaseSuggester(Connection connection) {
		conn = connection;
	}

	@Override
	public void addSuggestions(String original, Suggestions sugg) {
		try {
			// System.err.println("Getting suggestions for " + original);
			// Full query, find variants from word form (via lemma)

			boolean newQuery = false;

			String fullQuery;

			if (newQuery) {
				fullQuery = "SELECT wf2.wordform " + "  FROM wordforms wf1, "
						+ "       wordforms wf2, " + "       analyzed_wordforms awf1, "
						+ "       analyzed_wordforms awf2, " + "       ne_variant_relations nevr "
						+ " WHERE awf1.wordform_id = wf1.wordform_id "
						+ "   AND awf1.lemma_id = nevr.first_lemma_id "
						+ "   AND nevr.second_lemma_id = awf2.lemma_id "
						+ "   AND nevr.ne_variant_relation_type_id != 3 "
						+ "   AND awf2.wordform_id = wf2.wordform_id "
						+ "   AND wf1.wordform_lowercase = ? " + "UNION  " + "SELECT wf2.wordform "
						+ "  FROM wordforms wf1, " + "       wordforms wf2, "
						+ "       analyzed_wordforms awf1, " + "       analyzed_wordforms awf2, "
						+ "       ne_variant_relations nevr "
						+ " WHERE awf1.wordform_id = wf1.wordform_id "
						+ "   AND awf1.lemma_id = nevr.second_lemma_id "
						+ "   AND nevr.first_lemma_id = awf2.lemma_id "
						+ "   AND nevr.ne_variant_relation_type_id != 3 "
						+ "   AND awf2.wordform_id = wf2.wordform_id "
						+ "   AND wf1.wordform_lowercase = ? " + "UNION  " + "SELECT wf2.wordform "
						+ "  FROM wordforms wf1, " + "       wordforms wf2, "
						+ "       analyzed_wordforms awf1, " + "       analyzed_wordforms awf2 "
						+ " WHERE awf1.wordform_id = wf1.wordform_id "
						+ "   AND awf1.lemma_id = awf2.lemma_id "
						+ "   AND awf2.wordform_id = wf2.wordform_id "
						+ "   AND wf1.wordform_lowercase = ?";
			} else {
				fullQuery = "SELECT DISTINCT LCASE(wf2.wordform) " + "  FROM wordforms wf1, "
						+ "       wordforms wf2, " + "       analyzed_wordforms awf1, "
						+ "       analyzed_wordforms awf2, " + "       ne_variant_relations nevr "
						+ "WHERE wf1.wordform_lowercase = ? "
						+ "   AND awf1.wordform_id = wf1.wordform_id "
						+ "   AND awf1.lemma_id = nevr.first_lemma_id "
						+ "   AND nevr.second_lemma_id = awf2.lemma_id "
						+ "   AND awf2.wordform_id = wf2.wordform_id " + "UNION "
						+ "SELECT DISTINCT LCASE(wf2.wordform) " + "  FROM wordforms wf1, "
						+ "       wordforms wf2, " + "       analyzed_wordforms awf1, "
						+ "       analyzed_wordforms awf2 " + "WHERE wf1.wordform_lowercase = ? "
						+ "   AND awf1.wordform_id = wf1.wordform_id "
						+ "   AND awf1.lemma_id = awf2.lemma_id "
						+ "   AND awf2.wordform_id = wf2.wordform_id";
			}
			PreparedStatement stmt = conn.prepareStatement(fullQuery);
			try {
				stmt.setString(1, original.toLowerCase());
				stmt.setString(2, original.toLowerCase());
				if (newQuery)
					stmt.setString(3, original.toLowerCase());
				ResultSet rs = stmt.executeQuery();
				while (rs.next()) {
					// FIXME: suggesties met leestekens (behalve punt) weglaten, foutjes in DB!
					String s = rs.getString(1);
					if (s.equals(Utilities.removePunctuation(s)))
						sugg.addSuggestion("named entity", s);
				}
			} finally {
				stmt.close();
			}
			// System.err.println("Got 'em!");
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public static void main(String[] args) throws ClassNotFoundException, SQLException {
		// Register the JDBC driver for MySQL.
		String url = "jdbc:mysql://impactdb.inl.loc:3306/NE_Lexicon_v2_3_dev?useUnicode=true&characterEncoding=utf8&autoReconnect=true";
		Class.forName("com.mysql.jdbc.Driver");
		Connection conn = DriverManager.getConnection(url, "impact", "impact");
		try {
			Suggester dbs = new NEDatabaseSuggester(conn);
			String[] w = new String[] { "leiden", "rotterdam", "jansen" };
			for (String word : w) {
				System.out.println(word + ": " + dbs.suggest(word));
			}
		} finally {
			conn.close();
		}
	}
}
