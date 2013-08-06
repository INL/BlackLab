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

	/**
	 * Get the type and version of the content store
	 * @param dir directory of the content store
	 * @return the contents of the store's version file
	 */
	public static VersionFile getStoreTypeVersion(File dir) {
		VersionFile vf = new VersionFile(dir);
		if (vf.exists())
			vf.read();
		else
			throw new RuntimeException("Content store directory must contain version file!");
		return vf;
	}

}
