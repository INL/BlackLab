package nl.inl.blacklab.search.indexmetadata;

import java.util.stream.Stream;

/**
 * Groups of metadata fields.
 * 
 * Used to divide metadata into logical groups.
 */
public interface MetadataFieldGroups extends Iterable<MetadataFieldGroup> {
    
    Stream<MetadataFieldGroup> stream();
    
    MetadataFieldGroup get(String name);
    
}