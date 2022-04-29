package org.ivdnt.blacklab.aggregator;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;

import org.ivdnt.blacklab.aggregator.representation.Index;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestClient {

    static final String LOCAL_TEST_URL = "http://localhost:8080/blacklab-server/test";
    static final String ZEEBRIEVEN_URL = "https://brievenalsbuit.ivdnt.org/blacklab-server/BaB";

    public static void main(String[] args) throws JsonProcessingException {
        Client client = ClientBuilder.newClient();

//        // Get server info
//        Server server = client.target("http://localhost:8080/blacklab-server/")
//                .request(MediaType.APPLICATION_JSON)
//                .get(Server.class);
//        System.out.println(server);

        // Get index info
        String indexName = "test"; //server.indices.get(0).name;
        Index index = client.target(ZEEBRIEVEN_URL)
                .request(MediaType.APPLICATION_JSON)
                .get(Index.class);

        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(index);

        System.out.println(json);
    }
}
