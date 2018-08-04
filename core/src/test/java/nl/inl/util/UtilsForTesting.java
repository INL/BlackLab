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

import java.io.File;
import java.io.FilenameFilter;
import java.util.UUID;

import nl.inl.util.FileUtil.FileTask;

/**
 * Misc. testing utilities.
 */
public final class UtilsForTesting {

    private UtilsForTesting() {
    }

    /**
     * Removes temporary test directories that may be left over from previous test
     * runs because of memory mapping file locking on Windows.
     *
     * It is good practice to start and end a test run by calling
     * removeBlackLabTestDirs().
     */
    public static void removeBlackLabTestDirs() {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));

        // Remove old ContentStore test dirs from temp dir, if possible
        // (may not be possible because of memory mapping lock on Windows;
        //  in this case we just leave the files and continue)
        for (File testDir : tempDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File parentDir, String name) {
                return name.startsWith("BlackLabTest_");
            }
        })) {

            // Recursively delete this temp dir
            FileUtil.processTree(testDir, new FileTask() {
                @Override
                public void process(File f) {
                    f.delete();
                }
            });
            testDir.delete();
        }
    }

    /**
     * Create a temporary directory for BlackLab testing. A GUID is used to avoid
     * collisions. Note that because of memory mapping and file locking issues, temp
     * dirs may hang around. It is good practice to start and end a test run by
     * calling removeBlackLabTestDirs().
     *
     * @param name descriptive name to be used in the temporary dir (useful while
     *            debugging)
     * @return the newly created temp dir.
     */
    public static File createBlackLabTestDir(String name) {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        File file = new File(tempDir, "BlackLabTest_" + name + "_" + UUID.randomUUID().toString());
        file.mkdir();
        return file;
    }

}
