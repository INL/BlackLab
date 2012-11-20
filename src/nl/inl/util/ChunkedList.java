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
package nl.inl.util;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Memory-efficient list of objects.
 *
 * Works by keeping a list of chunks. Each chunk is an array of a fixed size (the chunk size).
 * Together the chunks form the storage for the list.
 *
 * Add and remove operations in the middle of the list are relatively expensive as we may have to
 * shift elements over several chunks. Add and remove at the end should be fast, though.
 *
 * Advantage over ArrayList is better storage characteristics and the fact we don't have to
 * reallocate when the array runs out. Disadvantage is slower index-lookup speed (first we have to
 * determine the chunk the element is in). Iteration is pretty fast, though.
 */
public class ChunkedList<T> extends AbstractList<T> {
	final static int DEFAULT_CHUNK_SIZE = 250000; // 1M per array

	List<T[]> chunks = new ArrayList<T[]>();

	/**
	 * How many int elements are in each chunk
	 */
	int chunkSize;

	/**
	 * The size of the list (not the capacity of the chunks, the actual filled size).
	 */
	int size = 0;

	public ChunkedList(int chunkSize) {
		this.chunkSize = chunkSize;
	}

	public ChunkedList() {
		this(DEFAULT_CHUNK_SIZE);
	}

	public ChunkedList(Collection<T> coll) {
		this();
		addAll(coll);
	}

	public ChunkedList(Collection<T> coll, int chunkSize) {
		this(chunkSize);
		addAll(coll);
	}

	/** Iterator class for ChunkedIntList */
	public class ChunkedListIterator implements Iterator<T> {

		/** Current element index */
		int index;

		/** The chunk we're currently iterating through */
		T[] currentChunk;

		/** Chunk iterator */
		Iterator<T[]> currentChunkIt;

		/** The position of the current element inside the chunk */
		int posInChunk;

		public ChunkedListIterator() {
			index = -1;
			currentChunkIt = chunks.iterator();
			currentChunk = currentChunkIt.next();
			posInChunk = -1;
		}

		@Override
		public boolean hasNext() {
			return index < size - 1;
		}

		@Override
		public T next() {
			index++;
			if (index >= size) {
				throw new NoSuchElementException();
			}
			posInChunk++;
			if (posInChunk == chunkSize) {
				currentChunk = currentChunkIt.next();
				posInChunk = 0;
			}
			return currentChunk[posInChunk];
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

	}

	@Override
	public Iterator<T> iterator() {
		return new ChunkedListIterator();
	}

	@Override
	public T get(int index) {
		if (index < 0 || index >= size)
			throw new IndexOutOfBoundsException();
		int chunkNo = index / chunkSize;
		int indexInChunk = index % chunkSize;
		return chunks.get(chunkNo)[indexInChunk];
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public T set(int index, T element) {
		if (index < 0 || index >= size)
			throw new IndexOutOfBoundsException();
		int chunkNo = index / chunkSize;
		int indexInChunk = index % chunkSize;
		T prevEl = chunks.get(chunkNo)[indexInChunk];
		chunks.get(chunkNo)[indexInChunk] = element;
		return prevEl;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void add(int index, T element) {
		if (index < 0 || index > size)
			throw new IndexOutOfBoundsException();

		// See if all chunks are full
		if (size / chunkSize == chunks.size()) {
			// Yes, we need a new one
			// NOTE: cannot instantiate generic arrays in Java. So we
			//  create an Object[] and (unsafely) cast it. Because the
			//  array is never returned to the client, this is okay.
			chunks.add((T[]) new Object[chunkSize]);
		}

		// Move elements to make room
		int targetChunk = index / chunkSize;
		int indexInTargetChunk = index % chunkSize;
		int chunkContainingLastElement = (size - 1) / chunkSize;
		int chunkContainingFirstUnusedSlot = size / chunkSize;
		for (int i = chunkContainingLastElement; i >= targetChunk; i--) {
			T[] chunk = chunks.get(i);

			// Find chunk size
			int thisChunkSize = chunkSize;
			if (i == chunkContainingFirstUnusedSlot) {
				// Chunk is not full.
				thisChunkSize = size % chunkSize;
			} else {
				// Full chunk. Move last element in chunk to next chunk
				chunks.get(i + 1)[0] = chunk[chunkSize - 1];
			}

			// Move rest of the elements in chunk one position forward
			int srcPos = 0;
			if (i == targetChunk)
				srcPos = indexInTargetChunk;
			int length = thisChunkSize - srcPos - (i == chunkContainingFirstUnusedSlot ? 0 : 1);
			if (length > 0)
				System.arraycopy(chunk, srcPos, chunk, srcPos + 1, length);
		}

		// Finally, set the element and increment the list size
		chunks.get(targetChunk)[indexInTargetChunk] = element;
		size++;
	}

	@Override
	public T remove(int index) {
		if (index < 0 || index >= size)
			throw new IndexOutOfBoundsException();

		// Move elements to make room
		int targetChunk = index / chunkSize;
		int indexInTargetChunk = index % chunkSize;
		int chunkContainingLastElement = (size - 1) / chunkSize;
		int chunkContainingFirstUnusedSlot = size / chunkSize;

		// Save deleted element to return it
		T deleted = chunks.get(targetChunk)[indexInTargetChunk];

		// Move elements backward
		for (int i = targetChunk; i <= chunkContainingLastElement; i++) {
			T[] chunk = chunks.get(i);

			// Find chunk size
			int thisChunkSize = chunkSize;
			if (i == chunkContainingFirstUnusedSlot) {
				// Chunk is not full.
				thisChunkSize = size % chunkSize;
			}

			// Move elements in chunk one position backward
			int srcPos = 1;
			if (i == targetChunk)
				srcPos = indexInTargetChunk + 1;
			int length = thisChunkSize - srcPos;
			if (length > 0)
				System.arraycopy(chunk, srcPos, chunk, srcPos - 1, length);

			if (i != chunkContainingLastElement) {
				// Next chunk has at least one element. Move first element to last position of this
				// chunk.
				chunk[chunkSize - 1] = chunks.get(i + 1)[0];
			}
		}

		size--;

		return deleted;
	}

}
