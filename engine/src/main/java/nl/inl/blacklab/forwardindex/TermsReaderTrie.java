package nl.inl.blacklab.forwardindex;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.text.CollationKey;
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.set.mutable.primitive.MutableIntSetFactoryImpl;

import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import com.googlecode.concurrenttrees.radix.RadixTree;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultCharSequenceNodeFactory;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class TermsReaderTrie extends Terms {
    private abstract class TrieValue {
        public final int length;
        public final int indexSensitive;
        public final int sortPositionInsensitive; // this is stored in the terms file
        public final int sortPositionSensitive; // this is stored in the terms file
        
        public TrieValue(int length, int indexSensitive, int sortPositionSensitive, int sortPositionInsensitive) {
            this.length = length;
            
            this.indexSensitive = indexSensitive;
            this.sortPositionSensitive = sortPositionSensitive;
            this.sortPositionInsensitive = sortPositionInsensitive;
        }
        
        public abstract String getTerm();
    }
    
    private class TrieValueSmall extends TrieValue {
        public final int offset;
        
        public TrieValueSmall(int offset, int length, int indexSensitive, int sortPositionSensitive, int sortPositionInsensitive) {
            super(length, indexSensitive, sortPositionSensitive, sortPositionInsensitive);
            this.offset = offset;
        }

        @Override
        public String getTerm() {
           return new String(termsarrayutf8[offset], length, offset, StandardCharsets.UTF_8);
        }
    }
    
    private class TrieValueLarge extends TrieValue {
        public final long offset;
        
        public TrieValueLarge(long offset, int length, int indexSensitive, int sortPositionSensitive, int sortPositionInsensitive) {
            super(length, indexSensitive, sortPositionSensitive, sortPositionInsensitive);
            this.offset = offset;
        }

        @Override
        public String getTerm() {
            final int arrayToUse = (int) Math.floor(offset / Integer.MAX_VALUE); // double-to-int casting applies floor, which is what we want, because we need to convert to 0-indexed. 
            final int offsetToUse = (int) offset % Integer.MAX_VALUE;
            return new String(termsarrayutf8[arrayToUse], offsetToUse, length, StandardCharsets.UTF_8);
        }
    }
    
    protected static final Logger logger = LogManager.getLogger(TermsReaderTrie.class);

//    protected boolean initialized = false;
    protected final File termsFile;
    protected final RadixTree<TrieValue> termsTrie = new ConcurrentRadixTree<TermsReaderTrie.TrieValue>(new DefaultCharSequenceNodeFactory());
    
    public byte[][] termsarrayutf8;
    private TrieValue[] nodes;
    private HashMap<CollationKey, MutableIntSet> insensitiveTermToIds = new HashMap<>();
    private int zeroLengthTermId = -1;
    
    public TermsReaderTrie(Collators collators, File termsFile, boolean useBlockBasedTermsFile, boolean buildTermIndexesOnInit) {
        this.termsFile = termsFile;
        this.useBlockBasedTermsFile = useBlockBasedTermsFile;
        this.collator = collators.get(MatchSensitivity.SENSITIVE);
        this.collatorInsensitive = collators.get(MatchSensitivity.INSENSITIVE);
        
        try (RandomAccessFile raf = new RandomAccessFile(termsFile, "r")) {
            try (FileChannel fc = raf.getChannel()) {
                read(fc);
            }
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }
    
    @Override
    public int indexOf(String term) {
        TrieValue v = termsTrie.getValueForExactKey(term);
        return v != null ? v.indexSensitive : term.isEmpty() ? zeroLengthTermId : -1;
    }

    @Override
    public void indexOf(MutableIntSet results, String term, MatchSensitivity sensitivity) {
        if (sensitivity.isCaseSensitive()) {
            TrieValue v = termsTrie.getValueForExactKey(term);
            results.add(v != null ? v.indexSensitive : term.isEmpty() ? zeroLengthTermId : -1);
        }

        CollationKey insensitiveId = collatorInsensitive.getCollationKey(term);
        MutableIntSet sensitiveIdsForTerm = insensitiveTermToIds.get(insensitiveId);
        results.addAll(sensitiveIdsForTerm);
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("Not in write mode");
    }

    @Override
    public void write(File termsFile) {
        throw new UnsupportedOperationException("Not in write mode");
    }

    @Override
    public String get(int id) {
        if (id >= 0 && id < nodes.length) {
            return nodes[id].getTerm();
        }
        return ""; // ?
    }

    @Override
    public int numberOfTerms() {
        return numberOfTerms;
    }

    @Override
    public int idToSortPosition(int id, MatchSensitivity sensitivity) {
        if (id >= 0 && id < nodes.length) {
            return sensitivity.isCaseSensitive() ? nodes[id].sortPositionSensitive : nodes[id].sortPositionInsensitive;
        }
        return -1; // ?
    }

    protected void setBlockBasedFile(boolean useBlockBasedTermsFile) {
        this.useBlockBasedTermsFile = useBlockBasedTermsFile;
    }
        
    @Override
    public boolean termsEqual(int[] termId, MatchSensitivity sensitivity) {
        if (termId.length < 2) 
            return true;
        
        // sensitive compare - just get the sort index
        if (sensitivity.isCaseSensitive()) { 
            int last = nodes[termId[0]].sortPositionSensitive;
            for (int termIdIndex = 1; termIdIndex < termId.length; ++termIdIndex) {
                int cur = nodes[termId[termIdIndex]].sortPositionSensitive;
                if (cur != last) { return false; }
                last = cur;
            }
            return true;
        }
        
        // insensitive compare - get the insensitive sort index
        int last = nodes[termId[0]].sortPositionInsensitive;
        for (int termIdIndex = 1; termIdIndex < termId.length; ++termIdIndex) {
            int cur = nodes[termId[termIdIndex]].sortPositionInsensitive;
            if (cur != last) { return false; }
            last = cur;
        }
        return true;
    }
    
    
    
    private void read(FileChannel fc) throws IOException {
        long fileLength = termsFile.length();
        IntBuffer ib = readFromFileChannel(fc, fileLength);
        
        // convert all strings to the trie structure now.
//        String[] terms = this.terms;
//        this.terms = null;
        this.nodes = new TrieValue[numberOfTerms]; // only store leaf/terminal nodes, this should work?
        
        // now build the insensitive sorting positions..
        // the original code is weirdly slow, see if we can do better.
        
        // Read the sort order arrays
        int[] sortPositionSensitive = new int[numberOfTerms];
        int[] sortPositionInsensitive = new int[numberOfTerms]; // to use this - retrieve the sensitive id from the <collationkey, integer> map, then use that id as index in this array
        ib.position(ib.position() + numberOfTerms); // Advance past unused sortPos -> id array (left in there for file compatibility)
        ib.get(sortPositionSensitive);
        ib.position(ib.position() + numberOfTerms); // Advance past unused sortPos -> id array (left in there for file compatibility)
        ib.get(sortPositionInsensitive);
        
//        ib = null; // garbage collect option
        
        
        
        // 1. create mapping of insensitive terms to their sensitive ids.
        // (there is no such thing as an insensitive term id)
        for (int sensitiveTermId = 0; sensitiveTermId < terms.length; ++sensitiveTermId) {
            final int curSensitiveTermId = sensitiveTermId;
            final CollationKey ck = collatorInsensitive.getCollationKey(terms[sensitiveTermId]);
            insensitiveTermToIds.computeIfAbsent(ck, __ -> new MutableIntSetFactoryImpl().of()).add(curSensitiveTermId);
        }
        
        // 2. create mapping of sensitive terms to their metadata (id etc) 
        RadixTree<TrieValue> termsTrie = new ConcurrentRadixTree<TermsReaderTrie.TrieValue>(new DefaultCharSequenceNodeFactory());
        long offset = 0;
        int length = 0;
        for (int i = 0; i < terms.length; ++i) {
            final String term = terms[i];
            length = term.getBytes().length;
            
            TrieValue tv = offset < Integer.MAX_VALUE 
                ? new TrieValueSmall(
                    (int) offset, 
                    length,
                    i,
                    sortPositionSensitive[i], sortPositionInsensitive[i]
                )
                : new TrieValueLarge(
                    offset, 
                    length,
                    i,
                    sortPositionSensitive[i], sortPositionInsensitive[i]
                );
                    
           

           this.nodes[i] = tv;
           if (length > 0)
               termsTrie.put(term,  tv);
           else 
               zeroLengthTermId = i;
           offset += length;
        }
        
        // 3. collapse all term strings into a arrays of bytes (one per 2gb of term data)
        // TODO perhaps we should find a way to share these strings with other forward indexes
        // but that would require us to store String instances, not ideal.
        long totalTermsLengthRemaining = offset;
        offset = 0; // reuse
        length = 0;
        for (int termIndex = 0; termIndex < terms.length; ++termIndex) {
            final long curArrayLength = Math.min(totalTermsLengthRemaining, Integer.MAX_VALUE); // this is safe, but wonky cast required.
            final byte[] curArray = new byte[(int) curArrayLength];
            
            offset = 0;
            while (termIndex < terms.length) {
                byte[] curTerm = terms[termIndex].getBytes(StandardCharsets.UTF_8);
                if (offset + curTerm.length >= Integer.MAX_VALUE) {
                    --termIndex; // we didn't handle this term - redo it (with new array allocated above)
                    break;
                }
                
                System.arraycopy(curTerm, 0, curArray, (int) offset, curTerm.length);
                // free the string memory, update the current offset and index of the term we're copying
                terms[termIndex] = null;
                offset += curTerm.length;
                ++termIndex;
                totalTermsLengthRemaining -= curTerm.length;
            }
            
            // array finished, move into TrieValue static storage.
            final byte[][] newtermsarrayutf8 = new byte[termsarrayutf8 != null ? termsarrayutf8.length + 1 : 1][];
            if (termsarrayutf8 != null) // copy existing arrays
                System.arraycopy(newtermsarrayutf8, 0, termsarrayutf8, 0, termsarrayutf8.length);
            newtermsarrayutf8[newtermsarrayutf8.length - 1] = curArray;
            termsarrayutf8 = newtermsarrayutf8;
        }
    }
}
