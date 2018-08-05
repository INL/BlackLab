package nl.inl.blacklab.interfaces.results;

/**
 * Information about the number of results that are being (or were) processed and counted.
 * 
 * If you're searching for hits, and have set a maximum of 1000 hits, processed().total()
 * might get up to a 1000 and stop, while counted().total() will keep counting until 
 * all hits have been counted (or the maximum number of hits to count is reached).
 */
public interface ResultsStats {
	ResultsNumber processed();
	ResultsNumber counted();
}
