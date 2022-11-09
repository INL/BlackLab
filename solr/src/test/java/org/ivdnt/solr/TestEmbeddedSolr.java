/**
 * Embedded Solr test.
 *
 * Inspired by code from Mtas, https://github.com/meertensinstituut/mtas/
 */

package org.ivdnt.solr;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.request.GenericSolrRequest;
import org.apache.solr.client.solrj.request.RequestWriter;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.CoreContainer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class TestEmbeddedSolr {
    private static final String SOLR_DIR_NAME = "blacklab-test-solr";

    private static final String CORE_NAME = "testcore";

    public static final String FIELD_TEST1 = "test1";

    public static final String FIELD_TEST2 = "test2";

    private static EmbeddedSolrServer server;

    private static Path solrPath;

    private static void copyFile(Path sourcePath, Path targetPath, String fileName) throws IOException {
        Files.copy(sourcePath.resolve(fileName), targetPath.resolve(fileName));
    }

    private static boolean deleteDirectoryTree(File dir) {
        File[] allContents = dir.listFiles();
        if (allContents != null) {
            for (File file: allContents) {
                if (!deleteDirectoryTree(file))
                    return false;
            }
        }
        return dir.delete();
    }

    @BeforeClass
    public static void prepareClass() throws Exception
    {
        solrPath = Files.createTempDirectory(SOLR_DIR_NAME);

        Path resourcePath = Paths.get("src", "test", "resources", "solrDir");

        copyFile(resourcePath, solrPath, "solr.xml");

        CoreContainer container = new CoreContainer(solrPath.toAbsolutePath(), new Properties());
        container.load();

        server = new EmbeddedSolrServer(container, CORE_NAME);

        setLogLevel("WARN"); // show log messages

        // Create core and add document
        createCore(CORE_NAME, resourcePath.resolve("conf"));
        SolrInputDocument testDoc = new SolrInputDocument();
        testDoc.addField("id", "THIS-IS-A-TEST-0002");
        testDoc.addField(FIELD_TEST1, "<?xml version=\"1.0\"?>\n<test><name>Monkey</name><type>Mammal</type></test>");
        testDoc.addField(FIELD_TEST2, "woohoo");
        server.add(CORE_NAME, testDoc);
        server.commit(CORE_NAME);

        // (component is already added in solrconfig.xml, so this call is not needed,
        //  and the method unfortunately doesn't work yet anyway, see comment. we'll look at it later)
        //addSearchComponent(CORE_NAME, "apply-xslt", ApplyXsltComponent.class.getCanonicalName());
    }

    private static void addSearchComponent(String coreName, String compName, String fqcn)
            throws SolrServerException, IOException {
        String json =
            "{\n" +
            "  \"add-searchcomponent\": {\n" +
            "    \"name\": \""+ compName +"\",\n" +
            "    \"class\": \"" + fqcn + "\",\n" +
            "    \"xsltFile\": \"test.xslt\",\n" +
            "    \"inputField\": \"test1\",\n" +
            "  }\n" +
            "}\n";
        String path = "/" + coreName + "/config";
        GenericSolrRequest reqAddSearchComponent = new GenericSolrRequest(SolrRequest.METHOD.POST,
                path, new ModifiableSolrParams());
        RequestWriter.ContentWriter contentWriter =
                new RequestWriter.StringPayloadContentWriter(json, "application/json");
        reqAddSearchComponent.setContentWriter(contentWriter);
        // FIXME ERROR: unknown handler: /testcore/config
        // (but in JUnit we can just put the config in solrconfig.xml, so not needed right now)
        NamedList<Object> response = server.request(reqAddSearchComponent);
        System.err.println("Add search component response\n" + response.toString());
    }

    private static void setLogLevel(String level) throws SolrServerException, IOException {
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set("set", "root:" + level);
        GenericSolrRequest reqSetLogLevel = new GenericSolrRequest(SolrRequest.METHOD.POST,
                "/admin/info/logging", params);
        NamedList<Object> response = server.request(reqSetLogLevel);
        System.err.println("Log level response\n" + response.toString());
    }

    /**
     * Create core via the CoreAdmin API.
     *
     * @param coreName name for new core
     * @param confTemplatePath where template files can be found
     */
    private static void createCore(String coreName, Path confTemplatePath) throws IOException {
        String pathToSolrConfigXml = confTemplatePath.resolve("solrconfig.xml").toAbsolutePath().toString();
        String pathToSchemaXml = confTemplatePath.resolve("schema.xml").toAbsolutePath().toString();

        CoreAdminRequest.Create request = new CoreAdminRequest.Create();
        request.setCoreName(coreName);
        request.setConfigName(pathToSolrConfigXml);
        request.setSchemaName(pathToSchemaXml);

        try {
            System.setProperty("solr.allow.unsafe.resourceloading", "true"); // allow loading files from outside dirs
            server.request(request);
        } catch (Exception e) {
            throw new RuntimeException("Error creating core " + coreName, e);
        }

        // Copy XSLT file used by our SearchComponent.
        Path coreConfPath = server.getCoreContainer().getCore(coreName).getInstancePath().resolve("conf");
        copyFile(confTemplatePath, coreConfPath, "test.xslt");
    }

    @AfterClass
    public static void cleanUpClass() throws IOException {
        if (server != null)
            server.close();
        File f = solrPath.toFile();
        if (f.isDirectory() && f.getName().equals(SOLR_DIR_NAME) && f.canWrite()) {
            deleteDirectoryTree(f);
        }
    }

    @Ignore
    @Test
    public void testStuff() throws SolrServerException, IOException {
        ModifiableSolrParams solrParams = new ModifiableSolrParams();
        solrParams.add(CommonParams.Q, "*:*");
        solrParams.add("applyXslt", "true"); // activate our xslt component

        System.err.println(CORE_NAME);
        QueryResponse queryResponse = server.query(CORE_NAME, solrParams);
        System.err.println("RESPONSE: " + queryResponse.getResponse());
        /*for (SolrDocument document: queryResponse.getResults()) {
            System.out.println(document);
        }*/
    }
}
