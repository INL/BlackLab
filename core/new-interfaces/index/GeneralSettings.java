package nl.inl.blacklab.interfaces.index;

/**
 * Settings that apply to a whole searching and are not overridable
 * per search.
 */
public interface GeneralSettings {
	
	// Log settings
	
	boolean traceIndexOpening();
	
	boolean traceQueryOptimization();
	
	boolean traceQueryExecution();
	

}
