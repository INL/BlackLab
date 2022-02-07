/**
 * Classes related to evaluate CQL global constraints, which filter matches.
 *
 * {@link nl.inl.blacklab.search.matchfilter.MatchFilter} classes used to evaluate global constraints on matches,
 * e.g. <code>a:[] "and" b:[] :: a.word = b.word</code>
 * to find things like "more and more", "less and less", etc.
 *
 * Also contains {@link nl.inl.blacklab.search.matchfilter.ConstraintValue} classes that are the values
 * constraint expressions can evaluate to.
 */
package nl.inl.blacklab.search.matchfilter;