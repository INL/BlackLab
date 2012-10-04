/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.indexers.alto;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nl.inl.util.FileUtil;

public class AltoUtils {
	/** Pattern to find the values of the CONTENT attributes */
	static Pattern pattContentAttrs = Pattern.compile("\\bCONTENT=\\s*\"([^'\"]+)\"");

	static File metadataFile;

	/** Document titles by DPO num */
	static Map<String, String> titles;
	static Map<String, String> dates;
	static Map<String, String> authors;

	/**
	 * Pluck words from the CONTENT attributes in an ALTO snippet.
	 *
	 * @param altoXml
	 *            the ALTO "XML" snippet. Quotes because this is not necessarily well-formed,
	 *            because it's an arbitrary sequence of words from the content; in-between may be
	 *            start-line tags without end-line tags, for example.
	 * @return just the values of the CONTENT attributes with spaces in between.
	 */
	static public String getFromContentAttributes(String altoXml) {
		Matcher m = pattContentAttrs.matcher(altoXml);
		StringBuilder b = new StringBuilder();
		while (m.find()) {
			if (b.length() > 0)
				b.append(" ");
			b.append(m.group(1));
		}
		return b.toString();
	}

	static public void setMetadataFile(File metadataFile) {
		AltoUtils.metadataFile = metadataFile;
	}

	static void readMetadata() {
		titles = new HashMap<String, String>();
		dates = new HashMap<String, String>();
		authors = new HashMap<String, String>();
		// File metadataFile = new File("c:\\temp\\dpo_metadata.txt");
		BufferedReader r = FileUtil.openForReading(metadataFile);
		String l;
		while (true) {
			try {
				l = r.readLine();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			if (l == null)
				break;
			if (l.length() == 0)
				continue;
			String[] fields = l.split("\t");
			titles.put(fields[0].trim(), fields[1].trim());
			dates.put(fields[0].trim(), fields[3].trim());
			authors.put(fields[0].trim(), fields[4].trim());
		}
	}

	static public String getTitleFromDpo(String dpo) {
		if (titles == null)
			readMetadata();

		String t = titles.get(dpo);
		if (t == null)
			t = "?";
		return t;
	}

	static public String getDateFromDpo(String dpo) {
		if (titles == null)
			readMetadata();

		String t = dates.get(dpo);
		if (t == null)
			t = "?";
		return t;
	}

	static public String getAuthorFromDpo(String dpo) {
		if (titles == null)
			readMetadata();

		String t = authors.get(dpo);
		if (t == null)
			t = "?";
		return t;
	}

}
