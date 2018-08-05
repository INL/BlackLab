package nl.inl.blacklab.interfaces.results;

/**
 * Statistics about hits and docs.
 * 
 * E.g. Hits will count the number of documents as it goes.
 */
public interface ResultsStatsHitsDocs {
	ResultsStats hits();
	ResultsStats docs();
}
