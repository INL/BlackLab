package nl.inl.blacklab.server.config;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonSetter;

import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class BLSConfigParameters {
    String patternLanguage = "corpusql";
    
    String filterLanguage = "luceneql";
    
    MatchSensitivity defaultSearchSensitivity;

    DefaultMax processHits = DefaultMax.get(5000000, 5000000);
    
    DefaultMax countHits = DefaultMax.get(10000000, 10000000);
    
    DefaultMax pageSize = DefaultMax.get(50, 3000);
    
    DefaultMax contextSize = DefaultMax.get(5, 200);

    private boolean writeHitsAndDocsInGroupedHits = false;


    @JsonGetter("defaultSearchSensitivity")
    public String getDefaultSearchSensitivityName() {
        return defaultSearchSensitivity.toString();
    }

    @JsonSetter("defaultSearchSensitivity")
    public void setDefaultSearchSensitivityName(String value) {
        defaultSearchSensitivity = MatchSensitivity.fromName(value);
    }

    public String getPatternLanguage() {
        return patternLanguage;
    }

    public void setPatternLanguage(String patternLanguage) {
        this.patternLanguage = patternLanguage;
    }

    public String getFilterLanguage() {
        return filterLanguage;
    }

    public void setFilterLanguage(String filterLanguage) {
        this.filterLanguage = filterLanguage;
    }

    public DefaultMax getProcessHits() {
        return processHits;
    }

    public void setProcessHits(DefaultMax processHits) {
        this.processHits = processHits;
    }

    public DefaultMax getCountHits() {
        return countHits;
    }

    public void setCountHits(DefaultMax countHits) {
        this.countHits = countHits;
    }

    public DefaultMax getPageSize() {
        return pageSize;
    }

    public void setPageSize(DefaultMax pageSize) {
        this.pageSize = pageSize;
    }

    public DefaultMax getContextSize() {
        return contextSize;
    }

    public void setContextSize(DefaultMax contextSize) {
        this.contextSize = contextSize;
    }
    
    public MatchSensitivity getDefaultSearchSensitivity() {
        return defaultSearchSensitivity;
    }

    public void setDefaultSearchSensitivity(MatchSensitivity defaultSearchSensitivity) {
        this.defaultSearchSensitivity = defaultSearchSensitivity;
    }

    public boolean isWriteHitsAndDocsInGroupedHits() {
        return writeHitsAndDocsInGroupedHits;
    }

    public void setWriteHitsAndDocsInGroupedHits(boolean writeHitsAndDocsInGroupedHits) {
        this.writeHitsAndDocsInGroupedHits = writeHitsAndDocsInGroupedHits;
    }
}