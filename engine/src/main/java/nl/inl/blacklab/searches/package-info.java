/**
 * Classes that describe a complete search request.
 *
 * These may include a pattern query, document filter and grouping, sorting and sampling parameters.
 *
 * A {@link nl.inl.blacklab.searches.Search} structure forms the key to our
 * {@link nl.inl.blacklab.searches.SearchCache}. Having this kind of structure before starting to execute the search
 * also gives us the opportunity to make intelligent optimization decisions. For example, see
 * {@link nl.inl.blacklab.searches.SearchHitGroupsFromHits}, where certain groupings may resolve using a faster path.
 */
package nl.inl.blacklab.searches;