/**
 * Here a lot of different classes reside, mostly fairly high-level
 * classes that constitute the public interface of the BlackLab library,
 * but some parts should probably be moved to a different package.
 *
 * Some notable classes:
 *
 * - {@link nl.inl.blacklab.search.BlackLab} is the top-level starting point of the API. It allows you to
 *   open an index, or if you want, even control the creation of {@link nl.inl.blacklab.search.BlackLabEngine}
 *   (although for most users, the single automatically-generated `BlackLabEngine` is fine).
 * - {@link nl.inl.blacklab.search.BlackLabEngine} is the object you'll deal with once you've opened your index.
 *   You can start a new {@link nl.inl.blacklab.searches.Search} object here, or find information about the index.
 */
package nl.inl.blacklab.search;