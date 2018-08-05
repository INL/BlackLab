package nl.inl.blacklab.interfaces;

import java.text.Collator;

/**
 * Settings that can be overridden from the defaults
 * on a per-search basis.
 */
public interface SearchSettings {
	
	// Search settings
	
	int maxHitsToFetch();
	
	MatchSensitivity defaultSensitivity();
	
	Collator collator();
	
	// ... more? ...
	
}
