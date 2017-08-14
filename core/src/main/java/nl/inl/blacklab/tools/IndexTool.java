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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import org.apache.lucene.index.CorruptIndexException;

import nl.inl.blacklab.index.DocIndexerFactory;
import nl.inl.blacklab.index.DocumentFormatException;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.DocumentFormats.FormatDesc;
import nl.inl.blacklab.index.DownloadCache;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.search.Searcher;
import nl.inl.util.ExUtil;
import nl.inl.util.FileUtil;
import nl.inl.util.LogUtil;
import nl.inl.util.LuceneUtil;
import nl.inl.util.StringUtil;

/**
 * The indexer class and main program for the ANW corpus.
 */
public class IndexTool {

	static Map<String, String> indexerParam = new TreeMap<>();

	public static void main(String[] args) throws Exception {
		// If the current directory contains indexer.properties, read it
		File propFile = new File(".", "indexer.properties");
		if (propFile.canRead())
			readParametersFromPropertiesFile(propFile);

		// Parse command line
		int maxDocsToIndex = 0;
		File indexDir = null, inputDir = null;
		String glob = "*";
		String docFormat = null;
		boolean createNewIndex = false;
		String command = "";
		Set<String> commands = new HashSet<>(Arrays.asList("add", "create", "delete"));
		boolean addingFiles = true;
		String deleteQuery = null;
		boolean useThreads = false;
		List<File> linkedFileDirs = new ArrayList<>();
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
				switch(name) {
				case "threads":
					useThreads = true;
					break;
				case "linked-file-dir":
                    if (i + 1 == args.length) {
                        System.err.println("--linked-file-dir option needs argument");
                        usage();
                        return;
                    }
				    linkedFileDirs.add(new File(args[i + 1]));
				    i++;
				    break;
				case "maxdocs":
					if (i + 1 == args.length) {
						System.err.println("--maxdocs option needs argument");
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
					break;
				case "create":
					System.err.println("Option --create is deprecated; use create command (--help for details)");
					createNewIndex = true;
					break;
				case "indexparam":
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
					break;
				case "help":
					usage();
					return;
				default: {
					System.err.println("Unknown option --" + name);
					usage();
					return;
				}
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
				} else if (addingFiles && docFormat == null) {
					docFormat = arg;
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
			System.err.println("No command specified; specify 'create' or 'add'. (--help for details)");
			usage();
			return;
			//System.err.println("No command specified; assuming \"add\" (--help for details)");
			//command = "add";
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
		boolean autoDetectFormat = false;
		if (docFormat == null) {
			System.err.println("No DocIndexer class name given; trying to detect it from the index...");
			docFormat = "autodetect format";
			autoDetectFormat = true;
			//usage();
			//return;
		}

		// Init log4j
		LogUtil.setupBasicLoggingConfig();

		File indexDirParent = indexDir.getAbsoluteFile().getParentFile();
		File inputDirParent = inputDir.getAbsoluteFile().getParentFile();
		List<File> dirs = new ArrayList<>(Arrays.asList(new File("."), inputDir, inputDirParent, indexDir));
		if (!dirs.contains(indexDirParent))
			dirs.add(indexDirParent);
		propFile = FileUtil.findFile(dirs, "indexer", Arrays.asList("properties"));
		if (propFile != null && propFile.canRead())
			readParametersFromPropertiesFile(propFile);

		File indexTemplateFile = null;
		if (createNewIndex) {
			indexTemplateFile = FileUtil.findFile(dirs, "indextemplate", Arrays.asList("json", "yaml", "yml"));
		}

		String op = createNewIndex ? "Creating new" : "Appending to";
		String strGlob = File.separator;
		if (glob != null && glob.length() > 0 && !glob.equals("*")) {
			strGlob += glob;
		}
		System.out.println(op + " index in " + indexDir + File.separator + " from " + inputDir + strGlob + " (format "
				+ docFormat + ")");
		if (!indexerParam.isEmpty()) {
			System.out.println("Indexer parameters:");
			for (Map.Entry<String,String> e: indexerParam.entrySet()) {
				System.out.println("  " + e.getKey() + ": " + e.getValue());
			}
		}

		// Make sure BlackLab can find our format configuration files
		// (by default, it will already look in $BLACKLAB_CONFIG_DIR/formats, $HOME/.blacklab/formats
		//  and /etc/blacklab/formats, but we also want it to look in the current dir, the input dir,
		//  and the parent(s) of the input and index dirs)
		List<File> formatDirs = new ArrayList<>(Arrays.asList(new File("."), inputDirParent, inputDir));
		if (!formatDirs.contains(indexDirParent))
		    formatDirs.add(indexDirParent);
		DocumentFormats.registerFormatsInDirs(formatDirs);

		// Determine DocIndexer to use
		DocIndexerFactory docIndexerFactory = null;
		if (!autoDetectFormat) {
			if (docFormat.equals("teip4")) {
				System.err.println("'teip4' is deprecated, use 'tei' for either TEI P4 or P5.");
				docFormat = "tei";
			}
			docIndexerFactory = DocumentFormats.getIndexerFactory(docFormat);
			if (docIndexerFactory == null) {
				System.err.println("Input format " + docFormat + " not found.");
				usage();
				return;
			}
		}

		// Create the indexer and index the files
        DownloadCache.setDownloadAllowed(true); // allow downloading linked (metadata) documents
		if (!createNewIndex || indexTemplateFile == null || !indexTemplateFile.canRead()) {
			indexTemplateFile = null;
		}
		Indexer indexer;
		try {
			indexer = new Indexer(indexDir, createNewIndex, docIndexerFactory, indexTemplateFile);
			if (useThreads)
				indexer.setUseThreads(true);
		} catch (DocumentFormatException e1) {
			if (e1.getMessage().contains("document format")) { // ARGH, UGLY..
				System.err.println("Failed to detect document format. Please specify it on the command line.");
				usage();
				return;
			}
			throw e1;
		}
		if (createNewIndex)
			indexer.getSearcher().getIndexStructure().setDocumentFormat(docFormat);
		indexer.setIndexerParam(indexerParam);
		if (maxDocsToIndex > 0)
			indexer.setMaxNumberOfDocsToIndex(maxDocsToIndex);
        indexer.setLinkedFileDirs(linkedFileDirs);
		try {
			if (glob.contains("*") || glob.contains("?")) {
				// Real wildcard glob
				indexer.index(inputDir, glob);
			} else {
				// Single file.
				indexer.index(new File(inputDir, glob), "*");
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

	private static void readParametersFromPropertiesFile(File propFile) {
		Properties p = readPropertiesFromFile(propFile);
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
		System.err.flush();
		System.out.flush();
		System.out
				.println("Usage:\n"
						+ "  IndexTool {add|create} [options] <indexdir> <inputdir> <format>\n"
						+ "  IndexTool delete <indexdir> <filterQuery>\n"
						+ "\n"
						+ "Options:\n"
						+ "  --maxdocs <n>          Stop after indexing <n> documents\n"
                        + "  --linked-file-dir <d>  Look in directory <d> for linked (e.g. metadata) files\n"
                        + "\n"
                        + "Deprecated options (not needed anymore with .yaml format configs):\n"
						+ "  --indexparam <file>    Read properties file with parameters for DocIndexer\n"
						+ "                         (NOTE: even without this option, if the current\n"
						+ "                         directory, the input or index directory (or its parent)\n"
						+ "                         contain a file named indexer.properties, these are passed\n"
						+ "                         to the indexer)\n"
						+ "  ---<name> <value>      Pass parameter to DocIndexer class\n"
						+ "  ---meta-<name> <value> Add an extra metadata field to documents indexed.\n"
						+ "                         You can also add a property named meta-<name> to your\n"
						+ "                         indexer.properties file. This field is stored untokenized.\n"
						+ "\n"
						+ "Available input format configurations:");
		for (FormatDesc format: DocumentFormats.getSupportedFormats()) {
            String name = format.getName();
		    String displayName = format.getDisplayName();
		    String desc = format.getDescription();
            if (displayName.length() > 0)
                displayName = " (" + displayName + ")";
            if (desc.length() > 0) {
                desc = "\n      " + StringUtil.join(StringUtil.wrap(desc, 75), "\n      ");
            }
			System.out.println("  " + name + displayName + desc);
		}
	}

	/**
	 * Read Properties from the specified file
	 *
	 * @param file
	 *            the file to read
	 * @return the Properties read
	 */
	public static Properties readPropertiesFromFile(File file) {
		try {
			if (!file.isFile()) {
				throw new IllegalArgumentException("Property file " + file.getCanonicalPath()
						+ " does not exist or is not a regular file!");
			}

			try (Reader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), "iso-8859-1"))) {
				Properties properties = new Properties();
				properties.load(in);
				return properties;
			}
		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}
}
