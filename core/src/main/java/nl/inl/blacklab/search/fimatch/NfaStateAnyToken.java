package nl.inl.blacklab.search.fimatch;

public class NfaStateAnyToken extends NfaStateToken {

	public NfaStateAnyToken(NfaState nextState) {
		super(0, ANY_TOKEN, nextState);
	}

}
