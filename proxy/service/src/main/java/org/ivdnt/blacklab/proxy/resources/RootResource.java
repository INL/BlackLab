package org.ivdnt.blacklab.proxy.resources;

import java.util.Collections;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.ivdnt.blacklab.proxy.logic.Requests;

@Path("")
public class RootResource {

    /** REST client */
    private final Client client;

    @Inject
    public RootResource(Client client) {
        this.client = client;
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response serverInfo() {
        return Requests.get(client, Collections.emptyMap());
    }

}
