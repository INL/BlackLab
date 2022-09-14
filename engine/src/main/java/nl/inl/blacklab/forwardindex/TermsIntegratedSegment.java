package nl.inl.blacklab.forwardindex;

import java.io.IOException;
import java.util.Iterator;

import org.apache.lucene.store.IndexInput;

import net.jcip.annotations.NotThreadSafe;
import nl.inl.blacklab.codec.BlackLab40PostingsFormat;
import nl.inl.blacklab.codec.BlackLab40PostingsReader;
import nl.inl.blacklab.codec.BlackLab40PostingsWriter;

/**
 * Ideally short-lived and only used during setup of the forward index and terms instances.
 * In the future, if and when we no longer have need to transform results from segment to global,
 * could be rewritten to do everything with memory mapping, and become longer lived.
 * In that case this will become the primary Terms access (instead of Terms/TermsIntegrated) probably.
 *
 * Iteration is NOT thread safe, as the underlying file buffers are shared.
 */
@NotThreadSafe()
public class TermsIntegratedSegment implements AutoCloseable {
    private boolean isClosed = false;
    private BlackLab40PostingsReader segmentReader;
    private BlackLab40PostingsWriter.Field field;
    private int ord;

    private IndexInput _termIndexFile;
    private IndexInput _termsFile;
    private IndexInput _termOrderFile;


    public TermsIntegratedSegment(BlackLab40PostingsReader segmentReader, String luceneField, int ord) {
        try {
            this.segmentReader = segmentReader;
            this.ord = ord;
            this._termIndexFile = segmentReader.openIndexFile(BlackLab40PostingsFormat.TERMINDEX_EXT);
            this._termsFile = segmentReader.openIndexFile(BlackLab40PostingsFormat.TERMS_EXT);
            this._termOrderFile = segmentReader.openIndexFile(BlackLab40PostingsFormat.TERMORDER_EXT);


            // TODO read cache these fields somewhere so we don't read them once per annotation
            try (IndexInput fieldInput = segmentReader.openIndexFile(BlackLab40PostingsFormat.FIELDS_EXT)) {
                while (fieldInput.getFilePointer() < fieldInput.length()) {
                    BlackLab40PostingsWriter.Field f = new BlackLab40PostingsWriter.Field(fieldInput);
                    if (f.getFieldName().equals(luceneField)) {
                        this.field = f;
                        break;
                    }
                }
                // we checked all fields but did not find it.
                throw new RuntimeException("Trying to read forward index for field "+luceneField+ ", but it does not exist.");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        isClosed = true;
        _termIndexFile.close();
        _termsFile.close();
        _termOrderFile.close();
        _termIndexFile = _termsFile = _termOrderFile = null;
        segmentReader = null;
    }

    public static class TermInSegment {
        public String term;
        /** The local term id. */
        public int id;
        public int sortPosition;
    }

    public enum IterationOrder {
        insensitive,
        sensitive,
    }


    @NotThreadSafe
    public class TermInSegmentIterator implements Iterator<TermInSegment> {
        /** Contains the result of the last call to next() */
        private TermInSegment next = new TermInSegment();
        /** Contains the next term returned by next() */
        private TermInSegment peek = new TermInSegment();

        /** represents the term currently loaded in {@link #peek} */
        int i = -1;
        int n = field.getNumberOfTerms();


        public TermInSegmentIterator(IterationOrder order) {

            // first navigate to where the sensitive iteration order is stored in the _termOrderFile.
            /*
            for reference, the file contains:
                int[n] insensitivePos2TermID    ( offset [0*n*int] )
                int[n] termID2InsensitivePos    ( offset [1*n*int] )
                int[n] sensitivePos2TermID      ( offset [2*n*int] )
                int[n] termID2sensitivePos      ( offset [3*n*int] )
             */
            try {
                _termOrderFile.seek(((long)n)*Integer.BYTES*(order == IterationOrder.sensitive ? 3 : 1));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public boolean hasNext() {
            return i < n;
        }

        public TermInSegment peek() {
            return this.peek;
        }

        @Override
        public TermInSegment next() {
            // eg.: [next, peek] = [1,2]. swap to [next,peek] = [2,1]. Then read a new entry into peek: [next, peek] = [2,3]
            TermInSegment tmp = this.next;
            this.next = this.peek;
            this.peek = tmp;

            try {
                // load into peek.
                if (i < n) {
                    i+=1;

                    peek.id = _termOrderFile.readInt(); // read int a i
                    peek.sortPosition = i;

                    long offsetInTermStringFile = _termIndexFile.readLong(); // read long at i
                    _termsFile.seek(offsetInTermStringFile);
                    peek.term = _termsFile.readString();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return this.next;
        }

        public TermsIntegratedSegment source() {
            return TermsIntegratedSegment.this;
        }
    }

    /** Return an iterator that returns the terms in order of case- and diacritic sensitive sorting */
    public TermInSegmentIterator iteratorSensitive() {
        if (isClosed) throw new UnsupportedOperationException("Cannot iterate terms after closing");
        return new TermInSegmentIterator(IterationOrder.sensitive);
    }

    public TermInSegmentIterator iteratorInsensitive() {
        if (isClosed) throw new UnsupportedOperationException("Cannot iterate terms after closing");
        return new TermInSegmentIterator(IterationOrder.insensitive);
    }

    public int ord() {
        return this.ord;
    }
}
