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
import java.util.Set;
import java.util.TreeMap;

import nl.inl.blacklab.index.DocIndexer;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.indexers.DocIndexerAlto;
import nl.inl.blacklab.indexers.DocIndexerFolia;
import nl.inl.blacklab.indexers.DocIndexerPageXml;
import nl.inl.blacklab.indexers.DocIndexerTeiP4;
import nl.inl.blacklab.indexers.DocIndexerXmlSketch;
import nl.inl.util.LogUtil;
import nl.inl.util.LuceneUtil;

import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.queryParser.ParseException;

/**
 * The indexer class and main program for the ANW corpus.
 */
public class IndexTool {
	/** Predefined input formats */
	static Map<String, Class<? extends DocIndexer>> formats = new TreeMap<String, Class<? extends DocIndexer>>();

	static {
		formats.put("teip4", DocIndexerTeiP4.class);
		formats.put("sketchxml", DocIndexerXmlSketch.class);
		formats.put("alto", DocIndexerAlto.class);
		formats.put("folia", DocIndexerFolia.class);
		formats.put("pagexml", DocIndexerPageXml.class);
	}

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception {

		int maxDocs = 0;
		File indexDir = null, inputDir = null;
		String glob = "*";
		String docIndexerName = null;
		boolean createNewIndex = false;
		String command = "";
		Set<String> commands = new HashSet<String>(Arrays.asList("add", "create", "delete"));
		Map<String, String> indexerParam = new TreeMap<String, String>();
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
				if (name.equals("maxDocs")) {
					if (i + 1 == args.length) {
						System.err.println("-maxDocs option needs argument");
						usage();
						return;
					}
					try {
						maxDocs = Integer.parseInt(args[i + 1]);
						i++;
					} catch (NumberFormatException e) {
						System.err.println("-maxDocs option needs integer argument");
						usage();
						return;
					}
				} else if (name.equals("create")) {
					System.err.println("Option --create is deprecated; use create command (--help for details)");
					createNewIndex = true;
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
					addingFiles = !command.equals("delete");
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
				} else if (addingFiles && docIndexerName == null) {
					docIndexerName = arg;
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

		if (command.length() == 0) {
			System.err.println("No command specified; assuming \"add\" (--help for details)");
			command = "add";
		}
		if (command.equals("delete")) {
			commandDelete(indexDir, deleteQuery);
			return;
		}
		createNewIndex |= command.equals("create");

		if (inputDir == null) {
			System.err.println("No input dir given.");
			usage();
			return;
		}
		if (docIndexerName == null) {
			System.err.println("No DocIndexer class name given.");
			usage();
			return;
		}
		String op = createNewIndex ? "Creating new" : "Appending to";
		System.out.println(op + " index in " + indexDir + " from " + inputDir + " ("
				+ docIndexerName + ")");

		LogUtil.initLog4jBasic();

		Class<? extends DocIndexer> docIndexerClass;
		if (formats.containsKey(docIndexerName.toLowerCase())) {
			// Predefined format.
			docIndexerClass = formats.get(docIndexerName.toLowerCase());
		} else {
			try {
				// Is it a fully qualified class name?
				docIndexerClass = (Class<? extends DocIndexer>) Class.forName(docIndexerName);
			} catch (Exception e1) {
				try {
					// Is it relative to the indexers package?
					docIndexerClass = (Class<? extends DocIndexer>) Class
							.forName("nl.inl.blacklab.indexers." + docIndexerName);
				} catch (Exception e) {
					System.err.println("DocIndexer class " + docIndexerName + " not found.");
					usage();
					return;
				}
			}
		}

		Indexer indexer = new Indexer(indexDir, createNewIndex, docIndexerClass);
		indexer.setIndexerParam(indexerParam);
		if (maxDocs > 0)
			indexer.setMaxDocs(maxDocs);
		try {
			indexer.index(inputDir, glob, true);
		} catch (Exception e) {
			System.err.println("An error occurred, aborting indexing. Error details follow.");
			e.printStackTrace();
		} finally {
			// Close the index.
			indexer.close();
		}
	}

	private static void commandDelete(File indexDir, String deleteQuery) throws IOException,
			ParseException, CorruptIndexException {
		if (deleteQuery == null) {
			System.err.println("No delete query given.");
			usage();
			return;
		}
		Indexer indexer = new Indexer(indexDir, false, null);
		try {
			System.out.println("Doing delete: " + deleteQuery);
			indexer.delete(LuceneUtil.parseLuceneQuery(deleteQuery, null));
		} finally {
			indexer.close();
		}
	}

	private static void usage() {
		System.out
				.println("Usage:\n"
						+ "  IndexTool {add|create} [options] <indexdir> <inputdir> <format>\n"
						+ "  IndexTool delete <indexdir> <filterQuery>\n"
						+ "\n"
						+ "Options:\n"
						+ "  --maxdocs <n>      Stop after indexing <n> documents\n"
						+ "  ---<name> <value>  Pass parameter to DocIndexer class\n"
						+ "\n"
						+ "Valid formats:");
		for (String format: formats.keySet()) {
			System.out.println("  " + format);
		}
		System.out.println("  (or specify your own DocIndexer class)");
	}
}
