package nl.inl.blacklab.server.lib.results;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.document.Document;

import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.lib.ConcordanceContext;
import nl.inl.blacklab.server.lib.SearchCreator;

public class ResultHitGroup {

    HitGroup group;

    private final String identity;

    private final String identityDisplay;

    private final long size;

    private final List<Pair<String, String>> properties;

    private CorpusSize subcorpusSize = null;

    private final long numberOfDocsInGroup;

    private ConcordanceContext concordanceContext = null;

    private Map<Integer, String> docIdToPid = null;

    private ResultListOfHits listOfHits = null;

    ResultHitGroup(SearchCreator params, HitGroups groups, HitGroup group, DocProperty metadataGroupProperties,
            DocResults subcorpus, List<HitProperty> prop, Map<Integer, Document> luceneDocs) {
        this.group = group;
        PropertyValue id = group.identity();
        identity = id.serialize();
        identityDisplay = id.toString();
        size = group.size();

        if (metadataGroupProperties != null) {
            // Find size of corresponding subcorpus group
            PropertyValue docPropValues = groups.groupCriteria().docPropValues(id);
            subcorpusSize = WebserviceOperations.findSubcorpusSize(params, subcorpus.query(), metadataGroupProperties,
                    docPropValues);
        }

        numberOfDocsInGroup = group.storedResults().docsStats().countedTotal();

        properties = new ArrayList<>();
        List<PropertyValue> valuesForGroup = id.valuesList();
        for (int j = 0; j < prop.size(); ++j) {
            final HitProperty hp = prop.get(j);
            final PropertyValue pv = valuesForGroup.get(j);
            properties.add(Pair.of(hp.serialize(), pv.toString()));
        }

        if (params.includeGroupContents()) {
            Hits hitsInGroup = group.storedResults();
            ContextSettings contextSettings = params.contextSettings();
            concordanceContext = ConcordanceContext.get(hitsInGroup, contextSettings.concType(),
                    contextSettings.size());
            docIdToPid = WebserviceOperations.collectDocsAndPids(params.blIndex(), hitsInGroup, luceneDocs);
        }

        if (params.includeGroupContents()) {
            Hits hitsInGroup = getGroup().storedResults();
            listOfHits = WebserviceOperations.listOfHits(params, hitsInGroup, getConcordanceContext(),
                    getDocIdToPid());
        }
    }

    public String getIdentity() {
        return identity;
    }

    public String getIdentityDisplay() {
        return identityDisplay;
    }

    public long getSize() {
        return size;
    }

    public List<Pair<String, String>> getProperties() {
        return properties;
    }

    public CorpusSize getSubcorpusSize() {
        return subcorpusSize;
    }

    public long getNumberOfDocsInGroup() {
        return numberOfDocsInGroup;
    }

    public ConcordanceContext getConcordanceContext() {
        return concordanceContext;
    }

    public Map<Integer, String> getDocIdToPid() {
        return docIdToPid;
    }

    public HitGroup getGroup() {
        return group;
    }

    public ResultListOfHits getListOfHits() {
        return listOfHits;
    }
}
