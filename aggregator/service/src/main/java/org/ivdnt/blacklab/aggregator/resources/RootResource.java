package org.ivdnt.blacklab.aggregator.resources;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;

import org.ivdnt.blacklab.aggregator.Aggregator;
import org.ivdnt.blacklab.aggregator.representation.Server;

@Path("/")
public class RootResource {

    /** Client is a heavy object and Jersey clients should be threadsafe */
    private static final Client client = ClientBuilder.newClient();

    @GET
    @Path("")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Server serverInfo() {
        // Get server info
        try {
            Server server = client.target(Aggregator.BLS_URL)
                    .request(MediaType.APPLICATION_JSON)
                    .get(Server.class);
            return server;
        } finally {
            client.close();
        }
    }

}
