/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.externalstorage;

import java.io.File;

import nl.inl.util.VersionFile;

/**
 * Store string content by id in a directory of compound files with a TOC file. Quickly retrieve
 * (parts of) the string content.
 */
public abstract class ContentStoreDirAbstract extends ContentStore {
	/**
	 * Dir to store the content and TOC
	 */
	protected File dir;

	protected void setStoreType(String type, String version) {
		VersionFile.write(dir, type, version);
	}

	public static VersionFile getStoreTypeVersion(File dir) {
		VersionFile vf = new VersionFile(dir);
		if (vf.exists())
			vf.read();
		else
			throw new RuntimeException("Content store directory must contain version file!");
		return vf;
	}

}
