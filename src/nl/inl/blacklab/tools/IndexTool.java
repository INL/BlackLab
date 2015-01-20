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
package nl.inl.blacklab.tools;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import nl.inl.blacklab.index.DocIndexer;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.indexers.DocIndexerAlto;
import nl.inl.blacklab.indexers.DocIndexerFolia;
import nl.inl.blacklab.indexers.DocIndexerPageXml;
import nl.inl.blacklab.indexers.DocIndexerTei;
import nl.inl.blacklab.indexers.DocIndexerXmlSketch;
import nl.inl.blacklab.search.Searcher;
import nl.inl.util.LogUtil;
import nl.inl.util.LuceneUtil;
import nl.inl.util.PropertiesUtil;

import org.apache.lucene.index.CorruptIndexException;

/**
 * The indexer class and main program for the ANW corpus.
 */
public class IndexTool {
	/** Predefined input formats */
	static Map<String, Class<? extends DocIndexer>> formats = new TreeMap<String, Class<? extends DocIndexer>>();

	static {
		formats.put("tei", DocIndexerTei.class);
		formats.put("sketchxml", DocIndexerXmlSketch.class);
		formats.put("alto", DocIndexerAlto.class);
		formats.put("folia", DocIndexerFolia.class);
		formats.put("pagexml", DocIndexerPageXml.class);
	}

	static Map<String, String> indexerParam = new TreeMap<String, String>();

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception {

		// If the current directory contains indexer.properties, read it
		File propFile = new File(".", "indexer.properties");
		if (propFile.canRead())
			readParametersFromPropertiesFile(propFile);

		// Parse command line
		int maxDocsToIndex = 0;
		File indexDir = null, inputDir = null;
		String glob = "*";
		String fileFormat = null;
		boolean createNewIndex = false;
		String command = "";
		Set<String> commands = new HashSet<String>(Arrays.asList("add", "create", "delete"));
		boolean addingFiles = true;
		String deleteQuery = null;
		for (int i = 0; i < args.length; i++) {
			String arg = args[i].trim();
			if (arg.startsWith("---")) {
				String name = arg.substring(3);
				if (i + 1 == args.length) {
					System.err.println("Passing parameter to indexer: argument needed!");
					usage();
					return;
				}
				i++;
				String value = args[i];
				indexerParam.put(name, value);
			} else if (arg.startsWith("--")) {
				String name = arg.substring(2);
				if (name.equals("maxdocs")) {
					if (i + 1 == args.length) {
						System.err.println("-maxdocs option needs argument");
						usage();
						return;
					}
					try {
						maxDocsToIndex = Integer.parseInt(args[i + 1]);
						i++;
					} catch (NumberFormatException e) {
						System.err.println("--maxdocs option needs integer argument");
						usage();
						return;
					}
				} else if (name.equals("create")) {
					System.err.println("Option --create is deprecated; use create command (--help for details)");
					createNewIndex = true;
				} else if (name.equals("indexparam")) {
					if (i + 1 == args.length) {
						System.err.println("--indexparam option needs argument");
						usage();
						return;
					}
					propFile = new File(args[i + 1]);
					if (!propFile.canRead()) {
						System.err.println("Cannot read " + propFile);
						usage();
						return;
					}
					readParametersFromPropertiesFile(propFile);
					i++;
				} else if (name.equals("help")) {
					usage();
					return;
				} else {
					System.err.println("Unknown option --" + name);
					usage();
					return;
				}
			} else {
				if (command.length() == 0 && commands.contains(arg)) {
					command = arg;
					addingFiles = command.equals("add") || command.equals("create");
				} else if (indexDir == null) {
					indexDir = new File(arg);
				} else if (addingFiles && inputDir == null) {
					if (arg.startsWith("\"") && arg.endsWith("\"")) {
						// Trim off extra quotes needed to pass file glob to
						// Windows JVM.
						arg = arg.substring(1, arg.length() - 1);
					}
					if (arg.contains("*") || arg.contains("?") || new File(arg).isFile()) {
						// Contains file glob. Separate the two components.
						int n = arg.lastIndexOf('/', arg.length() - 2);
						if (n < 0)
							n = arg.lastIndexOf('\\', arg.length() - 2);
						if (n < 0) {
							glob = arg;
							inputDir = new File(".");
						} else {
							glob = arg.substring(n + 1);
							inputDir = new File(arg.substring(0, n));
						}
					} else {
						inputDir = new File(arg);
					}
				} else if (addingFiles && fileFormat == null) {
					fileFormat = arg;
				} else if (command.equals("delete") && deleteQuery == null) {
					deleteQuery = arg;
				} else {
					System.err.println("Too many arguments!");
					usage();
					return;
				}
			}
		}
		if (indexDir == null) {
			System.err.println("No index dir given.");
			usage();
			return;
		}

		// Check the command
		if (command.length() == 0) {
			System.err.println("No command specified; assuming \"add\" (--help for details)");
			command = "add";
		}
		if (command.equals("delete")) {
			commandDelete(indexDir, deleteQuery);
			return;
		}
		if (command.equals("create"))
			createNewIndex = true;

		// We're adding files. Do we have an input dir/file and file format name?
		if (inputDir == null) {
			System.err.println("No input dir given.");
			usage();
			return;
		}
		if (fileFormat == null) {
			System.err.println("No DocIndexer class name given.");
			usage();
			return;
		}

		// Init log4j
		LogUtil.initLog4jIfNotAlready();

		propFile = findFile("indexer.properties", indexDir, inputDir);
		if (propFile != null && propFile.canRead())
			readParametersFromPropertiesFile(propFile);

		File indexTemplateFile = null;
		if (createNewIndex) {
			indexTemplateFile = findFile("indextemplate.json", indexDir, inputDir);
		}

		String op = createNewIndex ? "Creating new" : "Appending to";
		String strGlob = File.separator;
		if (glob != null && glob.length() > 0 && !glob.equals("*")) {
			strGlob += glob;
		}
		System.out.println(op + " index in " + indexDir + File.separator + " from " + inputDir + strGlob + " ("
				+ fileFormat + ")");
		if (indexerParam.size() > 0) {
			System.out.println("Indexer parameters:");
			for (Map.Entry<String,String> e: indexerParam.entrySet()) {
				System.out.println("  " + e.getKey() + ": " + e.getValue());
			}
		}

		// Determine DocIndexer class to use
		Class<? extends DocIndexer> docIndexerClass;
		if (fileFormat.equals("teip4")) {
			System.err.println("'teip4' is deprecated, use 'tei' for either TEI P4 or P5.");
			fileFormat = "tei";
		}
		if (formats.containsKey(fileFormat.toLowerCase())) {
			// Predefined format.
			docIndexerClass = formats.get(fileFormat.toLowerCase());
		} else {
			try {
				// Is it a fully qualified class name?
				docIndexerClass = (Class<? extends DocIndexer>) Class.forName(fileFormat);
			} catch (Exception e1) {
				try {
					// Is it relative to the indexers package?
					docIndexerClass = (Class<? extends DocIndexer>) Class
							.forName("nl.inl.blacklab.indexers." + fileFormat);
				} catch (Exception e) {
					System.err.println("DocIndexer class " + fileFormat + " not found.");
					usage();
					return;
				}
			}
		}

		// Create the indexer and index the files
		if (!createNewIndex || indexTemplateFile == null || !indexTemplateFile.canRead()) {
			indexTemplateFile = null;
		}/* else {
			indexer.setNewIndexMetadataTemplate(indexTemplateFile);
		}*/
		Indexer indexer = new Indexer(indexDir, createNewIndex, docIndexerClass, indexTemplateFile);
		indexer.setIndexerParam(indexerParam);
		if (maxDocsToIndex > 0)
			indexer.setMaxNumberOfDocsToIndex(maxDocsToIndex);
		try {
			if (glob.contains("*") || glob.contains("?")) {
				// Real wildcard glob
				indexer.index(inputDir, glob);
			} else {
				// Single file; just index that, with no glob specified
				// (so if it's an archive, we'll just process all .xml files within)
				indexer.index(new File(inputDir, glob));
			}
		} catch (Exception e) {
			System.err.println("An error occurred, aborting indexing (changes will be rolled back). Error details follow.");
			e.printStackTrace();
			indexer.rollback();
		} finally {
			// Close the index.
			indexer.close();
		}
	}

	private static File findFile(String fileName, File indexDir, File inputDir) {
		// If the input or index directory or the parent of the index directory
		// contains indexer.properties, read it
		File propFile = new File(indexDir, fileName);
		if (propFile.canRead())
			return propFile;
		propFile = new File(indexDir.getParentFile(), fileName);
		if (propFile.canRead())
			return propFile;
		if (inputDir.isDirectory()) {
			propFile = new File(inputDir, fileName);
			if (propFile.canRead())
				return propFile;
		}
		return null;
	}

	private static void readParametersFromPropertiesFile(File propFile) {
		Properties p = PropertiesUtil.readFromFile(propFile);
		for (Map.Entry<Object, Object> e: p.entrySet()) {
			indexerParam.put(e.getKey().toString(), e.getValue().toString());
		}
	}

	private static void commandDelete(File indexDir, String deleteQuery) throws IOException,
			org.apache.lucene.queryparser.classic.ParseException, CorruptIndexException {
		if (deleteQuery == null) {
			System.err.println("No delete query given.");
			usage();
			return;
		}
		Searcher searcher = Searcher.openForWriting(indexDir, false);
		try {
			System.out.println("Doing delete: " + deleteQuery);
			searcher.delete(LuceneUtil.parseLuceneQuery(deleteQuery, searcher.getAnalyzer(), null));
		} finally {
			searcher.close();
		}
	}

	private static void usage() {
		System.out
				.println("Usage:\n"
						+ "  IndexTool {add|create} [options] <indexdir> <inputdir> <format>\n"
						+ "  IndexTool delete <indexdir> <filterQuery>\n"
						+ "\n"
						+ "Options:\n"
						+ "  --maxdocs <n>        Stop after indexing <n> documents\n"
						+ "  --indexparam <file>  Read properties file with parameters for DocIndexer\n"
						+ "                       (NOTE: even without this option, if the current\n"
						+ "                        directory, the input or index directory (or its parent)\n"
						+ "                        contain a file named indexer.properties, these are passed\n"
						+ "                        to the indexer)\n"
						+ "  ---<name> <value>    Pass parameter to DocIndexer class\n"
						+ "\n"
						+ "Valid formats:");
		for (String format: formats.keySet()) {
			System.out.println("  " + format);
		}
		System.out.println("  (or specify your own DocIndexer class)");
	}
}
