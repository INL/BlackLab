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
package nl.inl.blacklab.contentstore;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Set;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.util.VersionFile;

/**
 * Store string content by integer id. Quickly retrieve (parts of) the string
 * content.
 */
public abstract class ContentStore {

    static final Charset DEFAULT_CHARSET = Charset.forName("utf-8");

    public static ContentStore open(File indexXmlDir, boolean indexMode, boolean create) throws ErrorOpeningIndex {
        String type;
        if (create)
            type = "fixedblock";
        else {
            VersionFile vf = ContentStoreDirAbstract.getStoreTypeVersion(indexXmlDir);
            type = vf.getType();
        }
        if (type.equals("fixedblock")) {
            if (indexMode)
                return new ContentStoreFixedBlockWriter(indexXmlDir, create);
            if (create)
                throw new UnsupportedOperationException("create == true, but not in index mode");
            return new ContentStoreFixedBlockReader(indexXmlDir);
        }
        if (type.equals("utf8zip"))
            return new ContentStoreDirZip(indexXmlDir, create);
        if (type.equals("utf8"))
            return new ContentStoreDirUtf8(indexXmlDir, create);
        if (type.equals("utf16")) {
            throw new UnsupportedOperationException("UTF-16 content store is deprecated. Please re-index your data.");
        }
        throw new UnsupportedOperationException("Unknown content store type " + type);
    }

    /** A task to perform on a document in the content store. */
    public interface DocTask {
        void perform(int cid, String contents);
    }

    /**
     * Store a document.
     *
     * It is possible to first call storePart() several times, as long as you finish
     * with a call to store. The parameter may be the empty string if you wish.
     *
     * @param content (part of) the content of the document to store
     * @return the content store id assigned to the document
     */
    public abstract int store(String content);

    public abstract int store(byte[] content, int offset, int length, Charset cs);
    
    
    /**
     * Store part of a large document.
     *
     * You can call this several times, but it must end with a call to store() or
     * the document isn't properly stored.
     *
     * @param content part of the content of the document to store
     */
    public abstract void storePart(String content);
    
    public abstract void storePart(byte[] content, int offset, int length, Charset cs); 

    /**
     * Retrieve a document from the content store.
     * 
     * @param id the document's content store id
     * @return the original content
     */
    public abstract String retrieve(int id);

    /**
     * Retrieve one or more substrings from the specified content.
     *
     * This is more efficient than retrieving the whole content, or retrieving parts
     * in separate calls, because the file is only opened once and random access is
     * used to read only the required parts.
     *
     * NOTE: if offset and length are both -1, retrieves the whole content. This is
     * used by the retrieve(id) method.
     *
     * @param id id of the entry to get substrings from
     * @param start the starting points of the substrings (in characters). -1 means
     *            "start of document"
     * @param end the end points of the substrings (in characters). -1 means "end of
     *            document"
     * @return the parts
     */
    public String retrievePart(int id, int start, int end) {
        return retrieveParts(id, new int[] { start }, new int[] { end })[0];
    }

    /**
     * Retrieve substrings from a document.
     *
     * @param id content store document id
     * @param start start of the substring
     * @param end end of the substring
     * @return the substrings
     */
    public abstract String[] retrieveParts(int id, int[] start, int[] end);

    /**
     * Close the content store
     */
    public abstract void close();

    /**
     * Delete a document from the content store.
     * 
     * @param id content store id of the document to delete
     */
    public abstract void delete(int id);

    /**
     * Clear the entire content store.
     * @throws IOException 
     */
    public abstract void clear() throws IOException;

    /**
     * Returns the set of doc ids in the store. Note that the IDs of deleted
     * document are still returned by this method. Use isDeleted() to check.
     * 
     * @return the set of doc ids
     */
    public abstract Set<Integer> idSet();

    /**
     * Return true iff the entry with this id was deleted.
     * 
     * @param id the entry to check
     * @return true iff deleted
     */
    public abstract boolean isDeleted(int id);

    /**
     * Returns the document length in characters
     * 
     * @param id the document
     * @return the length in characters
     */
    public abstract int docLength(int id);

    /**
     * Perform a task on each document in the content store.
     * 
     * @param task the task to perform
     */
    public void forEachDocument(DocTask task) {
        idSet().stream().forEach(cid -> task.perform(cid, retrieve(cid)));
    }

    public abstract void initialize();

}
