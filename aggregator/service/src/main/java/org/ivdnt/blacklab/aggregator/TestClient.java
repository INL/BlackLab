package org.ivdnt.blacklab.aggregator;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;

import org.ivdnt.blacklab.aggregator.representation.HitsResults;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class TestClient {

    static final String LOCAL_TEST_URL = "http://localhost:8080/blacklab-server/test";
    static final String ZEEBRIEVEN_URL = "https://brievenalsbuit.ivdnt.org/blacklab-server/BaB";

    public static void main(String[] args) throws JsonProcessingException {

        String corpusUrl = ZEEBRIEVEN_URL;
        String blsUrl = corpusUrl.substring(0, corpusUrl.lastIndexOf('/'));

        ObjectMapper objectMapper = new ObjectMapper();
        ObjectWriter writer = objectMapper.writerWithDefaultPrettyPrinter();

        Client client = ClientBuilder.newClient();

//        // Get server info
//        Server server = client.target(blsUrl)
//                .request(MediaType.APPLICATION_JSON)
//                .get(Server.class);
//        System.out.println(writer.writeValueAsString(server));
//
//        // Get index info
//        Index index = client.target(ZEEBRIEVEN_URL)
//                .request(MediaType.APPLICATION_JSON)
//                .get(Index.class);
//        System.out.println(writer.writeValueAsString(index));

        // Perform search
        HitsResults hits = client.target(ZEEBRIEVEN_URL + "/hits?patt=%22de%22")
                .request(MediaType.APPLICATION_JSON)
                .get(HitsResults.class);
        System.out.println(writer.writeValueAsString(hits));
    }
}
