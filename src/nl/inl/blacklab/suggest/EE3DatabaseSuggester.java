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
import java.util.HashMap;
import java.util.Map;

/**
 * Suggester using the EE3 database to suggest word form variants given a word form (currently gives
 * not so useful (too many) results; query may need to be refined)
 */
public class EE3DatabaseSuggester extends Suggester {
	private Connection conn;

	public EE3DatabaseSuggester(Connection connection) {
		conn = connection;
	}

	/*
	 *
	 * -- Simple SQL queries:
	 *
	 * -- Find lemmas from word form SELECT DISTINCT modern_lemma FROM lemmata,
	 * simple_analyzed_wordforms, wordforms WHERE lemmata.lemma_id =
	 * simple_analyzed_wordforms.lemma_id AND simple_analyzed_wordforms.wordform_id =
	 * wordforms.wordform_id AND wordforms.wordform = 'appel';
	 *
	 * -- Find word forms from lemma SELECT DISTINCT wordform FROM lemmata,
	 * simple_analyzed_wordforms, wordforms WHERE lemmata.lemma_id =
	 * simple_analyzed_wordforms.lemma_id AND simple_analyzed_wordforms.wordform_id =
	 * wordforms.wordform_id AND lemmata.modern_lemma = 'appel';
	 */

	// Provide translation from POS code to human-readable text
	static Map<String, String> typeHeader = new HashMap<String, String>();

	static {
		typeHeader.put("NOU", "noun");
		typeHeader.put("VRB", "verb");
		typeHeader.put("ADJ", "adjective");
		typeHeader.put("ADV", "adverb");
		typeHeader.put("INT", "interjection");
		typeHeader.put("NUM", "numeral");
		typeHeader.put("PRN", "pronoun");
		typeHeader.put("CON", "conjunction");
		typeHeader.put("PART", "incomplete word");
		typeHeader.put("MWE", "multiword expression");
		typeHeader.put("RES", "residual");
		typeHeader.put("ADP", "preposition");
		typeHeader.put("ART", "article");
		typeHeader.put("X", "residual");
		typeHeader.put("A", "adjective");
		typeHeader.put("PART_MWE", "part of multiword expression");
		typeHeader.put("V", "verb");
		typeHeader.put("NP", "noun phrase");
		typeHeader.put("?", "unknown");
		typeHeader.put("", "unknown");

	}

	@Override
	public void addSuggestions(String original, Suggestions sugg) {
		try {
			// System.err.println("Getting suggestions for " + original);
			// Full query, find variants from word form (via lemma)
			final String fullQuery = "select distinct w.wordform, l.lemma_part_of_speech, l.modern_lemma from "
					+ "lemmata l, simple_analyzed_wordforms a, wordforms w, simple_analyzed_wordforms a2, wordforms w2 "
					+ "where a.wordform_id = w.wordform_id and l.lemma_id = a.lemma_id and "
					+ "l.lemma_id  = a2.lemma_id and a2.wordform_id = w2.wordform_id and w2.wordform = ? and w.wordform != ?";
			PreparedStatement stmt = conn.prepareStatement(fullQuery);
			try {
				stmt.setString(1, original.toLowerCase());
				stmt.setString(2, original.toLowerCase());
				ResultSet rs = stmt.executeQuery();
				while (rs.next()) {
					String pos = rs.getString(2);
					String lemma = rs.getString(3);
					String[] posParts = pos.split("\\s+");
					StringBuilder resultPos = new StringBuilder();
					for (String posPart : posParts) {
						if (resultPos.length() > 0)
							resultPos.append("/");
						if (typeHeader.containsKey(posPart))
							resultPos.append(typeHeader.get(posPart));
						else
							resultPos.append(posPart);
					}
					sugg.addSuggestion(lemma + " (" + resultPos + ")", rs.getString(1));
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
		String url = "jdbc:mysql://impactdb.inl.loc:3306/EE3?useUnicode=true&characterEncoding=utf8&autoReconnect=true";
		Class.forName("com.mysql.jdbc.Driver");
		Connection conn = DriverManager.getConnection(url, "impact", "impact");
		try {
			Suggester dbs = new EE3DatabaseSuggester(conn);
			String[] w = new String[] { "de", "appel", "peer" };
			for (String word : w) {
				System.out.println(word + ": " + dbs.suggest(word));
			}
		} finally {
			conn.close();
		}
	}
}
