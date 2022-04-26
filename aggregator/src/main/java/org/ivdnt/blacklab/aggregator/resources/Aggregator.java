package org.ivdnt.blacklab.aggregator.resources;

import java.util.List;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.ivdnt.blacklab.aggregator.representation.ExternalLinkResults;
import org.ivdnt.blacklab.aggregator.representation.IndexSummary;
import org.ivdnt.blacklab.aggregator.representation.ServerInfoResponse;
import org.ivdnt.blacklab.aggregator.representation.User;

@Path("/{corpus-name}")
public class Aggregator {

    @GET
    @Path("")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public ServerInfoResponse serverInfo() {
        IndexSummary index = new IndexSummary("test2", "Test index2", "TEI");
        User user = new User(false, "", false);
        ServerInfoResponse serverInfo = new ServerInfoResponse("yesterday", "3.x", List.of(index), user);
        return serverInfo;
    }

    @GET
    @Path("/hits")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public ExternalLinkResults list(
    		@PathParam("resource") String resourceName,
    		@DefaultValue("-1") @QueryParam("status") int status,
            @DefaultValue("0") @QueryParam("offset") int offset,
            @DefaultValue("100") @QueryParam("limit") int limit,
            @DefaultValue("") @QueryParam("dst_ids") String dstIds,
            @DefaultValue("0") @QueryParam("lastedit") int lastEditDays) {
        return null;
    }
}
