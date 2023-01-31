package nl.inl.blacklab.indexers.config;

import java.util.Map;

/** Shared superclass of some Config (blf.yaml/blf.json) related classes. */
public interface ConfigWithAnnotations {

    void addAnnotation(ConfigAnnotation annotation);

    Map<String, ConfigAnnotation> getAnnotations();

    Map<String, ConfigAnnotation> getAnnotationsFlattened();

}
