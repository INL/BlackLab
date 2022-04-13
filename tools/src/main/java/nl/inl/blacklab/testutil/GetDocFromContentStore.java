package nl.inl.blacklab.testutil;

import nl.inl.blacklab.contentstore.ContentStore;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;

import java.io.File;

/**
 * Retrieves and displays a document from a BlackLab content store.
 */
public class GetDocFromContentStore {

    public static void main(String[] args) throws ErrorOpeningIndex {

        if (args.length != 2) {
            System.err.println("Usage: GetDocFromContentStore <contentStoreDir> <docId>");
            return;
        }

        File csDir = new File(args[0]);
        int id = Integer.parseInt(args[1]);

        ContentStore cs = ContentStore.open(csDir, false, false);
        String content = cs.retrieve(id);
        System.out.println(content);
    }
}
