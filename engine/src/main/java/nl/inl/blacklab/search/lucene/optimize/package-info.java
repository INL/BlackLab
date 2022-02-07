/**
 * Classes for optimizing {@link nl.inl.blacklab.search.lucene.BLSpanQuery} structures.
 *
 * {@link nl.inl.blacklab.search.lucene.optimize.ClauseCombiner} classes represent several
 * rules for potentially combining (rewriting to more efficient version)
 * adjacent clauses.
 *
 * For example, {@link nl.inl.blacklab.search.lucene.optimize.ClauseCombinerRepetition} will recognize
 * <code>"test" "test"</code> and rewrite it to the more efficient
 * version <code>"test"{2}</code>.
 *
 * Each clause combiner will determine whether its rule
 * could be applied to two adjacent clauses, and how relatively
 * beneficial this would be. The goal is to have the
 * optimization rules apply in order of decreasing importance,
 * but this importance may hinge on the actual clauses, so
 * no fixed order is given.
 */
package nl.inl.blacklab.search.lucene.optimize;