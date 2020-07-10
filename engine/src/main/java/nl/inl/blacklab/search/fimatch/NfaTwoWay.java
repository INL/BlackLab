package nl.inl.blacklab.search.fimatch;

import java.util.Map;

/** Stores a (partial) NFA and its reverse. */
public class NfaTwoWay {

    private Nfa nfa;

    private Nfa nfaReverse;

    public NfaTwoWay(Nfa nfa, Nfa nfaReverse) {
        this.nfa = nfa;
        this.nfaReverse = nfaReverse;
    }

    public Nfa getNfa() {
        return nfa;
    }

    public Nfa getNfaReverse() {
        return nfaReverse;
    }

    public NfaTwoWay copy() {
        return new NfaTwoWay(nfa.copy(), nfaReverse.copy());
    }

    public void append(NfaTwoWay part) {
        nfa.append(part.getNfa());
        Nfa r = part.getNfaReverse().copy();
        r.append(nfaReverse);
        nfaReverse = r;
    }

    public void lookupPropertyNumbers(ForwardIndexAccessor fiAccessor, Map<NfaState, Boolean> statesVisited) {
        nfa.lookupAnnotationNumbers(fiAccessor, statesVisited);
        nfaReverse.lookupAnnotationNumbers(fiAccessor, statesVisited);
    }

    public void finish() {
        nfa.finish();
        nfaReverse.finish();
    }

}
