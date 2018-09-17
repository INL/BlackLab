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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/**
 * Reads/writes a type/version file for a directory, to indicate the version of
 * the directory's contents.
 */
public class VersionFile {

    /**
     * Read version file from directory
     *
     * @param dir the directory containing the version file
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
     * @param dir the directory containing the version file
     * @param defaultType the type to use if the version file does not exist
     * @param defaultVersion the versopm to use if the version file does not exist
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
     * @param dir the directory to write the version file to
     * @param type the type to write
     * @param version the version to write
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
     * @param dir the directory containing the version file
     * @param type the type to check for
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
     * @param dir the directory containing the version file
     * @param type the type to check for
     * @param version the version to check for
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

    public static boolean exists(File indexDir) {
        VersionFile vf = new VersionFile(indexDir);
        return vf.exists();
    }

    public void read() {
        try {
            if (!file.exists())
                throw new BlackLabRuntimeException("Version file not found: " + file);
            try (BufferedReader r = FileUtil.openForReading(file)) {
                String line = r.readLine();
                if (line == null)
                    throw new BlackLabRuntimeException("Version file appears to be empty: " + file);
                String[] info = line.trim().split("\\|\\|", -1);
                type = info[0];
                if (info.length > 1)
                    version = info[1];
            }
        } catch (IOException e) {
            throw new BlackLabRuntimeException(e);
        }
    }

    void write() {
        try (PrintWriter w = FileUtil.openForWriting(file)) {
            w.write(type + "||" + version + "\n");
        } catch (FileNotFoundException e) {
            throw BlackLabRuntimeException.wrap(e);
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
