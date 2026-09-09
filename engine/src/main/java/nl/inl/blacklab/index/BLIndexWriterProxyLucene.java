package nl.inl.blacklab.index;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DocumentStoredFieldVisitor;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.search.TermQuery;
import org.jspecify.annotations.NonNull;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.util.StringUtil;

/**
 * Simple proxy for Lucene IndexWriter.
 */
public class BLIndexWriterProxyLucene implements BLIndexWriterProxy, Closeable {

    private final BlackLabIndexWriter index;

    private final IndexWriter indexWriter;

    /** Which field, if any, contains our persistent identifiers. Otherwise null.
     * Lazily initialized on first use; only access through getPidFieldName().
     */
    private String pidFieldName;

    /** Have we looked for the pid field name? If true and pidFieldName is null, there is no pid field, don't look again. */
    private boolean pidFieldNameInitialized = false;

    /** All persistent identifier field values in this index so far.
     * Lazily initialized on first use. Only access through addToPids().
     */
    private Set<String> usedPids = null;

    public BLIndexWriterProxyLucene(IndexWriter indexWriter, BlackLabIndexWriter index) {
        this.indexWriter = indexWriter;
        this.index = index;

    }

    private synchronized String getPidFieldName() {
        if (pidFieldName == null && !pidFieldNameInitialized) {
            MetadataField pidField = index.metadata().metadataFields().pidField();
            pidFieldName = pidField == null ? null : pidField.name();
            pidFieldNameInitialized = true;
        }
        return pidFieldName;
    }

    /** Get the PID for this document, if the index has a PID field.
     * <p>
     * Used to check for duplicates when adding documents to the index.
     *
     * @param document the document
     * @return the PID term, or null if no PID field is configured or this is a fragment
     */
    private String getPidTerm(BLInputDocument document) {
        String pidFieldName = getPidFieldName();
        if (pidFieldName == null)
            throw new ErrorIndexingFile("Missing pid field name");
        String pid = document.get(pidFieldName);
        if (pid == null) {
            String fragmentPid = document.get(
                    BLInputDocument.FRAG_FIELD_DOC); // fragments index reference to their document in this field
            if (fragmentPid != null)
                return null; // this is a fragment, don't check for duplicates
        }
        if (pid == null) {
            throw new ErrorIndexingFile("Document has no persistent identifier (pidField '" + pidFieldName +
                    "'). Document: " + document);
        }
        // lowercase, remove accents
        return StringUtil.desensitize(pid);
    }

    @Override
    public void addDocuments(List<BLInputDocument> documents) throws IOException {
        List<Document> docs = luceneDocs(documents);
        // Do we have a doc type field?
        // Do we have a persistent identifier (pid)? If yes, ensure it only occurs once in the corpus.
        if (getPidFieldName() != null) {
            // We have a pid; if any already exist in the index; update the document instead of adding.
            List<String> pidTerms = new ArrayList<>(documents.size());
            for (BLInputDocument document : documents) {
                String pidTerm = getPidTerm(document);
                if (pidTerm != null)
                    pidTerms.add(pidTerm);
            }
            BlackLabIndexWriter.IfDocumentExists ifDocumentExists = index.getIfDocumentExists();
            addOrUpdate(docs, pidTerms, ifDocumentExists);
        } else {
            // We don't have a pid. Just add the documents.
            indexWriter.addDocuments(docs);
        }
    }

    /**
     * Atomically add or update document (or skip/fail, depending on config).
     */
    private synchronized void addOrUpdate(List<Document> docs, List<String> pidTerms,
            BlackLabIndexWriter.IfDocumentExists ifDocumentExists) throws IOException {
        if (pidTerms != null && !addToPids(pidTerms)) { // (pidTerm == null means no pid configured or this is a fragment)
            // Some already exist; handle according to configuration
            switch (ifDocumentExists) {
            case UPSERT -> indexWriter.updateDocuments(queryFrom(pidTerms), docs);
            case SKIP -> { /* do nothing */ }
            case FAIL -> throw new ErrorIndexingFile("Document with pid '" + pidTerms +
                    "' already exists in corpus; cannot add it again " +
                    "(ifDocumentExists setting set to 'fail'; set to 'replace' to upsert instead)");
            default -> throw new IllegalArgumentException();
            }
        } else {
            // Not in the index yet; add it now.
            indexWriter.addDocuments(docs);
        }
    }

    private Query queryFrom(List<String> pidTerms) {
        if (pidTerms.size() == 1) {
            return pidTermQuery(pidTerms.get(0));
        } else {
            BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
            for (String pidTerm : pidTerms) {
                queryBuilder.add(pidTermQuery(pidTerm), BooleanClause.Occur.SHOULD);
            }
            return queryBuilder.build();
        }
    }

    private @NonNull Query pidTermQuery(String pid) {
        return new TermQuery(new Term(getPidFieldName(), pid));
    }

    /**
     * Add a pid to the set of used pids.
     *
     * @param pids the pids to add
     * @return true if there were any new pids added, false if some or all pids were already in the set
     */
    private synchronized boolean addToPids(List<String> pids) {
        if (usedPids == null)
            usedPids = findUsedPids();
        return usedPids.addAll(pids);
    }

    /** Finds all PIDs currently in the index. */
    private Set<String> findUsedPids() {
        Set<String> usedPids = new ObjectOpenHashSet<>(getNumberOfDocs());
        String pidFieldName1 = getPidFieldName();
        if (pidFieldName1 != null) {
            try (IndexReader reader = DirectoryReader.open(indexWriter)) {
                var fields = reader.storedFields();
                var fieldsToVisit = Collections.singleton(pidFieldName1);

                for (int i = 0; i < reader.maxDoc(); i++) {
                    var visitor = new DocumentStoredFieldVisitor(fieldsToVisit) {
                        private boolean found = false;

                        @Override
                        public Status needsField(FieldInfo fieldInfo) {
                            if (found)
                                return Status.STOP;
                            if (fieldsToVisit.contains(fieldInfo.name)) {
                                found = true;
                                return Status.YES;
                            }
                            return Status.NO;
                        }
                    };
                    fields.document(i, visitor);
                    var doc = visitor.getDocument();

                    String pid1 = doc.get(pidFieldName1);
                    if (pid1 != null) {
                        usedPids.add(pid1);
                    }
                }
            } catch (IOException e) {
                throw new ErrorIndexingFile("Error gathering existing persistent identifiers from index", e);
            }
        }
        return usedPids;
    }

    private Document luceneDoc(BLInputDocument document) {
        return ((BLInputDocumentLucene)document).getDocument();
    }

    private List<Document> luceneDocs(List<BLInputDocument> documents) {
        List<Document> luceneDocs = new ArrayList<>(documents.size());
        for (BLInputDocument document : documents) {
            BLIndexWriterProxy.ensureDocTypeFieldSet(document);
            luceneDocs.add(((BLInputDocumentLucene)document).getDocument());
        }
        return luceneDocs;
    }

    @Override
    public void close() throws IOException {
        indexWriter.close();
    }

    @Override
    public void commit() throws IOException {
        indexWriter.commit();
    }

    @Override
    public void rollback() throws IOException {
        indexWriter.rollback();
    }

    @Override
    public boolean isOpen() {
        return indexWriter.isOpen();
    }

    public IndexWriter getWriter() {
        return indexWriter;
    }

    @Override
    public void deleteDocuments(Query q) throws IOException {
        if (index.metadataFields().anyOccurInFragments()) {
            // We have fragments in this index, so we need to delete all fragments of the matching documents as well.
            // First, find all matching documents (not fragments)
            if (getPidFieldName() == null) {
                throw new IllegalStateException("Index contains fragments but no pid field is configured");
            }
            try (IndexReader reader = DirectoryReader.open(indexWriter)) {
                // Find all the matching PIDs
                IndexSearcher searcher = new IndexSearcher(reader);
                List<String> pids = new ArrayList<>();
                searcher.search(q, new SimpleCollector() {
                    @Override
                    public void collect(int doc) throws IOException {
                        Document document = searcher.doc(doc);
                        String pid = document.get(getPidFieldName());
                        if (pid != null) { // i.e. skip any fragments we may have matched
                            pid = StringUtil.desensitize(pid);
                            pids.add(pid);
                        }
                    }

                    @Override
                    public ScoreMode scoreMode() {
                        return ScoreMode.COMPLETE_NO_SCORES;
                    }
                });

                // Create a query matching all fragments and documents with those PIDs, and delete them
                BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
                for (String pid : pids) {
                    Term fragPidTerm = new Term(BLInputDocument.FRAG_FIELD_DOC, pid);
                    queryBuilder.add(new TermQuery(fragPidTerm), BooleanClause.Occur.SHOULD);
                }
                for (String pid : pids) {
                    Term docPidTerm = new Term(getPidFieldName(), pid);
                    queryBuilder.add(new TermQuery(docPidTerm), BooleanClause.Occur.SHOULD);
                }
                indexWriter.deleteDocuments((Query) queryBuilder.build());
            }
        } else {
            // No need for fragment-aware deletion; just delete the document(s) matching the query
            indexWriter.deleteDocuments(q);
        }
    }

    @Override
    public long updateDocuments(Query q, List<BLInputDocument> documents) throws IOException {
        return indexWriter.updateDocuments(q, luceneDocs(documents));
    }

    @Override
    public int getNumberOfDocs() {
        return indexWriter.getDocStats().numDocs;
    }

}
