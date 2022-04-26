package org.ivdnt.blacklab.aggregator.resources;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.ivdnt.blacklab.aggregator.representation.IndexSummary;
import org.ivdnt.blacklab.aggregator.representation.ServerInfoResponse;
import org.ivdnt.blacklab.aggregator.representation.User;

@Path("/")
public class Root {

    @GET
    @Path("")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public ServerInfoResponse serverInfo() {
        IndexSummary index = new IndexSummary("test", "Test index", "TEI");
        User user = new User(false, "", false);
        ServerInfoResponse serverInfo = new ServerInfoResponse("yesterday", "3.x", List.of(index), user);
        return serverInfo;
    }

}
