package nl.inl.blacklab.forwardindex;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.store.IndexInput;

import net.jcip.annotations.NotThreadSafe;
import nl.inl.blacklab.codec.BlackLab40PostingsFormat;
import nl.inl.blacklab.codec.BlackLab40PostingsReader;
import nl.inl.blacklab.codec.BlackLab40PostingsWriter;
import nl.inl.blacklab.codec.BlackLab40PostingsWriter.Field;

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
    private final int ord;

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
                if (this.field == null)
                    throw new RuntimeException("Trying to read forward index for field "+luceneField+ ", but it does not exist.");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
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
        // example for 3 terms [0,1,2]

        // before initialize 
        // n = 3
        // i = 0 (how many terms have we already returned - how many times has next() been called)
        
        // when just initialized
        // i = 0
        // next = _
        // peek = 0
        // hasnext = true (i < n)

        // get 1 term
        // i = 1
        // next = 0 
        // peek = 1
        // hasnext = true 

        // get 1 term
        // i = 2
        // next = 1
        // peek = 2
        // hasnext = true

        // get 1 term
        // next = 2
        // peek = _
        // hasnext = false


        // example for 0 terms []

        // before initialize
        // n = 0
        
        // when just initialized
        // next = _
        // peek = _
        // hasnext = false = 

        
        
        // peek now contains the term at index 1

        // get 1 term
        // next contains 
        
        
        private TermInSegment next = new TermInSegment();
        private TermInSegment peek = new TermInSegment();

        // index of the term CURRENTLY loaded in peek/how many times has next() been called.
        int i = 0;
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
                _termOrderFile.seek(((long)n)*Integer.BYTES*(order == IterationOrder.sensitive ? 3 : 1) + field.getTermOrderOffset());
                _termIndexFile.seek(field.getTermIndexOffset());
                loadPeek();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
 
        private void loadPeek() {
            try {
                if (i < n) {
                    peek.sortPosition = _termOrderFile.readInt(); // read int a i
                    peek.id = i;
    
                    long offsetInTermStringFile = _termIndexFile.readLong(); // read long at i
                    _termsFile.seek(offsetInTermStringFile);
                    peek.term = _termsFile.readString();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public boolean hasNext() {
            return i < n;
        }

        /** Returns null if no more data */
        public TermInSegment peek() {
            return i < n ? peek : null;
        }

        @Override
        public TermInSegment next() {
            if (i >= n) return null;
            
            // load peek into next
            TermInSegment tmp = this.next;
            this.next = this.peek;
            this.peek = tmp;
            ++i;
            loadPeek();
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

    public Field field() {
        return this.field;
    }
}
