package nl.inl.blacklab.testutil;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import nl.inl.blacklab.contentstore.ContentStore;
import nl.inl.blacklab.contentstore.ContentStoreDirZip;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.util.FileUtil;
import nl.inl.util.Timer;

/**
 * Executes a batch of fetch operations on a content store.
 */
public class BatchContentStore {

    private static final int SNIPPET_LENGTH_CHARS = 100;

    public static void main(String[] args) {

        int fileArgNumber = 0;
        File indexDir = null;
        File inputFile = null;
        for (String arg : args) {
            arg = arg.trim();
            if (arg.charAt(0) == '-') {
                if (arg.equals("-t")) {
                    // determineTotalHits = false;
                } else {
                    System.err.println("Illegal option: " + arg);
                    usage();
                    return;
                }
            } else {
                switch (fileArgNumber) {
                case 0:
                    indexDir = new File(arg);
                    if (!indexDir.exists() || !indexDir.isDirectory()) {
                        System.err.println("Index directory not found: " + arg);
                        usage();
                        return;
                    }
                    break;
                case 1:
                    inputFile = new File(arg);
                    if (!inputFile.exists()) {
                        System.err.println("Input file not found: " + arg);
                        usage();
                        return;
                    }
                    break;
                default:
                    System.err.println("Too many file arguments (supply index dir and input file)");
                    usage();
                    return;
                }
                fileArgNumber++;
            }
        }
        if (fileArgNumber < 2) {
            System.err.println("Too few file arguments (supply index dir and input file)");
            usage();
            return;
        }

        System.err.print("Opening content store... ");
        ContentStore cs = new ContentStoreDirZip(indexDir);
        System.err.println("done. [#docs: " + cs.idSet().size() + "]");

        System.out.println("First\tNumber\tSkip\tSnippets\tTime");

        for (String query : FileUtil.readLines(inputFile)) {
            query = query.trim();
            if (query.length() == 0 || query.charAt(0) == '#')
                continue; // skip empty lines and #-comments
            String[] numberStr = query.split("\\s+");
            int[] numbers = new int[numberStr.length];
            try {
                for (int i = 0; i < numberStr.length; i++) {
                    numbers[i] = Integer.parseInt(numberStr[i]);
                }

                int first = numbers[0];
                int number = numbers.length > 1 ? numbers[1] : 100;
                int skip = numbers.length > 2 ? numbers[2] : 0;
                int snippets = numbers.length > 3 ? numbers[3] : 5;
                long time = doPerformanceTest(cs, first, number, skip, snippets);
                System.out.println(String.format("%d\t%d\t%d\t%d\t%d", first, number, skip, snippets,
                        time));

            } catch (Exception e) {
                e.printStackTrace(System.err);
                System.err.println("Error with line '" + query + "'; skipping...");
            }

        }
    }

    /**
     * Do a performance test, retrieving snippets from a number of documents.
     * 
     * @param cs the content store
     *
     * @param first fiid (position in toc) of first document to access
     * @param number number of documents to access
     * @param skip number of documents to skip between accesses
     * @param snippets number of random snippets to retrieve from each document
     * @return elapsed time in ms
     */
    public static long doPerformanceTest(ContentStore cs, int first, int number, int skip,
            int snippets) {
        int[] start = new int[snippets];
        int[] end = new int[snippets];

        // Build sorted list of non-deleted ids.
        List<Integer> docIds = new ArrayList<>();
        for (Integer i : cs.idSet()) {
            if (!cs.isDeleted(i))
                docIds.add(i);
        }
        docIds.sort(Comparator.naturalOrder());

        Timer t = new Timer();
        int docPos = first;
        for (int i = 0; i < number; i++) {
                int id, length;
            do {
                if (docPos >= docIds.size())
                    throw new BlackLabRuntimeException("Performance test went beyond end of content store ("
                            + docIds.size() + " docs)");
                id = docIds.get(docPos);

                // Choose random snippets
                length = cs.docLength(id);
                if (length == 0) // can't get snippet from empty doc
                    docPos++;
            } while (length == 0);
            int snippetLength = Math.min(SNIPPET_LENGTH_CHARS, length);
            for (int j = 0; j < snippets; j++) {
                start[j] = (int) (Math.random() * (length - snippetLength));
                end[j] = start[j] + snippetLength;
            }

            // Retrieve snippets
            cs.retrieveParts(id, start, end);
        }
        return t.elapsed();
    }

    private static void usage() {
        System.err.println("\nUsage: " + BatchContentStore.class.getSimpleName()
                + " <contentStoreDir> <inputfile>\n\n"
                + "<inputfile> should contain lines of whitespace-separated integers:\n"
                + "   <first> <number> <skip> <snippets>\n" + "\n"
                + "   first: position of the first document to access\n"
                + "   number: number of documents to access [100]\n"
                + "   skip: how many documents to skip between accesses [0]\n"
                + "   snippets: how many random snippets to retrieve per document [5]\n" + "\n"
                // + "Options:\n"
                // + "-t do not determine total number of hits\n"
                // + "\n"
                + "Output:\n" + "<first> <number> <skip> <snippets>\t<searchTimeMs>\n");
    }
}
