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
package nl.inl.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities for working with files
 */
public class FileUtil {
	/**
	 * The default encoding for opening files.
	 */
	private static String defaultEncoding = "utf-8";

	/**
	 * Sorts File objects alphabetically, case-insensitively,
	 * subdirectories first. Used by listFilesSorted(). */
	final public static Comparator<File> LIST_FILES_COMPARATOR = new Comparator<File>() {
		@Override
		public int compare(File a, File b) {
			int ad = a.isDirectory() ? 0 : 1;
			int bd = b.isDirectory() ? 0 : 1;
			return ad != bd ? (ad - bd) : a.getName().compareToIgnoreCase(b.getName());
		}
	};

	/**
	 * Get the default encoding for opening files.
	 *
	 * @return the default encoding
	 */
	public static String getDefaultEncoding() {
		return defaultEncoding;
	}

	/**
	 * Set the default encoding for opening files.
	 *
	 * @param defaultEncoding
	 *            the default encoding
	 */
	public static void setDefaultEncoding(String defaultEncoding) {
		FileUtil.defaultEncoding = defaultEncoding;
	}

	/**
	 * Opens a file for writing in the default encoding.
	 *
	 * Wraps the Writer in a BufferedWriter and PrintWriter for efficient and convenient access.
	 *
	 * @param file
	 *            the file to open
	 * @return write interface into the file
	 */
	public static PrintWriter openForWriting(File file) {
		return openForWriting(file, defaultEncoding);
	}

	/**
	 * Opens a file for writing.
	 *
	 * Wraps the Writer in a BufferedWriter and PrintWriter for efficient and convenient access.
	 *
	 * @param file
	 *            the file to open
	 * @param encoding
	 *            the encoding to use, e.g. "utf-8"
	 * @return write interface into the file
	 */
	public static PrintWriter openForWriting(File file, String encoding) {
		try {
			return new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(
					file), encoding)));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Opens a file for appending.
	 *
	 * Wraps the Writer in a BufferedWriter and PrintWriter for efficient and convenient access.
	 *
	 * @param file
	 *            the file to open
	 * @return write interface into the file
	 */
	public static PrintWriter openForAppend(File file) {
		return openForAppend(file, defaultEncoding);
	}

	/**
	 * Opens a file for appending.
	 *
	 * Wraps the Writer in a BufferedWriter and PrintWriter for efficient and convenient access.
	 *
	 * @param file
	 *            the file to open
	 * @param encoding
	 *            the encoding to use, e.g. "utf-8"
	 * @return write interface into the file
	 */
	public static PrintWriter openForAppend(File file, String encoding) {
		try {
			return new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(
					file, true), encoding)));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Opens a file for reading, with the default encoding.
	 *
	 * Wraps the Reader in a BufferedReader for efficient and convenient access.
	 *
	 * @param file
	 *            the file to open
	 * @return read interface into the file
	 */
	public static BufferedReader openForReading(File file) {
		return openForReading(file, defaultEncoding);
	}

	/**
	 * Opens a file for reading, with the default encoding.
	 *
	 * Wraps the Reader in a BufferedReader for efficient and convenient access.
	 *
	 * @param file
	 *            the file to open
	 * @param encoding
	 *            the encoding to use, e.g. "utf-8"
	 * @return read interface into the file
	 */
	public static BufferedReader openForReading(File file, String encoding) {
		try {
			return new BufferedReader(new InputStreamReader(new FileInputStream(file), encoding));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// TODO: add writeLines()

	/**
	 * Read a file into a list of lines
	 *
	 * @param inputFile
	 *            the file to read
	 * @return list of lines
	 */
	public static List<String> readLines(File inputFile) {
		return readLines(inputFile, defaultEncoding);
	}

	/**
	 * Read a file into a list of lines
	 *
	 * @param inputFile
	 *            the file to read
	 * @param encoding
	 *            the encoding to use, e.g. "utf-8"
	 * @return list of lines
	 */
	public static List<String> readLines(File inputFile, String encoding) {
		try {
			List<String> result = new ArrayList<>();
			try (BufferedReader in = openForReading(inputFile, encoding)) {
				String line;
				while ((line = in.readLine()) != null) {
					result.add(line.trim());
				}
			}
			return result;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Replaces illegal characters (/, \, :, *, ?, ", <, > and |) in a filename with an underscore.
	 *
	 * NOTE: this is not intended for security use, just to make sure filenames aren't
	 * invalid! For security with untrusted input, allow only a conservative set of characters
	 * (i.e. [a-zA-Z0-9_\-\.], and forbid "." and "..").
	 *
	 * @param filename
	 *            the filename to sanitize
	 * @return the sanitized filename
	 */
	public static String sanitizeFilename(String filename) {
		return FileUtil.sanitizeFilename(filename, "_");
	}

	/**
	 * Replaces illegal characters (\t, \r, \n, /, \, :, *, ?, ", <, > and |) in a filename with the specified
	 * character.
	 *
	 * NOTE: this is not intended for security use, just to make sure filenames aren't
	 * invalid! For security with untrusted input, allow only a conservative set of characters
	 * (i.e. [a-zA-Z0-9_\-\.], and forbid "." and "..").
	 *
	 * @param filename
	 *            the filename to sanitize
	 * @param invalidChar
	 *            the replacement character
	 * @return the sanitized filename
	 */
	public static String sanitizeFilename(String filename, String invalidChar) {
		return filename.replaceAll("[\t\r\n/\\\\:\\*\\?\"<>\\|]", invalidChar);
	}

	/**
	 * Get the size of a file or a directory tree
	 *
	 * @param root
	 * @return size of the file or directory tree
	 */
	public static long getTreeSize(File root) {
		long size = 0;
		if (root.isFile())
			size = root.length();
		else {
			for (File f : root.listFiles()) {
				size += getTreeSize(f);
			}
		}
		return size;
	}

	/**
	 * A task to execute on a file. Used by processTree().
	 */
	public static abstract class FileTask {
		/**
		 * Execute the task on this file.
		 *
		 * @param f
		 *            the file to process
		 */
		public abstract void process(File f);
	}

	/**
	 * Perform an operation on all files in a tree.
	 *
	 * Sorts the files alphabetically, with directories first,
	 * so they are always processed in the same order.
	 *
	 * @param root
	 *            the directory to start in (all subdirs are processed)
	 * @param task
	 *            the task to execute for every file
	 */
	public static void processTree(File root, FileTask task) {
		if (!root.isDirectory())
			throw new RuntimeException("FileUtil.processTree: must be called with a directory! "
					+ root);
		for (File f : listFilesSorted(root)) {
			if (f.isFile())
				task.process(f);
			else if (f.isDirectory()) {
				processTree(f, task);
			}
		}
	}

	/**
	 * Perform an operation on some files in a tree.
	 *
	 * Sorts the files alphabetically, with directories first,
	 * so they are always processed in the same order.
	 *
	 * @param dir
	 *            the directory to start in
	 * @param glob
	 *            which files to process (e.g. "*.xml")
	 * @param recurseSubdirs
	 *            whether or not to process subdirectories
	 * @param task
	 *            the task to execute for every file
	 */
	public void processTree(File dir, String glob, boolean recurseSubdirs, FileTask task) {
		Pattern pattGlob = Pattern.compile(FileUtil.globToRegex(glob));
		for (File file : listFilesSorted(dir)) {
			if (file.isDirectory()) {
				// Process subdir?
				if (recurseSubdirs)
					processTree(file, glob, recurseSubdirs, task);
			} else if (file.isFile()) {
				// Regular file; does it match our glob expression?
				Matcher m = pattGlob.matcher(file.getName());
				if (m.matches()) {
					task.process(file);
				}
			}
		}
	}

	/**
	 * Returns files in a directory, sorted.
	 *
	 * Sorts alphabetically, case-insensitively, and puts subdirectories first.
	 *
	 * @param dir the directory to list files in
	 * @return the sorted array of files
	 */
	public static File[] listFilesSorted(File dir) {
		File[] files = dir.listFiles();
		Arrays.sort(files, LIST_FILES_COMPARATOR);
		return files;
	}

	/**
	 * Convert a simple file glob expression (containing * and/or ?) to a regular expression.
	 *
	 * Example: "log*.txt" becomes "^log.*\\.txt$"
	 *
	 * @param glob
	 *            the file glob expression
	 * @return the regular expression
	 */
	public static String globToRegex(String glob) {
		glob = glob.replaceAll("\\^", "\\\\\\^");
		glob = glob.replaceAll("\\$", "\\\\\\$");
		glob = glob.replaceAll("\\.", "\\\\.");
		glob = glob.replaceAll("\\\\", "\\\\");
		glob = glob.replaceAll("\\*", ".*");
		glob = glob.replaceAll("\\?", ".");
		return "^" + glob + "$";
	}

	/**
	 * Find a file on the classpath.
	 *
	 * @param fn name of the file we're looking for
	 * @return the file if found, null otherwise
	 */
	public static File findOnClasspath(String fn) {
		String sep = System.getProperty("path.separator");
		for (String part: System.getProperty("java.class.path").split("\\" + sep)) {
			File f = new File(part);
			File dir = f.isFile() ? f.getParentFile() : f;
			File ourFile = new File(dir, fn);
			if (ourFile.exists())
				return ourFile;
		}
		return null;
	}

	/**
	 * Detect the Unicode encoding of an input stream by looking for a BOM at the current position.
	 *
	 * If no BOM is found, the specified default encoding is returned and
	 * the position of the stream is unchanged.
	 *
	 * If a BOM is found, it is interpreted and the corresponding encoding
	 * is returned. The stream will remain positioned after the BOM.
	 *
	 * This method uses InputStream.mark(), which must be supported by the given stream
	 * (BufferedInputStream supports this).
	 *
	 * Only works for UTF-8 and UTF16 (LE/BE) for now.
	 *
	 * @param inputStream the input stream
	 * @param useDefaultEncoding encoding to return if no BOM found
	 * @return the encoding
	 * @throws IOException
	 */
	public static String detectBomEncoding(BufferedInputStream inputStream, String useDefaultEncoding) throws IOException {
		String encoding = "";

		if (!inputStream.markSupported()) {
			throw new RuntimeException("Need support for inputStream.mark()!");
		}

		inputStream.mark(4); // mark this position so we can reset() later
		int firstByte  = inputStream.read();
		int secondByte = inputStream.read();
		if(firstByte == 0xFF && secondByte == 0xFE) {
			// BOM voor UTF-16LE
			encoding = "utf-16le";
			// We staan nu na de BOM, dus ok
		} else if(firstByte == 0xFE && secondByte == 0xFF) {
			// BOM voor UTF-16 LE
			encoding = "utf-16be";
			// We staan nu na de BOM, dus ok
		} else if(firstByte == 0xEF && secondByte == 0xBB) {
			int thirdByte = inputStream.read();
			if(thirdByte == 0xBF) {
				// BOM voor UTF-8
				encoding = "utf-8";
			}
			// We staan nu na de BOM, dus ok
		} else {
			// Geen BOM maar wel 2 bytes gelezen; "rewind"
			inputStream.reset();
			encoding = useDefaultEncoding; // (we assume, as we haven't found a BOM)
		}
		return encoding;
	}

	/**
	 * Read an entire file into a String
	 * @param file the file to read
	 * @return the file's contents
	 */
	public static String readFile(File file) {
		return StringUtil.join(readLines(file), "\n");
	}

	/**
	 * Write a String to a file.
	 * @param file the file to write
	 * @param data what to write to the file
	 */
	public static void writeFile(File file, String data) {
		try (PrintWriter out = openForWriting(file)) {
			out.print(data);
		}
	}

	/**
	 * Add a parenthesized number to a file name to get a file name that doesn't exist yet.
	 *
	 * @param file the file that exists already
	 * @return a file with a number added that doesn't exist yet
	 */
	public static File addNumberToExistingFileName(File file) {
		File parentFile = file.getParentFile();
		String name = file.getName();
		int number = 2;
		File newFile;
		do {
			newFile = new File(parentFile, name + " (" + number + ")");
			number++;
		} while(newFile.exists());
		return newFile;
	}
}
