/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Reads/writes a type/version file for a directory, to indicate the version of the directory's
 * contents.
 */
public class VersionFile {

	/**
	 * Read version file from directory
	 *
	 * @param dir
	 *            the directory containing the version file
	 * @return the VersionFile object
	 * @throws FileNotFoundException
	 */
	public static VersionFile read(File dir) throws FileNotFoundException {
		VersionFile f = new VersionFile(dir);
		if (!f.exists())
			throw new FileNotFoundException();
		f.read();
		return f;
	}

	/**
	 * Read version file from directory
	 *
	 * @param dir
	 *            the directory containing the version file
	 * @return the VersionFile object
	 */
	public static VersionFile read(File dir, String defaultType, String defaultVersion) {
		VersionFile f = new VersionFile(dir);
		if (f.exists()) {
			f.read();
		} else {
			f.setType(defaultType);
			f.setVersion(defaultVersion);
		}
		return f;
	}

	/**
	 * Write version file to directory
	 *
	 * @param dir
	 *            the directory to write the version file to
	 * @return the VersionFile object
	 */
	public static VersionFile write(File dir, String type, String version) {
		VersionFile f = new VersionFile(dir);
		f.setType(type);
		f.setVersion(version);
		f.write();
		return f;
	}

	/**
	 * Check type of version file
	 *
	 * @param dir
	 *            the directory containing the version file
	 * @param type
	 *            the type to check for
	 * @return true if the type matches, false if not
	 */
	public static boolean isType(File dir, String type) {
		VersionFile f = new VersionFile(dir);
		f.read();
		return f.getType().equals(type);
	}

	/**
	 * Check type and version of version file
	 *
	 * @param dir
	 *            the directory containing the version file
	 * @param type
	 *            the type to check for
	 * @param version
	 *            the version to check for
	 * @return true if both match, false if not
	 */
	public static boolean isTypeVersion(File dir, String type, String version) {
		VersionFile f = new VersionFile(dir);
		f.read();
		return f.getType().equals(type) && f.getVersion().equals(version);
	}

	private File file;

	private String type;

	private String version;

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public VersionFile(File dir) {
		file = new File(dir, "version.dat");
	}

	public boolean exists() {
		return file.exists();
	}

	public void read() {
		try {
			if (!file.exists())
				throw new RuntimeException("Version file not found: " + file);
			BufferedReader r = FileUtil.openForReading(file);
			try {
				String[] info = r.readLine().trim().split("\\|\\|");
				type = info[0];
				if (info.length > 1)
					version = info[1];
			} finally {
				r.close();
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void write() {
		PrintWriter w = FileUtil.openForWriting(file);
		try {
			w.write(type + "||" + version + "\n");
		} finally {
			w.close();
		}
	}

	@Override
	public String toString() {
		return "type = " + type + ", version = " + version;
	}

	public static String report(File indexDir) {
		VersionFile vf = new VersionFile(indexDir);
		if (!vf.exists())
			return "no version file found";
		vf.read();
		return vf.toString();
	}

}
