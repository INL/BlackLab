package nl.inl.util;

import java.io.File;
import java.util.UUID;

/**
 * Misc. testing utilities.
 */
public final class UtilsForTesting {

    private UtilsForTesting() {
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
