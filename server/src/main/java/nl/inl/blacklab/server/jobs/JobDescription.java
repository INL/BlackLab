package nl.inl.blacklab.server.jobs;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.requesthandlers.SearchParameters;
import nl.inl.blacklab.server.search.SearchManager;

/** Description of a job */
public abstract class JobDescription {

    private Class<? extends Job> jobClass;

    Constructor<? extends Job> jobClassCtor;

    JobDescription inputDesc;

    private SearchSettings searchSettings;

    private SearchParameters searchParam;

    public JobDescription(SearchParameters param, Class<? extends Job> jobClass, JobDescription inputDesc,
            SearchSettings searchSettings) {
        try {
            this.searchParam = param;
            this.jobClass = jobClass;
            jobClassCtor = jobClass.getConstructor(SearchManager.class, User.class, JobDescription.class);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new RuntimeException(e);
        }
        this.inputDesc = inputDesc;
        this.searchSettings = searchSettings;
    }

    public JobDescription getInputDesc() {
        return inputDesc;
    }

    /**
     * Get the index name
     * 
     * @return name of the index this job if for
     */
    public String getIndexName() {
        return inputDesc.getIndexName();
    }

    /**
     * Generate a unique identifier string for this job, for caching, etc.
     * 
     * @return the unique identifier string
     */
    public String uniqueIdentifier() {
        return jobClass.getSimpleName() + "("
                + (inputDesc == null ? "" : "input=" + inputDesc.uniqueIdentifier() + ", ");
    }

    /**
     * Create the job corresponding to this description.
     * 
     * @param searchMan searchmanager to use
     * @param user user that created the job
     * @return newly created job
     * @throws BlsException on error
     */
    public Job createJob(SearchManager searchMan, User user) throws BlsException {
        try {
            return jobClassCtor.newInstance(searchMan, user, this);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            throw new InternalServerError("Cannot instantiate job class for JobDesc: " + this, 31, e);
        }
    }

    @Override
    public String toString() {
        return uniqueIdentifier();
    }

    public SearchSettings getSearchSettings() {
        return searchSettings;
    }

    /**
     * Get the pattern to search for (if any)
     * 
     * @return pattern, or null if none given
     */
    public TextPattern getPattern() {
        return null;
    }

    public Query getFilterQuery() {
        return null;
    }

    public SampleSettings getSampleSettings() {
        return null;
    }

    public MaxSettings getMaxSettings() {
        return null;
    }

    public WindowSettings getWindowSettings() {
        return null;
    }

    public ContextSettings getContextSettings() {
        return null;
    }

    public List<DocProperty> getFacets() {
        return null;
    }

    public DocGroupSettings getDocGroupSettings() {
        return null;
    }

    public DocGroupSortSettings getDocGroupSortSettings() {
        return null;
    }

    public DocSortSettings getDocSortSettings() {
        return null;
    }

    public HitGroupSettings getHitGroupSettings() {
        return null;
    }

    public HitGroupSortSettings getHitGroupSortSettings() {
        return null;
    }

    public HitSortSettings getHitSortSettings() {
        return null;
    }

    public HitFilterSettings getHitFilterSettings() {
        return null;
    }

    public void dataStreamEntries(DataStream ds) {
        ds.entry("jobClass", jobClass.getSimpleName())
                .entry("inputDesc", inputDesc == null ? "(none)" : inputDesc.toString())
                .entry("url", getUrl());
    }

    public abstract String getUrlPath();

    public String getUrl() {
        return searchParam.getIndexName() + "/" + getUrlPath() + "?" + searchParam.getUrlParam();
    }

}
