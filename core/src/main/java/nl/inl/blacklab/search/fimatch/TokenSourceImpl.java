package nl.inl.blacklab.search.fimatch;

import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.forwardindex.ForwardIndex;

/** Source of tokens for the forward index matching process. */
class TokenSourceImpl extends TokenSource {

	/** Default size for our chunks */
	private static final int CHUNK_SIZE = 10;

	/** Forward indices */
	private List<ForwardIndex> fis;

	/** Forward index id of the document we're looking at*/
	private int fiid;

	/** Number of tokens in document. */
	private int docLengthTokens;

	/** Chunks of the document from the forward index, for each of the properties. */
	private List<List<int[]>> allPropChunks = new ArrayList<>();

	/** Helper array for fetching chunks. Allocate it once to save time. */
	private int[] starts = new int[] {0};

	/** Helper array for fetching chunks. Allocate it once to save time. */
	private int[] ends = new int[] {0};

	public TokenSourceImpl(List<ForwardIndex> fis, int fiid) {
		this.fis = fis;
		this.fiid = fiid;
		this.docLengthTokens = fis.get(0).getDocLength(fiid);

		// Create empty lists of chunks for each property
		for (int i = 0; i < fis.size(); i++) {
			allPropChunks.add(new ArrayList<int[]>());
		}
	}

	@Override
	public int getToken(int propIndex, int pos) {
		if (pos < 0 || pos >= docLengthTokens)
			return -1;

		// Get the list of chunks for the property we're interested in,
		// and the forward index object to get more.
		List<int[]> chunks = allPropChunks.get(propIndex);

		// Where can our token be found?
		int whichChunk = pos / CHUNK_SIZE;
		int posWithinChunk = pos % CHUNK_SIZE;

		// Make sure we have the chunk we need:
		// First, make sure the list is long enough.
		// (we fill with nulls to avoid fetching chunks we don't need)
		while (chunks.size() <= whichChunk)
			chunks.add(null);
		// Now, see if we have the chunk we want, and fetch it if not
		int[] chunk = chunks.get(whichChunk);
		if (chunk == null) {
			chunk = fetchChunk(propIndex, whichChunk);
			chunks.set(whichChunk, chunk);
		}

		return chunk[posWithinChunk];

	}

	/**
	 * Fetch a chunk from the forward index for the specified property.
	 * @param propIndex which property we want a forward index chunk for
	 * @param number the chunk number to fetch
	 * @return the chunk
	 */
	protected int[] fetchChunk(int propIndex, int number) {
		int chunkStart = number * CHUNK_SIZE;
		starts[0] = chunkStart;
		ends[0] = chunkStart + CHUNK_SIZE;
		if (ends[0] > docLengthTokens) {
			ends[0] = docLengthTokens;
		}
		return fis.get(propIndex).retrievePartsInt(fiid, starts, ends).get(0);
	}

}
