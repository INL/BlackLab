package nl.inl.blacklab;
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


import java.io.File;
import java.nio.charset.StandardCharsets;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.mocks.DocIndexerExample;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.Concordances;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.util.FileUtil;
import nl.inl.util.FileUtil.FileTask;

/**
 * Simple test program to demonstrate index & search functionality.
 */
public class Example {

    /**
     * The BlackLab index object.
     */
    static BlackLabIndex index;

    /**
     * Some test XML data to index.
     */
    static String[] testData = {
            "<doc>" +
                    "<w l='the'   p='art' >The</w> " +
                    "<w l='quick' p='adj'>quick</w> " +
                    "<w l='brown' p='adj'>brown</w> " +
                    "<w l='fox'   p='nou'>fox</w> " +
                    "<w l='jump'  p='vrb' >jumps</w> " +
                    "<w l='over'  p='pre' >over</w> " +
                    "<w l='the'   p='art' >the</w> " +
                    "<w l='lazy'  p='adj'>lazy</w> " +
                    "<w l='dog'   p='nou'>dog</w>" +
                    ".</doc>",

            "<doc> " +
                    "<w l='may' p='vrb'>May</w> " +
                    "<w l='the' p='art'>the</w> " +
                    "<w l='force' p='nou'>force</w> " +
                    "<w l='be' p='vrb'>be</w> " +
                    "<w l='with' p='pre'>with</w> " +
                    "<w l='you' p='pro'>you</w>" +
                    ".</doc>"
    };

    /**
     * The main program
     * 
     * @param args command line arguments
     * @throws ErrorOpeningIndex 
     * @throws InvalidQuery 
     */
    public static void main(String[] args) throws ErrorOpeningIndex, InvalidQuery {

        // Get a temporary directory for our test index, and make sure it doesn't exist
        File indexDir = new File(System.getProperty("java.io.tmpdir"), "BlackLabExample");
        cleanupOldIndexDir(indexDir);

        // Register our custom DocIndexer.
        DocumentFormats.registerFormat("exampleformat", DocIndexerExample.class);

        // Create an index and add our test documents.
        Indexer indexer = null;
        try {
            indexer = Indexer.createNewIndex(indexDir, "exampleformat");
            for (int i = 0; i < testData.length; i++) {
                indexer.index("test" + (i + 1), testData[i].getBytes(StandardCharsets.UTF_8));
            }

        } catch (Exception e) {

            // An error occurred during indexing.
            System.err.println("An error occurred, aborting indexing. Error details follow.");
            e.printStackTrace();

        } finally {

            // Finalize and close the index.
            if (indexer != null)
                indexer.close();

        }

        // Create the BlackLab index object
        index = BlackLab.open(indexDir);
        try {

            // Find the word "the"
            System.out.println("-----");
            findPattern(parseCorpusQL(" 'the' "));

            // Find prepositions
            System.out.println("-----");
            findPattern(parseCorpusQL(" [pos='pre'] "));

            // Find sequence of words
            System.out.println("-----");
            findPattern(parseCorpusQL(" 'the' []{0,2} 'fo.*' "));

        } catch (InvalidQuery e) {

            // Query parse error
            System.err.println(e.getMessage());

        } finally {

            // Close the index object
            index.close();

        }
    }

    private static void cleanupOldIndexDir(File indexDir) {
        if (indexDir.exists()) {
            // Delete the old example dir
            // (NOTE: we cannot do this on exit because memory mappings may
            //  prevent deletion on Windows)
            FileUtil.processTree(indexDir, new FileTask() {
                @Override
                public void process(File f) {
                    if (!f.delete())
                        throw new BlackLabRuntimeException("Could not delete file: " + f);
                }
            });
        }
    }

    /**
     * Parse a Corpus Query Language query
     *
     * @param query the query to parse
     * @return the resulting BlackLab text pattern
     * @throws InvalidQuery if query couldn't be parsed
     */
    private static TextPattern parseCorpusQL(String query) throws InvalidQuery {

        // A bit of cheating here - CorpusQL only allows double-quoting, but
        // that makes our example code look ugly (we have to add backslashes).
        // We may extend CorpusQL to allow single-quoting in the future.
        query = query.replaceAll("'", "\"");

        // Parse query using the CorpusQL parser
        return CorpusQueryLanguageParser.parse(query);
    }

    /**
     * Find a text pattern in the contents field and display the matches.
     *
     * @param tp the text pattern to search for
     * @throws WildcardTermTooBroad if a wildcard term matched too many terms in the index
     * @throws InvalidQuery 
     */
    static void findPattern(TextPattern tp) throws InvalidQuery {
        // Execute the search
        BLSpanQuery query = tp.toQuery(QueryInfo.create(index));
        Hits hits = index.find(query);
        Hits sortedHits = hits.sort(new HitPropertyHitText(index));

        // Display the concordances
        displayConcordances(sortedHits);
    }

    /**
     * Display a list of hits.
     *
     * @param hits the hits to display
     */
    static void displayConcordances(Hits hits) {
        // Loop over the hits and display.
        Concordances concs = hits.concordances(index.defaultContextSize(), ConcordanceType.FORWARD_INDEX);
        for (Hit hit : hits) {
            Concordance conc = concs.get(hit);
            // Strip out XML tags for display.
            String[] concParts = conc.partsNoXml();
            String left = concParts[0];
            String match = concParts[1];
            String right = concParts[2];

            System.out.printf("[%05d:%06d] %45s[%s]%s%n", hit.doc(), hit.start(), left, match, right);
        }
    }

}
