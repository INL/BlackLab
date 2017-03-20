package nl.inl.blacklab.search.fimatch;

import java.util.Map;

public class NfaStateAnyToken extends NfaStateToken {

	public NfaStateAnyToken(NfaState nextState) {
		super(0, ANY_TOKEN, nextState);
	}

	@Override
	protected String dumpInternal(Map<NfaState, Integer> stateNrs) {
		return "ANY(" + (nextState == null ? "null" : nextState.dump(stateNrs)) + ")";
	}
	
}
