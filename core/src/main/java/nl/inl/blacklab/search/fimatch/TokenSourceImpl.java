package nl.inl.blacklab.search.fimatch;

import java.util.List;

import nl.inl.blacklab.forwardindex.ForwardIndex;

/** Source of tokens for the forward index matching process. */
class TokenSourceImpl extends TokenSource {

	private List<ForwardIndex> fis;

	private int fiid;

	public TokenSourceImpl(List<ForwardIndex> fis, int fiid, int startingPosition, int direction) {
		super(startingPosition, direction);
		this.fis = fis;
		this.fiid = fiid;
	}

	@Override
	public int getToken(int propIndex, int pos) {
		if (!validPos(pos)) // SLOOOW!
			return -1;
		int realPos = startingPosition + pos;
		int[] starts = new int[] {realPos};
		int[] ends = new int[] {realPos + 1};
		return fis.get(propIndex).retrievePartsInt(fiid, starts, ends).get(0)[0]; // SLOOOW!
	}

	@Override
	public boolean validPos(int pos) {
		return pos >= 0 && pos < fis.get(0).getDocLength(fiid);
	}

}
