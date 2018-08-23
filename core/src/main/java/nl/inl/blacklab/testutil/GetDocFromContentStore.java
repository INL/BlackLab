package nl.inl.blacklab.testutil;

import java.io.File;

import nl.inl.blacklab.contentstore.ContentStore;
import nl.inl.blacklab.contentstore.ContentStoreDirZip;

/**
 * Retrieves and displays a document from a BlackLab content store.
 */
public class GetDocFromContentStore {

    public static void main(String[] args) {

        if (args.length != 2) {
            System.err.println("Usage: GetDocFromContentStore <contentStoreDir> <docId>");
            return;
        }

        File csDir = new File(args[0]);
        int id = Integer.parseInt(args[1]);

        ContentStore cs = new ContentStoreDirZip(csDir);
        String content = cs.retrieve(id);
        System.out.println(content);
    }
}
