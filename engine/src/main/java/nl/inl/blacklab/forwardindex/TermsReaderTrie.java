package nl.inl.blacklab.forwardindex;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.text.CollationKey;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import gnu.trove.map.hash.THashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class TermsReaderTrie extends Terms {
    private static class TrieValue {
        public final String term; 
        public final int indexSensitive;
        public final int sortPositionInsensitive; // this is stored in the terms file
        public final int sortPositionSensitive; // this is stored in the terms file
        
        public TrieValue(String term, int indexSensitive, int sortPositionSensitive, int sortPositionInsensitive) {
            this.term = term;
            
            this.indexSensitive = indexSensitive;
            this.sortPositionSensitive = sortPositionSensitive;
            this.sortPositionInsensitive = sortPositionInsensitive;
        }
        
        public String getTerm() {
            return term;
        }
    }
    
    protected static final Logger logger = LogManager.getLogger(TermsReaderTrie.class);

//    protected boolean initialized = false;
    protected final File termsFile;
    protected final Map<String, TrieValue> sensitiveTermToId = new HashMap<>();
//    protected final RadixTree<TrieValue> termsTrie = new ConcurrentRadixTree<TermsReaderTrie.TrieValue>(new DefaultCharSequenceNodeFactory());
    
//    public byte[][] termsarrayutf8;
    private TrieValue[] nodes;
    private TObjectIntHashMap<CollationKey> insensitiveTermToSingleId = new TObjectIntHashMap<>(); // split these so we avoid the boxing memory price for single entry ints (which are the vast majority) 
    private THashMap<CollationKey, IntArrayList> insensitiveTermToMultipleIds = new THashMap<>(); 
//    private int zeroLengthTermId = -1;
    
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
        TrieValue v = sensitiveTermToId.get(term);
        return v != null ? v.indexSensitive : -1;
    }

    @Override
    public void indexOf(MutableIntSet results, String term, MatchSensitivity sensitivity) {
        if (sensitivity.isCaseSensitive()) {
            TrieValue v = sensitiveTermToId.get(term);
            results.add(v != null ? v.indexSensitive : -1);
        }

        CollationKey insensitiveId = collatorInsensitive.getCollationKey(term);
        if (insensitiveTermToSingleId.containsKey(insensitiveId)) results.addAll(insensitiveTermToSingleId.get(insensitiveId));
        else if (insensitiveTermToMultipleIds.containsKey(insensitiveId)) results.addAll(insensitiveTermToMultipleIds.get(insensitiveId));
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
        ib = null; // garbage collect option
        
        // 1. create mapping of insensitive terms to their sensitive ids.
        // (there is no such thing as an insensitive term id)
        for (int sensitiveTermId = 0; sensitiveTermId < terms.length; ++sensitiveTermId) {
            final CollationKey ck = collatorInsensitive.getCollationKey(terms[sensitiveTermId]);
            // check if present in multiple
            if (insensitiveTermToMultipleIds.containsKey(ck)) insensitiveTermToMultipleIds.get(ck).add(sensitiveTermId);
            else if (!insensitiveTermToSingleId.containsKey(ck)) insensitiveTermToSingleId.put(ck, sensitiveTermId);
            else { // migrate from single to multi value store
                int alreadyPresentSensitiveTermId = insensitiveTermToSingleId.remove(ck);
                insensitiveTermToMultipleIds.put(ck, new IntArrayList(alreadyPresentSensitiveTermId, sensitiveTermId));
            }
        }
        
        // 2. create mapping of sensitive terms to their metadata (id etc) 
        for (int i = 0; i < terms.length; ++i) {
            final String term = terms[i];
            TrieValue tv = new TrieValue(term, i, sortPositionSensitive[i], sortPositionInsensitive[i]);
            this.nodes[i] = tv;
        }
        
        this.terms = null; // no need anymore?
    }
}
