package nl.inl.blacklab.search.fimatch;

import java.util.Collection;
import java.util.Map;

public class NfaStateAnyToken extends NfaStateToken {

	public NfaStateAnyToken(NfaState nextState) {
		super(0, ANY_TOKEN, nextState, "ANY");
	}

	@Override
	protected String dumpInternal(Map<NfaState, Integer> stateNrs) {
		return "ANY(" + dump(nextState, stateNrs) + ")";
	}

	@Override
	NfaStateToken copyInternal(Collection<NfaState> dangling, Map<NfaState, NfaState> copiesMade) {
		NfaStateToken copy = new NfaStateAnyToken(nextState);
		if (nextState == null)
			dangling.add(copy);
		return copy;
	}

}
