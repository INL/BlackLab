package nl.inl.blacklab.forwardindex;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import gnu.trove.map.hash.THashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class TermsReaderTrie extends Terms {
    
    protected static final Logger logger = LogManager.getLogger(TermsReaderTrie.class);

    protected final File termsFile;
    protected final TObjectIntHashMap<String> sensitiveTermToId = new TObjectIntHashMap<>();
    
  // split these so we avoid the boxing memory price for single entry ints (which are the vast majority) 
    private TObjectIntHashMap<byte[]> insensitiveTermToSingleId = new TObjectIntHashMap<byte[]>() {
        protected int hash(Object notnull) { return Arrays.hashCode((byte[]) notnull); }; 
    }; 
    private THashMap<byte[], IntArrayList> insensitiveTermToMultipleIds = new THashMap<byte[], IntArrayList>() { 
        protected int hash(Object notnull) { return Arrays.hashCode((byte[]) notnull); };
    };
    
    protected int[] sortPositionSensitive;
    protected int[] sortPositionInsensitive;
    
    
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
        return sensitiveTermToId.get(term);
    }

    @Override
    public void indexOf(MutableIntSet results, String term, MatchSensitivity sensitivity) {
        if (sensitivity.isCaseSensitive()) {
            results.add(sensitiveTermToId.get(term));
        }

        byte[] insensitiveId = collatorInsensitive.getCollationKey(term).toByteArray();
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
        if (id >= 0 && id < numberOfTerms) {
            return terms[id];
        }
        return ""; // ?
    }

    @Override
    public int numberOfTerms() {
        return numberOfTerms;
    }

    @Override
    public int idToSortPosition(int id, MatchSensitivity sensitivity) {
        if (id >= 0 && id < numberOfTerms) {
            return sensitivity.isCaseSensitive() ? sortPositionSensitive[id] : sortPositionInsensitive[id];
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
            int last = sortPositionSensitive[termId[0]];
            for (int termIdIndex = 1; termIdIndex < termId.length; ++termIdIndex) {
                int cur = sortPositionSensitive[termId[termIdIndex]];
                if (cur != last) { return false; }
                last = cur;
            }
            return true;
        }
        
        // insensitive compare - get the insensitive sort index
        int last = sortPositionInsensitive[termId[0]];
        for (int termIdIndex = 1; termIdIndex < termId.length; ++termIdIndex) {
            int cur = sortPositionInsensitive[termId[termIdIndex]];
            if (cur != last) { return false; }
            last = cur;
        }
        return true;
    }
    
    private void read(FileChannel fc) throws IOException {
        System.out.println("Initializing termsreader " + termsFile);
        final long start = System.nanoTime();
    
        long fileLength = termsFile.length();
        IntBuffer ib = readFromFileChannel(fc, fileLength);
        
        // now build the insensitive sorting positions..
        // the original code is weirdly slow, see if we can do better.
        
        // Read the sort order arrays
        this.sortPositionSensitive = new int[numberOfTerms];
        this.sortPositionInsensitive = new int[numberOfTerms];
        ib.position(ib.position() + numberOfTerms); // Advance past unused sortPos -> id array (left in there for file compatibility)
        ib.get(sortPositionSensitive);
        ib.position(ib.position() + numberOfTerms); // Advance past unused sortPos -> id array (left in there for file compatibility)
        ib.get(sortPositionInsensitive);
        ib = null; // garbage collect option
        
        // 1. create mapping of insensitive terms to their sensitive ids.
        // (there is no such thing as an insensitive term id)
        for (int sensitiveTermId = 0; sensitiveTermId < terms.length; ++sensitiveTermId) {
            final byte[] ck = collatorInsensitive.getCollationKey(terms[sensitiveTermId]).toByteArray();
            
            // check if present in multiple
            if (insensitiveTermToMultipleIds.containsKey(ck)) insensitiveTermToMultipleIds.get(ck).add(sensitiveTermId);
            else if (!insensitiveTermToSingleId.containsKey(ck)) insensitiveTermToSingleId.put(ck, sensitiveTermId);
            else { // migrate from single to multi value store
                int alreadyPresentSensitiveTermId = insensitiveTermToSingleId.remove(ck);
                insensitiveTermToMultipleIds.put(ck, new IntArrayList(alreadyPresentSensitiveTermId, sensitiveTermId));
            }
        }
        
        System.out.println("finishing initializing termsreader" + termsFile + " - " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + "ms to process " + numberOfTerms + " terms");
    }
}
