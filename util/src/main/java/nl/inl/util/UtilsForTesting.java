package nl.inl.util;

import java.io.File;
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
        for (File testDir : tempDir.listFiles((parentDir, name) -> name.startsWith("BlackLabTest_"))) {

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
        File file = new File(tempDir, "BlackLabTest_" + name + "_" + UUID.randomUUID());
        file.mkdir();
        return file;
    }

}
