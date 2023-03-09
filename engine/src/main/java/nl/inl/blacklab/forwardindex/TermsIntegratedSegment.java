package nl.inl.blacklab.forwardindex;

import java.io.IOException;
import java.util.Iterator;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.store.IndexInput;

import net.jcip.annotations.NotThreadSafe;
import nl.inl.blacklab.codec.BlackLab40PostingsFormat;
import nl.inl.blacklab.codec.BlackLab40PostingsReader;
import nl.inl.blacklab.codec.BlackLab40PostingsWriter;
import nl.inl.blacklab.codec.BlackLab40PostingsWriter.Field;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/**
 * Presents an iterator over ONE field/annotation in ONE segment of the Index.
 *
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

            // OPT: read cache these fields somewhere so we don't read them once per annotation
            try (IndexInput fieldInput = segmentReader.openIndexFile(BlackLab40PostingsFormat.FIELDS_EXT)) {
                while (fieldInput.getFilePointer() < (fieldInput.length() - CodecUtil.footerLength())) {
                    BlackLab40PostingsWriter.Field f = new BlackLab40PostingsWriter.Field(fieldInput);
                    if (f.getFieldName().equals(luceneField)) {
                        this.field = f;
                        break;
                    }
                }
                // we checked all fields but did not find it.
                if (this.field == null)
                    throw new BlackLabRuntimeException("Trying to read forward index for field "+luceneField+ ", but it does not exist.");
            }
        } catch (IOException e) {
            throw new BlackLabRuntimeException("Error reading forward index/terms for segment");
        }
    }

    public Iterator<TermInSegment> iterator() {
        if (this.isClosed) throw new BlackLabRuntimeException("Segment is closed");
        return new TermInSegmentIterator(this);
    }

    @Override
    public void close() {
        try {
            isClosed = true;
            _termIndexFile.close();
            _termsFile.close();
            _termOrderFile.close();
            _termIndexFile = _termsFile = _termOrderFile = null;
            segmentReader = null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** returns the total number of terms in this segment for this field */
    public int size() {
        return this.field.getNumberOfTerms();
    }

    public static class TermInSegment {
        public String term;
        /** The local term id. */
        public int id;
        public int sortPositionSensitive;
        public int sortPositionInsensitive;
    }

    @NotThreadSafe
    public static class TermInSegmentIterator implements Iterator<TermInSegment> {
        /**
         * Metadata about this field(=annotation) in this segment(=section of the index).
         * such as how many terms in the field in this segment,
         * file offsets where to find the data in the segment's files, etc.
         */
        private final Field field;
        /** Ord (ordinal) of the segment */
        private final int ord;

        /**
         * File with the iteration order.
         * All term IDS are local to this segment.
         * for reference, the file contains the following mappings:
         *     int[n] termID2InsensitivePos    ( offset [0*n*int] )
         *     int[n] insensitivePos2TermID    ( offset [1*n*int] )
         *     int[n] termID2SensitivePos      ( offset [2*n*int] )
         *     int[n] sensitivePos2TermID      ( offset [3*n*int] )
         */
        private final IndexInput termID2SensitivePosFile;
        private final IndexInput termID2InsensitivePosFile;
        /** File containing the strings */
        private final IndexInput termStringFile;

        /** index of the term CURRENTLY loaded in peek/how many times has next() been called. */
        private int i = 0;
        /** Total number of terms in the segment */
        private final int n;
        private final TermInSegment t = new TermInSegment();

        /**
         * @param segment only used for initialization, because we need to pass many parameters otherwise.
         */
        public TermInSegmentIterator(TermsIntegratedSegment segment) {
            // first navigate to where the sensitive iteration order is stored in the _termOrderFile.
            try {
                this.field = segment.field;
                this.ord = segment.ord;

                // clone these file accessors, as they are not threadsafe
                // while this code was written these file handles were only ever used in one thread,
                // but doing this ensures we don't break things in the future.
                this.termID2SensitivePosFile = segment._termOrderFile.clone();
                this.termID2InsensitivePosFile = segment._termOrderFile.clone();
                this.termStringFile = segment._termsFile.clone();

                this.i = 0;
                this.n = field.getNumberOfTerms();

                this.termID2SensitivePosFile.seek(((long)n)*Integer.BYTES*2+field.getTermOrderOffset());
                this.termID2InsensitivePosFile.seek(field.getTermOrderOffset());

                // All fields share the same strings file.  Move to the start of our section in the file.
                IndexInput stringoffsets = segment._termIndexFile.clone();
                stringoffsets.seek(field.getTermIndexOffset());
                long firstStringOffset = stringoffsets.readLong();

                this.termStringFile.seek(firstStringOffset);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public boolean hasNext() {
            return i < n;
        }

        @Override
        public TermInSegment next() {
            try {
                if (i >= n)
                    return null;

                this.t.term = termStringFile.readString();

                this.t.sortPositionInsensitive = this.termID2InsensitivePosFile.readInt();
                this.t.sortPositionSensitive = this.termID2SensitivePosFile.readInt();

                this.t.id = i++;

                return this.t;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Get the ord (ordinal) of the segment this iterator was constructed on.
         * You could see this as the "id" of the segment. Starts at 0 and increments by 1 for every next segment.
         */
        public int ord() {
            return this.ord;
        }

        /** returns the total number of terms in this segment */
        public int size() {
            return this.n;
        }
    }

    /**
     * Get the ord (ordinal) of the segment.
     * You could see this as the "id" of the segment. Starts at 0 and increments by 1 for every next segment.
     */
    public int ord() {
        return this.ord;
    }

    public Field field() {
        return this.field;
    }
}
