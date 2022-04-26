package org.ivdnt.blacklab.aggregator.resources;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.ivdnt.blacklab.aggregator.representation.AnnotatedField;
import org.ivdnt.blacklab.aggregator.representation.ExternalLinkResults;
import org.ivdnt.blacklab.aggregator.representation.FieldInfo;
import org.ivdnt.blacklab.aggregator.representation.Index;

@Path("/{corpus-name}")
public class IndexResource {

    @GET
    @Path("")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Index indexInfo(@PathParam("corpus-name") String corpusName) {
        FieldInfo fieldInfo = new FieldInfo("pid", "title");
        List<AnnotatedField> annotatedFields = List.of(new AnnotatedField());
        Index index = new Index(corpusName, fieldInfo, annotatedFields);
        return index;
    }

    @GET
    @Path("/hits")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public ExternalLinkResults list(
    		@PathParam("corpus-name") String corpusName,
    		@PathParam("patt") String cqlPattern) {
        return null;
    }
}
