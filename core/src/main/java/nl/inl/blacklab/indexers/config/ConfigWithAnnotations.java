package nl.inl.blacklab.indexers.config;

import java.util.Map;

public interface ConfigWithAnnotations {

    void addAnnotation(ConfigAnnotation annotation);

    Map<String, ConfigAnnotation> getAnnotations();

}
