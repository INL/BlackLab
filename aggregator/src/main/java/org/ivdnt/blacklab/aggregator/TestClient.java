package org.ivdnt.blacklab.aggregator;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;

import org.ivdnt.blacklab.aggregator.representation.Server;

public class TestClient {

    public static void main(String[] args) {
        Client client = ClientBuilder.newClient();
        Server server = client.target("http://localhost:8080/blacklab-server/")
                .request(MediaType.APPLICATION_JSON)
                .get(Server.class);

        System.out.println(server);
    }
}
