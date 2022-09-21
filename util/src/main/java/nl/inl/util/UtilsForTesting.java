package nl.inl.util;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Misc. testing utilities.
 */
public final class UtilsForTesting {

    /** What prefix do all test dirs use? */
    public static final String TEST_DIR_PREFIX = "BlackLabTest_";

    /** What file must be present to allow autoremoving a test dir? */
    public static final String MARKER_FILE_NAME = "REMOVE_TEST_DIR";

    /** How old must marker file be to be automatically removed? (10h) */
    public static final long REMOVE_TEST_DIRS_OLDER_THAN_MS = 10000; //10 * 3600 * 1000;

    /** Have we tried to clean up old test dirs? */
    private static boolean cleanedUpTestDirs = false;

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
        File[] l = tempDir.listFiles((parentDir, name) -> name.startsWith(TEST_DIR_PREFIX));
        if (l == null)
            return;

        // Remove old ContentStore test dirs from temp dir, if possible
        // (may not be possible because of memory mapping lock on Windows;
        //  in this case we just leave the files and continue)
        for (File testDir: l) {
            File markerFile = new File(testDir, MARKER_FILE_NAME);
            long now = System.currentTimeMillis();
            if (markerFile.exists() && now - markerFile.lastModified() > REMOVE_TEST_DIRS_OLDER_THAN_MS) {
                // Recursively delete this temp dir
                FileUtil.deleteTree(testDir);
            }
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
    public synchronized static File createBlackLabTestDir(String name) {
        // If there's old test dirs hanging around, clean them up (check once per run).
        if (!cleanedUpTestDirs) {
            cleanedUpTestDirs = true;
            removeBlackLabTestDirs();
        }

        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        File testDir = new File(tempDir, TEST_DIR_PREFIX + name + "_" + UUID.randomUUID());
        if (!testDir.mkdir())
            throw new RuntimeException("Unable to create test dir: " + testDir);

        // Create an empty marker file so we know this dir is okay to delete eventually
        File markerFile = new File(testDir, MARKER_FILE_NAME);
        try {
            if (!markerFile.createNewFile())
                throw new RuntimeException("Unable to create marker file: " + markerFile);
        } catch (IOException e) {
            throw new RuntimeException("Unable to create marker file: " + markerFile, e);
        }

        return testDir;
    }
}
