package nl.inl.blacklab.search.matchfilter;

import java.util.Arrays;

import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.lucene.HitQueryContext;

public class MatchFilterSameTokens extends MatchFilter {
	private String propertyName;

	private int propIndex = -1;

	private String[] groupName;

	private int[] groupIndex;

	private boolean caseSensitive;

	private boolean diacSensitive;

	public MatchFilterSameTokens(String leftGroup, String rightGroup, String propertyName, boolean caseSensitive, boolean diacSensitive) {
		this.groupName = new String[] {leftGroup, rightGroup};
		this.groupIndex = new int[2];

		this.propertyName = propertyName;
		this.caseSensitive = caseSensitive;
		this.diacSensitive = diacSensitive;
	}

	@Override
	public String toString() {
		String propPart = propertyName == null ? "" : "." + propertyName;
		return groupName[0] + propPart + " = " + groupName[1] + propPart;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (caseSensitive ? 1231 : 1237);
		result = prime * result + (diacSensitive ? 1231 : 1237);
		result = prime * result + Arrays.hashCode(groupName);
		result = prime * result + ((propertyName == null) ? 0 : propertyName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MatchFilterSameTokens other = (MatchFilterSameTokens) obj;
		if (caseSensitive != other.caseSensitive)
			return false;
		if (diacSensitive != other.diacSensitive)
			return false;
		if (!Arrays.equals(groupName, other.groupName))
			return false;
		if (propertyName == null) {
			if (other.propertyName != null)
				return false;
		} else if (!propertyName.equals(other.propertyName))
			return false;
		return true;
	}

	@Override
	public void setHitQueryContext(HitQueryContext context) {
		groupIndex = new int[2];
		for (int i = 0; i < 2; i++)
			groupIndex[i] = context.registerCapturedGroup(groupName[i]);
	}

	@Override
	public ConstraintValue evaluate(ForwardIndexDocument fiDoc, Span[] capturedGroups) {
		int[] termId = new int[2];
		for (int i = 0; i < 2; i++) {
			Span span = capturedGroups[groupIndex[i]];
			if (span == null)
				return ConstraintValue.get(false); // if either side is undefined, they are not equal
			int tokenPosition = span.getStart();
			if (propIndex < 0)
				termId[i] = tokenPosition;
			else
				termId[i] = fiDoc.getToken(propIndex, tokenPosition);
		}
		if (caseSensitive && diacSensitive)
			return ConstraintValue.get(termId[0] == termId[1]);
		// (Somewhat) insensitive; let Terms determine if term ids have the same sort position or not
		return ConstraintValue.get(fiDoc.termsEqual(propIndex, termId, caseSensitive, diacSensitive));
	}

	@Override
	public void lookupPropertyIndices(ForwardIndexAccessor fiAccessor) {
		if (propertyName != null) {
			propIndex = fiAccessor.getPropertyNumber(propertyName);
		}
	}

	@Override
	public MatchFilter rewrite() {
		return this;
	}

}
