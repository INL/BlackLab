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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
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
			List<String> result = new ArrayList<String>();
			BufferedReader in = openForReading(inputFile, encoding);
			try {
				String line;
				while ((line = in.readLine()) != null) {
					result.add(line.trim());
				}
			} finally {
				in.close();
			}
			return result;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Replaces illegal characters (/, \, :, *, ?, ", <, > and |) in a filename with an underscore.
	 *
	 * @param filename
	 *            the filename to sanitize
	 * @return the sanitized filename
	 */
	public static String sanitizeFilename(String filename) {
		return FileUtil.sanitizeFilename(filename, "_");
	}

	/**
	 * Replaces illegal characters (/, \, :, *, ?, ", <, > and |) in a filename with the specified
	 * character.
	 *
	 * @param filename
	 *            the filename to sanitize
	 * @param invalidChar
	 *            the replacement character
	 * @return the sanitized filename
	 */
	public static String sanitizeFilename(String filename, String invalidChar) {
		return filename.replaceAll("[\t\r\n\\/\\\\:\\*\\?\"<>\\|]", invalidChar);
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
	 * Perform an operation on all files in a tree
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
		for (File f : root.listFiles()) {
			if (f.isFile())
				task.process(f);
			else if (f.isDirectory()) {
				if (!f.getName().matches("\\.\\.?")) // skip . and ..
					processTree(f, task);
			}
		}
	}

	/**
	 * Perform an operation on some files in a tree
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
		for (File file : dir.listFiles()) {
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
	 * Read an entire file into a String
	 * @param file the file to read
	 * @return the file's contents
	 */
	public static String readFile(File file) {
		return StringUtil.join(readLines(file), "\n");
	}
}
