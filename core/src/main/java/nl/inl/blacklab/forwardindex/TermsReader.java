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
package nl.inl.blacklab.forwardindex;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.text.CollationKey;
import java.text.Collator;
import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.collections.api.set.primitive.MutableIntSet;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Keeps a first-come-first-serve list of unique terms. Each term gets a unique
 * index number. These numbers are stored in the forward index to conserve space
 * and allow quick lookups of terms occurring in specific positions.
 *
 * This version of the class stores the terms in a more efficient way so it
 * saves and loads faster, and includes the case-insensitive sorting order.
 * 
 * This implementation is thread-safe.
 */
class TermsReader extends Terms {

    protected static final Logger logger = LogManager.getLogger(TermsReader.class);

    /**
     * Mapping from term to its unique index number.
     */
    Reference2IntMap<String> termIndex;

    /**
     * The first index in the sortPositionPerIdInsensitive[] array that matches each
     * term, and the number of matching terms that follow. Used while building NFAs
     * to quickly fetch all indices matching a term case-insensitively.
     * 
     * The most significant 4 bytes of the long are the first term; the least significant
     * 4 bytes are the number of terms.
     */
    Object2LongMap<CollationKey> termIndexInsensitive;

    /**
     * The index number of each sorting position. Inverse of sortPositionPerId[]
     * array.
     */
    int[] idPerSortPosition;

    /**
     * The index number of each case-insensitive sorting position. Inverse of
     * sortPositionPerIdInsensitive[] array.
     */
    int[] idPerSortPositionInsensitive;

    /**
     * The sorting position for each index number. Inverse of idPerSortPosition[]
     * array.
     */
    int[] sortPositionPerId;

    /**
     * The case-insensitive sorting position for each index number.
     */
    int[] sortPositionPerIdInsensitive;

    /** If true, termIndex is a valid mapping from term to term id. */
    private boolean initialized;

    private File termsFile;

    TermsReader(Collators collators, File termsFile, boolean useBlockBasedTermsFile) {
        this.collator = collators.get(MatchSensitivity.SENSITIVE);
        this.collatorInsensitive = collators.get(MatchSensitivity.INSENSITIVE);

        // We already have the sort order, so TreeMap is not necessary here.
        this.termIndex = null;
        this.termIndexInsensitive = null;

        initialized = false;
        setBlockBasedFile(useBlockBasedTermsFile);
        
        if (termsFile == null || !termsFile.exists())
            throw new IllegalArgumentException("Terms file not found: " + termsFile);
        this.termsFile = termsFile;
    }

    @Override
    public int indexOf(String term) {

        // If we havent' filled termIndex based on terms[] yet, do so now.
        // (done automatically on open in background thread, so this shouldn't normally block)
        if (!initialized)
            initialize();
        
        int index = termIndex.getInt(term.intern());
        if (index == -1)
            return NO_TERM; // term not found
        return index;
    }

    @Override
    public void indexOf(MutableIntSet results, String term, MatchSensitivity sensitivity) {
        if (!initialized)
            initialize();
        
        // Make sure idPerSortPosition[Insensitive] is initialized
        // (we do this lazily because we rarely or never need this for some FIs, and it takes up a significant amount of memory)
        buildIdPerSortPosition();
        
        // NOTE: we don't do diacritics and case-sensitivity separately, but could in the future.
        //  right now, diacSensitive is ignored and caseSensitive is used for both.
        boolean caseSensitive = sensitivity.isCaseSensitive();
        int[] idLookup = caseSensitive ? idPerSortPosition : idPerSortPositionInsensitive;
        Collator coll = caseSensitive ? collator : collatorInsensitive;

        // Yes, use the available term index.
        // NOTE: insensitive index is only available in search mode.
        if (caseSensitive) {
            // Case-/accent-sensitive. Look up the term's id.
            results.add(termIndex.getInt(term.intern()));
        } else if (termIndexInsensitive != null) {
            // Case-/accent-insensitive. Find the relevant stretch of sort positions and look up the corresponding ids.
            CollationKey key = coll.getCollationKey(term);
            long firstAndNumber = termIndexInsensitive.getLong(key);
            if (firstAndNumber != -1) {
                int start = (int)(firstAndNumber >>> 32);
                int end = start + (int)firstAndNumber;
                for (int i = start; i < end; i++) {
                    results.add(idLookup[i]);
                }
            }
        }
    }

    @Override
    public boolean termsEqual(int[] termId, MatchSensitivity sensitivity) {
        if (!initialized)
            initialize();
        // NOTE: we don't do diacritics and case-sensitivity separately, but could in the future.
        //  right now, diacSensitive is ignored and caseSensitive is used for both.
        int[] idLookup = sensitivity.isCaseSensitive() ? sortPositionPerId : sortPositionPerIdInsensitive;
        int id0 = idLookup[termId[0]];
        for (int i = 1; i < termId.length; i++) {
            if (termId[i] == -1 || id0 != idLookup[termId[i]])
                return false;
        }
        return true;
    }

    @Override
    public synchronized void initialize() {
        if (initialized)
            return;
        
        // Read the terms file
        read(termsFile);

        termIndex = new Reference2IntOpenHashMap<>(terms.length);
        termIndex.defaultReturnValue(-1);
        termIndexInsensitive = new Object2LongOpenHashMap<>(terms.length);
        termIndexInsensitive.defaultReturnValue(-1);
        
        // Build the case-sensitive term index.
        for (int i = 0; i < numberOfTerms; i++) {
            termIndex.put(terms[i], i);
        }

        if (termIndexInsensitive != null) {
            // Now, store the first index in the sortPositionPerIdInsensitive[] array
            // that matches each term, and the number of matching terms that follow.
            // This can be used while building NFAs to quickly fetch all indices matching
            // a term case-insensitively.
            CollationKey prevTermKey = collatorInsensitive.getCollationKey("");
            int currentFirst = -1;
            int currentNumber = -1;
            for (int i = 0; i < numberOfTerms; i++) {
                String term = terms[idPerSortPositionInsensitive[i]];
                CollationKey termKey = collatorInsensitive.getCollationKey(term);
                if (!termKey.equals(prevTermKey)) {
                    if (currentFirst >= 0) {
                        currentNumber = i - currentFirst;
                        termIndexInsensitive.put(prevTermKey, ((long)currentFirst << 32) | currentNumber );
                    }
                    currentFirst = i;
                    currentNumber = 0;
                    prevTermKey = termKey;
                }
            }
            if (currentFirst >= 0) {
                currentNumber = numberOfTerms - currentFirst;
                termIndexInsensitive.put(prevTermKey, ((long)currentFirst << 32) | currentNumber );
            }
        }

        initialized = true;
    }

    @Override
    public void clear() {
        throw new BlackLabRuntimeException("Cannot clear, not in index mode");
    }

    private synchronized void read(File termsFile) {
        try {
            try (RandomAccessFile raf = new RandomAccessFile(termsFile, "r")) {
                try (FileChannel fc = raf.getChannel()) {
                    long fileLength = termsFile.length();
                    IntBuffer ib = readFromFileChannel(fc, fileLength);
                    
                    // Read the sort order arrays
                    sortPositionPerId = new int[numberOfTerms];
                    sortPositionPerIdInsensitive = new int[numberOfTerms];
                    ib.position(ib.position() + numberOfTerms); // Advance past unused sortPos -> id array (left in there for file compatibility)
                    ib.get(sortPositionPerId);
                    ib.position(ib.position() + numberOfTerms); // Advance past unused sortPos -> id array (left in there for file compatibility)
                    ib.get(sortPositionPerIdInsensitive);

                    buildIdPerSortPosition();
                }
            }
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    private synchronized void buildIdPerSortPosition() {
        if (idPerSortPosition == null) {
            // Invert sortPositionPerId[] array, so we can later do a binary search through our
            // terms to find a specific one. (only needed to deserialize sort/group criteria from URL)
            idPerSortPosition = new int[numberOfTerms];
            idPerSortPositionInsensitive = new int[numberOfTerms];
            Arrays.fill(idPerSortPositionInsensitive, -1);
            for (int i = 0; i < numberOfTerms; i++) {
                idPerSortPosition[sortPositionPerId[i]] = i;
                int x = sortPositionPerIdInsensitive[i];
                // Multiple terms can have the same (case-insensitive)
                // sort position. Skip over previous terms so each term is
                // in the array and we can look at adjacent terms to recover all
                // the terms with the same sort position later.
                while (idPerSortPositionInsensitive[x] >= 0)
                    x++;
                idPerSortPositionInsensitive[x] = i;
            }
        }
    }
    
    @Override
    public void write(File termsFile) {
        throw new BlackLabRuntimeException("Term.write(): not in index mode!");
    }

    @Override
    public String get(int index) {
        if (!initialized)
            initialize();
        assert index >= 0 && index < numberOfTerms : "Term index out of range (" + index + ", numterms = "
                + numberOfTerms + ")";
        return terms[index];
    }

    @Override
    public int numberOfTerms() {
        if (!initialized)
            initialize();
        return numberOfTerms;
    }

    @Override
    public void toSortOrder(int[] tokenId, int[] sortOrder, MatchSensitivity sensitivity) {
        if (!initialized)
            initialize();
        if (sensitivity.isCaseSensitive()) {
            for (int i = 0; i < tokenId.length; i++) {
                if (tokenId[i] == NO_TERM)
                    sortOrder[i] = NO_TERM;
                else
                    sortOrder[i] = sortPositionPerId[tokenId[i]];
            }
        } else {
            for (int i = 0; i < tokenId.length; i++) {
                if (tokenId[i] == NO_TERM)
                    sortOrder[i] = NO_TERM;
                else
                    sortOrder[i] = sortPositionPerIdInsensitive[tokenId[i]];
            }
        }
    }

    @Override
    public int compareSortPosition(int tokenId1, int tokenId2, MatchSensitivity sensitivity) {
        if (!initialized)
            initialize();
        if (sensitivity.isCaseSensitive()) {
            return sortPositionPerId[tokenId1] - sortPositionPerId[tokenId2];
        }
        return sortPositionPerIdInsensitive[tokenId1] - sortPositionPerIdInsensitive[tokenId2];
    }

    @Override
    public int idToSortPosition(int id, MatchSensitivity sensitivity) {
        if (!initialized)
            initialize();
        return sensitivity.isCaseSensitive() ? sortPositionPerId[id] : sortPositionPerIdInsensitive[id];
    }

    @Override
    protected void setBlockBasedFile(boolean useBlockBasedTermsFile) {
        this.useBlockBasedTermsFile = useBlockBasedTermsFile;
    }

}
