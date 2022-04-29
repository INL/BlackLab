package org.ivdnt.blacklab.aggregator.resources;

import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.ivdnt.blacklab.aggregator.representation.AnnotatedField;
import org.ivdnt.blacklab.aggregator.representation.DocInfo;
import org.ivdnt.blacklab.aggregator.representation.FieldInfo;
import org.ivdnt.blacklab.aggregator.representation.Hit;
import org.ivdnt.blacklab.aggregator.representation.HitGroup;
import org.ivdnt.blacklab.aggregator.representation.HitsResults;
import org.ivdnt.blacklab.aggregator.representation.Index;
import org.ivdnt.blacklab.aggregator.representation.MetadataField;
import org.ivdnt.blacklab.aggregator.representation.MetadataValues;
import org.ivdnt.blacklab.aggregator.representation.SearchParam;
import org.ivdnt.blacklab.aggregator.representation.SearchSummary;

@Path("/{corpus-name}")
public class IndexResource {

    @GET
    @Path("")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Index indexInfo(@PathParam("corpus-name") String corpusName) {
        FieldInfo fieldInfo = new FieldInfo("pid", "title");
        List<AnnotatedField> annotatedFields = List.of(new AnnotatedField());
        List<MetadataField> metadataFields = List.of(new MetadataField());
        Index index = new Index(corpusName, fieldInfo, annotatedFields, metadataFields);
        return index;
    }

    @GET
    @Path("/hits")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public HitsResults hits(
    		@PathParam("corpus-name") String corpusName,
    		@QueryParam("patt") String cqlPattern,
            @QueryParam("sort") String sort,
            @QueryParam("group") String group) {
        SearchParam searchParam = new SearchParam(corpusName, cqlPattern, sort, group);
        SearchSummary summary = new SearchSummary(searchParam);
        if (group == null || group.isEmpty()) {
            // Hits results
            String docPid = "my-doc-pid";
            Hit hit = new Hit(docPid, 0, 10);
            List<Hit> hits = List.of(hit);
            Map<String, MetadataValues> metadata = Map.of(
                    "title", new MetadataValues(List.of("Bla bla")),
                    "author", new MetadataValues(List.of("Zwets", "Neuzel"))
            );
            DocInfo docInfo = new DocInfo(docPid, metadata);
            List<DocInfo> docInfos = List.of(docInfo);
            return new HitsResults(summary, hits, docInfos);
        } else {
            // Hits grouped results
            List<HitGroup> hitGroups = List.of(new HitGroup());
            return new HitsResults(summary, hitGroups);
        }
    }
}
