package nl.inl.blacklab.interfaces.results;

/**
 * A results number.
 * 
 * For example: number of hits processed.
 * 
 * Some more options:
 * - hits or docs
 * - number processed or counted
 * - in the current (e.g. filtered) set or the original set
 * 
 * When done() starts returning true, this will be immutable.
 */
public interface ResultsNumber {
	
	/**
	 * Returns the total number of results (e.g. processed, counted, ...).
	 * 
	 * If necessary, this will block until an answer can be given.
	 * Afterwards, done() will return true and this method and numberSoFar() 
	 * will return the same value.  
	 * 
	 * @return number of results
	 */
	int total();
	
	/**
	 * Tests if there' at least the lower bound of results.
	 * 
	 * If necessary, this will block until an answer can be given.
	 * 
	 * Afterwards, soFar() will return at least lowerBound.
	 * 
	 * @param lowerBound number we're looking for
	 * @return true if there's at least lowerBound results
	 */
	boolean atLeast(int lowerBound);
	
	/**
	 * Returns the number of results so far (e.g. process, counted, ...).
	 * 
	 * If done() returns true, this will be equals to total().
	 * 
	 * @return number of results so far
	 */
	int soFar();
	
	/**
	 * Are we done?
	 * 
	 * If true, total() and atLeast(n) will return immediately;
	 * soFar() will be equal to total() and won't change anymore.
	 * 
	 * @return true if we are, false if not
	 */
	boolean done();
	
	/**
	 * Did we stop because there's too many results?
	 * 
	 * If we reach maximum() results and we're not done yet,
	 * we will stop and this method will start returning true.
	 * 
	 * If true, done() returns true and total(), soFar() and
	 * maximum() will all return the same value. 
	 * 
	 * @return true if we did, false if not
	 */
	boolean exceededMaximum();
	
	/**
	 * What's the maximum number of results?
	 * 
	 * If we reach this and we're not done yet, we will stop and 
	 * exceededMaximum() will return true.
	 * 
	 * @return the maximum
	 */
	int maximum();
}