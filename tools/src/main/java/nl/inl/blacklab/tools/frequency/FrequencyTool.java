package nl.inl.blacklab.tools.frequency;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.resultproperty.HitGroupPropertyIdentity;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyDocumentStoredField;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.resultproperty.HitPropertyMultiple;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAnyToken;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.searches.SearchCacheDummy;
import nl.inl.blacklab.searches.SearchHitGroups;

/**
 * Determine frequency lists over annotation(s) and
 * metadata field(s) for the entire index.
 */
public class FrequencyTool {

    // Faster/less memory-intensive method is work-in-progress...
    private static final boolean FASTER_METHOD = true;

    static void exit(String msg) {
        System.out.println(msg);
        System.exit(1);
    }

    static void exitUsage(String msg) {
        if (!StringUtils.isEmpty(msg)) {
            System.out.println(msg + "\n");
        }
        exit("Calculate term frequencies over annotation(s) and metadata field(s).\n\n" +
                "Usage:\n\n  FrequencyTool [--gzip] INDEX_DIR CONFIG_FILE [OUTPUT_DIR]\n\n" +
                "  --gzip       write directly to .gz file\n" +
                "  INDEX_DIR    index to generate frequency lists for\n" +
                "  CONFIG_FILE  YAML file specifying what frequency lists to generate. See README.md.\n" +
                "  OUTPUT_DIR   where to write TSV output files (defaults to current dir)\n\n");
    }

    public static void main(String[] args) throws ErrorOpeningIndex {
        BlackLab.setConfigFromFile(); // read blacklab.yaml if exists and set config from that

        // Check for options
        int numOpts = 0;
        boolean gzip = false;
        for (String arg: args) {
            if (arg.startsWith("--")) {
                numOpts++;
                switch (arg) {
                case "--gzip":
                    gzip = true;
                    break;
                case "--help":
                    exitUsage("");
                    break;
                }
            } else
                break;
        }

        // Process arguments
        int numArgs = args.length - numOpts;
        if (numArgs < 2 || numArgs > 3) {
            exitUsage("Incorrect number of arguments.");
        }

        // Open index
        File indexDir = new File(args[numOpts]);
        if (!indexDir.isDirectory() || !indexDir.canRead()) {
            exit("Can't read or not a directory " + indexDir);
        }
        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            // Read config
            File configFile = new File(args[numOpts + 1]);
            if (!configFile.canRead()) {
                exit("Can't read config file " + configFile);
            }
            Config config = Config.fromFile(configFile);
            AnnotatedField annotatedField = index.annotatedField(config.getAnnotatedField());
            config.check(index);
            index.setCache(new SearchCacheDummy()); // don't cache results

            // Output dir
            File outputDir = new File(System.getProperty("user.dir")); // current dir
            if (numArgs > 2) {
                outputDir = new File(args[numOpts + 2]);
            }
            if (!outputDir.isDirectory() || !outputDir.canWrite()) {
                exit("Not a directory or cannot write to output dir " + outputDir);
            }

            // Generate the frequency lists
            makeFrequencyLists(index, annotatedField, config.getFrequencyLists(), outputDir, gzip);
        }
    }

    private static void makeFrequencyLists(BlackLabIndex index, AnnotatedField annotatedField, List<ConfigFreqList> freqLists, File outputDir, boolean gzip) {
        for (ConfigFreqList freqList: freqLists) {
            makeFrequencyList(index, annotatedField, freqList, outputDir, gzip);
        }
    }

    private static void makeFrequencyList(BlackLabIndex index, AnnotatedField annotatedField, ConfigFreqList freqList, File outputDir, boolean gzip) {
        String reportName = freqList.getReportName();
        System.out.println("Generate frequency list: " + reportName);
        if (!FASTER_METHOD) {
            makeFrequencyListUnoptimized(index, annotatedField, freqList, outputDir, gzip);
            return;
        }

        // Use specifically optimized CalcTokenFrequencies
        List<String> annotationNames = freqList.getAnnotations();
        List<Annotation> annotations = annotationNames.stream().map(annotatedField::annotation).collect(Collectors.toList());
        List<String> metadataFields = freqList.getMetadataFields();
        final List<Integer> docIds = new ArrayList<>();
        index.forEachDocument((__, id) -> docIds.add(id));

        // Process chunks of the documents, saving a sorted chunk for each. At the end we will merge all the chunks
        // to get the final result.
        final int DOCS_PER_CHUNK = 1_000_000;
        final int numberOfChunks = (docIds.size() + DOCS_PER_CHUNK - 1) / DOCS_PER_CHUNK;
        File tmpDir = new File(outputDir, "tmp");
        if (!tmpDir.exists() && !tmpDir.mkdir())
            throw new RuntimeException("Could not create tmp dir: " + tmpDir);
        List<File> chunkFiles = new ArrayList<>();
        for (int i = 0; i < numberOfChunks; i++) {
            int chunkStart = i * DOCS_PER_CHUNK;
            int chunkEnd = Math.min(docIds.size(), chunkStart + DOCS_PER_CHUNK);
            List<Integer> docIdsInChunk = docIds.subList(chunkStart, chunkEnd);
            SortedMap<GroupIdHash, OccurrenceCounts> occurrences =
                    CalcTokenFrequencies.get(index, annotations, metadataFields, docIdsInChunk);
            String chunkName = reportName + i;

            File chunkFile = new File(tmpDir, chunkName + ".chunk");
            writeChunkFile(chunkFile, occurrences);
            chunkFiles.add(chunkFile);
        }

        // Now merge all the chunk files
        Terms[] terms = annotationNames.stream()
                .map(name -> index.annotationForwardIndex(annotatedField.annotation(name)).terms())
                .toArray(Terms[]::new);
        mergeChunkFiles(chunkFiles, outputDir, reportName, gzip, terms);
        for (File chunkFile: chunkFiles) {
            chunkFile.delete();
        }
        tmpDir.delete();
    }

    private static void writeChunkFile(File chunkFile, SortedMap<GroupIdHash, OccurrenceCounts> occurrences) {
        try (FileOutputStream fileOutputStream = new FileOutputStream(chunkFile)) {
            try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)) {
                // Write keys and values in sorted order, so we can merge later
                objectOutputStream.writeInt(occurrences.size()); // start with number of groups
                occurrences.forEach((key, value) -> {
                    try {
                        objectOutputStream.writeObject(key);
                        objectOutputStream.writeObject(value);
                    } catch (IOException e) {
                        throw new RuntimeException();
                    }
                });
            }
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    private static void mergeChunkFiles(List<File> chunkFiles, File outputDir, String reportName, boolean gzip, Terms[] terms) {
        File outputFile = new File(outputDir, reportName + ".tsv" + (gzip ? ".gz" : ""));
        try (OutputStream outputStream = new FileOutputStream(outputFile)) {
            OutputStream stream = outputStream;
            if (gzip)
                stream = new GZIPOutputStream(stream);
            try (Writer w = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
                 CSVPrinter csv = new CSVPrinter(w, FreqListOutputTsv.TAB_SEPARATED_FORMAT)) {
                int n = chunkFiles.size();
                FileInputStream[] inputStreams = new FileInputStream[n];
                ObjectInputStream[] chunks = new ObjectInputStream[n];
                int[] numGroups = new int[n]; // groups per chunk file

                // These hold the index, key and value for the current group from every chunk file
                int[] index = new int[n];
                GroupIdHash[] key = new GroupIdHash[n];
                OccurrenceCounts[] value = new OccurrenceCounts[n];

                try {
                    int chunksExhausted = 0;
                    for (int i = 0; i < n; i++) {
                        File chunkFile = chunkFiles.get(i);
                        FileInputStream fis = new FileInputStream(chunkFile);
                        inputStreams[i] = fis;
                        ObjectInputStream ois = new ObjectInputStream(fis);
                        numGroups[i] = ois.readInt();
                        chunks[i] = ois;
                        // Initialize index, key and value with first group from each file
                        index[i] = 0;
                        key[i] = numGroups[i] > 0 ? (GroupIdHash) ois.readObject() : null;
                        value[i] = numGroups[i] > 0 ? (OccurrenceCounts) ois.readObject() : null;
                        if (numGroups[i] == 0)
                            chunksExhausted++;
                    }

                    // Now, keep merging the "lowest" keys together and advance them,
                    // until we run out of groups.
                    while (chunksExhausted < n) {
                        // Find lowest key value; we will merge that group next
                        GroupIdHash nextGroupToMerge = key[0];
                        for (int j = 1; j < n; j++) {
                            if (key[j] != null && key[j].compareTo(nextGroupToMerge) < 0)
                                nextGroupToMerge = key[j];
                        }

                        // Merge all groups with the lowest value,
                        // and advance those chunk files to the next group
                        int hits = 0, docs = 0;
                        for (int j = 0; j < n; j++) {
                            if (key[j] != null && key[j].equals(nextGroupToMerge)) {
                                // Add to merged counts
                                hits += value[j].hits;
                                docs += value[j].docs;
                                // Advance to next group in this chunk
                                index[j]++;
                                boolean noMoreGroupsInChunk = index[j] >= numGroups[j];
                                key[j] = noMoreGroupsInChunk ? null : (GroupIdHash) chunks[j].readObject();
                                value[j] = noMoreGroupsInChunk ? null : (OccurrenceCounts) chunks[j].readObject();
                                if (noMoreGroupsInChunk)
                                    chunksExhausted++;
                            }
                        }

                        // Finally, write the merged group to the output file.
                        FreqListOutputTsv.writeGroupRecord(terms, csv, nextGroupToMerge, hits, docs);
                    }

                } catch (ClassNotFoundException e) {
                    throw new RuntimeException();
                } finally {
                    for (ObjectInputStream chunk : chunks) {
                        chunk.close();
                    }
                    for (FileInputStream fis : inputStreams) {
                        fis.close();
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    private static void makeFrequencyListUnoptimized(BlackLabIndex index, AnnotatedField annotatedField, ConfigFreqList freqList, File outputDir, boolean gzip) {
        // Create our search
        try {
            // Execute search and write output file
            SearchHitGroups search = getSearch(index, annotatedField, freqList);
            HitGroups result = search.execute();
            FreqListOutput.TSV.write(index, annotatedField, freqList, result, outputDir, gzip);
        } catch (InvalidQuery e) {
            throw new BlackLabRuntimeException("Error creating freqList " + freqList.getReportName(), e);
        }
    }

    private static SearchHitGroups getSearch(BlackLabIndex index, AnnotatedField annotatedField, ConfigFreqList freqList) {
        QueryInfo queryInfo = QueryInfo.create(index);
        BLSpanQuery anyToken = new SpanQueryAnyToken(queryInfo, 1, 1, annotatedField.name());
        HitProperty groupBy = getGroupBy(index, annotatedField, freqList);
        return index.search()
                .find(anyToken)
                .groupStats(groupBy, 0)
                .sort(new HitGroupPropertyIdentity());
    }

    private static HitProperty getGroupBy(BlackLabIndex index, AnnotatedField annotatedField, ConfigFreqList freqList) {
        List<HitProperty> groupProps = new ArrayList<>();
        // Add annotations to group by
        for (String name: freqList.getAnnotations()) {
            Annotation annotation = annotatedField.annotation(name);
            groupProps.add(new HitPropertyHitText(index, annotation));
        }
        // Add metadata fields to group by
        for (String name: freqList.getMetadataFields()) {
            groupProps.add(new HitPropertyDocumentStoredField(index, name));
        }
        return new HitPropertyMultiple(groupProps.toArray(new HitProperty[0]));
    }
}
