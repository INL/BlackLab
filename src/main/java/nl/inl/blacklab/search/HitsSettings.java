package nl.inl.blacklab.search;

import java.util.Collection;

public class HitsSettings {

	/**
	 * Stop retrieving hits after this number.
	 * (NO_LIMIT = -1 = don't stop retrieving)
	 */
	private int maxHitsToRetrieve;

	/**
	 * Stop counting hits after this number.
	 * (NO_LIMIT = -1 = don't stop counting)
	 */
	private int maxHitsToCount;

	/** What to use to make concordances: forward index or content store */
	private ConcordanceType concsType;

	/**
	 * The default field to use for retrieving concordance information.
	 */
	private String concordanceFieldName;

	/** Forward index to use as text context of &lt;w/&gt; tags in concordances (words; null = no text content) */
	private String concWordProps;

	/** Forward index to use as text context between &lt;w/&gt; tags in concordances (punctuation+whitespace; null = just a space) */
	private String concPunctProps;

	/** Forward indices to use as attributes of &lt;w/&gt; tags in concordances (null = the rest) */
	private Collection<String> concAttrProps; // all other FIs are attributes

	/** Our desired context size */
	private int desiredContextSize;

	@SuppressWarnings("deprecation")
	public HitsSettings(HitsSettings defaults, boolean staticHitsFieldsOverrideDefaults) {
		concordanceFieldName = defaults.concordanceFieldName;
		maxHitsToRetrieve = (staticHitsFieldsOverrideDefaults && Hits.defaultMaxHitsToRetrieveChanged) ? Hits.defaultMaxHitsToRetrieve : defaults.maxHitsToRetrieve;
		maxHitsToCount = (staticHitsFieldsOverrideDefaults && Hits.defaultMaxHitsToCountChanged) ? Hits.defaultMaxHitsToCount : defaults.maxHitsToCount;
		concsType = defaults.concsType;
		concWordProps = defaults.concWordProps;
		concPunctProps = defaults.concPunctProps;
		concAttrProps = defaults.concAttrProps;
		desiredContextSize = defaults.desiredContextSize;
	}

	public HitsSettings() {
		this.concordanceFieldName = Searcher.DEFAULT_CONTENTS_FIELD_NAME;
		maxHitsToRetrieve = Searcher.DEFAULT_MAX_RETRIEVE;
		maxHitsToCount = Searcher.DEFAULT_MAX_COUNT;
		concsType = Searcher.DEFAULT_CONC_TYPE;
		concWordProps = Searcher.DEFAULT_CONC_WORD_PROP;
		concPunctProps = Searcher.DEFAULT_CONC_PUNCT_PROP;
		concAttrProps = Searcher.DEFAULT_CONC_ATTR_PROP;
		desiredContextSize = Searcher.DEFAULT_CONTEXT_SIZE;
	}

	/** @return the maximum number of hits to retrieve. */
	public int maxHitsToRetrieve() {
		return maxHitsToRetrieve;
	}

	/** Set the maximum number of hits to retrieve
	 * @param n the number of hits, or HitsSettings.UNLIMITED for no limit
	 */
	public void setMaxHitsToRetrieve(int n) {
		this.maxHitsToRetrieve = n;
	}

	/** @return the maximum number of hits to count. */
	public int maxHitsToCount() {
		return maxHitsToCount;
	}

	/** Set the maximum number of hits to count
	 * @param n the number of hits, or HitsSettings.UNLIMITED for no limit
	 */
	public void setMaxHitsToCount(int n) {
		this.maxHitsToCount = n;
	}

	/**
	 * Are we making concordances using the forward index (true) or using
	 * the content store (false)? Forward index is more efficient but returns
	 * concordances that don't include XML tags.
	 *
	 * @return true iff we use the forward index for making concordances.
	 */
	public ConcordanceType concordanceType() {
		return concsType;
	}

	/**
	 * Do we want to retrieve concordances from the forward index or from the
	 * content store? Forward index is more efficient but doesn't exactly reproduces the
	 * original XML.
	 *
	 * The default type can be set by calling Searcher.setDefaultConcordanceType().
	 *
	 * @param type the type of concordances to make
	 */
	public void setConcordanceType(ConcordanceType type) {
		this.concsType = type;
	}

	/**
	 * Returns the field to use for retrieving concordances.
	 *
	 * @return the field name
	 */
	public String concordanceField() {
		return concordanceFieldName;
	}

	/**
	 * Sets the field to use for retrieving concordances.
	 *
	 * @param concordanceFieldName
	 *            the field name
	 */
	public void setConcordanceField(String concordanceFieldName) {
		this.concordanceFieldName = concordanceFieldName;
	}

	/**
	 * Indicate what properties to use to build concordances.
	 *
	 * This configuration is only valid when using forward indices to build concordances.
	 *
	 * @param wordFI FI to use as the text content of the &lt;w/&gt; tags (default "word"; null for no text content)
	 * @param punctFI FI to use as the text content between &lt;w/&gt; tags (default "punct"; null for just a space)
	 * @param attrFI FIs to use as the attributes of the &lt;w/&gt; tags (null for all other FIs)
	 */
	public void setConcordanceProperties(String wordFI, String punctFI, Collection<String> attrFI) {
		concWordProps = wordFI;
		concPunctProps = punctFI;
		concAttrProps = attrFI;
	}

	public String concWordProp() {
		return concWordProps;
	}

	public String concPunctProp() {
		return concPunctProps;
	}

	public Collection<String> concAttrProps() {
		return concAttrProps;
	}

	public int contextSize() {
		return desiredContextSize;
	}

	public void setContextSize(int n) {
		desiredContextSize = n;
	}

}
