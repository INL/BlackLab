package org.ivdnt.blacklab.aggregator.resources;

import java.net.URI;
import java.net.URISyntaxException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/")
public class Index {
    
    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response getHtml() throws URISyntaxException {
        return Response.seeOther(new URI("../web/api/index.jsp")).build();
    }

    
}
