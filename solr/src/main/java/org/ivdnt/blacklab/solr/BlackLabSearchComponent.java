package org.ivdnt.blacklab.solr;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import org.apache.lucene.index.IndexReader;
import org.apache.solr.cloud.ZkController;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.DocSet;
import org.apache.solr.servlet.HttpSolrCall;
import org.apache.solr.util.plugin.SolrCoreAware;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.server.lib.WebserviceParamsImpl;
import nl.inl.blacklab.server.lib.results.ApiVersion;
import nl.inl.blacklab.server.lib.results.DStream;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.util.ServletUtil;

public class BlackLabSearchComponent extends SearchComponent implements SolrCoreAware {

    public static final String COMPONENT_NAME = "blacklab-search";

    /** The core we're attached to. */
    private SolrCore core;

    /** Our search manager object. */
    private SearchManager searchManager;

    public BlackLabSearchComponent() {
        // Fix small annoyances in the API, at the cost of not being strictly 100% BLS compatible.
        // (these are things like inconsistent field names, data types, etc.)
        DStream.setApiVersion(ApiVersion.V4);
    }

    /**
     * Called when component is assigned to a core.
     * <p>
     * Should do initialization.
     *
     * @param core The core holding this component.
     */
    @Override
    public void inform(SolrCore core) {
        this.core = core;
    }

    /**
     * Find file in conf dir.
     *
     * @param path path relative to conf
     * @return the file
     */
    protected File findFile(String path) {
        ZkController zkController = core.getCoreContainer().getZkController();
        if (zkController != null) {
          throw new UnsupportedOperationException("Zookeeper not yet supported");
        }
        return new File(core.getResourceLoader().getConfigDir(), path);
    }

    /**
     * Called when component is instantiated.
     * <p>
     * Should interpret and store arguments.
     *
     * @param args arguments (from solrconfig.xml)
     */
    @Override
    public void init(NamedList args) {
//      SolrParams initArgs = args.toSolrParams();
//      System.out.println("Parameters: " + initArgs);
//      if (initArgs.get("xsltFile") == null || initArgs.get("inputField") == null) {
//          throw new RuntimeException("ApplyXsltComponent needs xsltFile and inputField parameters!");
//      }
//      xsltFilePath = initArgs.get("xsltFile");
//      inputField = initArgs.get("inputField");

        // TODO: read config from file? (pointed to by init parameters?)
        BLSConfig blsConfig = new BLSConfig();
        blsConfig.setIsSolr(true);
        searchManager = new SearchManager(blsConfig);
    }

    /**
     * Prepare request.
     * <p>
     * Called after request received, but before processing by any of the components.
     * <p>
     * Allows us to customize how other components will process this request, so we'll
     * get the data we need for our operation.
     *
     * @param rb response builder object where we can find request and indicate our needs
     */
    @Override
    public void prepare(ResponseBuilder rb) {
        if (QueryParamsSolr.shouldRunComponent(rb.req.getParams())) {
            rb.setNeedDocSet(true); // we need to know all the matching documents (to filter on them)
        }

        /*
        // See if we can load a test file now.
        String testFile = "conf/test.xslt";
        try (InputStream is = core.getResourceLoader().openResource(testFile)) {
            System.err.println(IOUtils.toString(is, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }*/

    }

    /**
     * Process a request.
     * <p>
     * Called after previous (e.g. standard Solr) components have run.
     *
     * @param rb response builder where we can find request and results from previous components
     */
    @Override
    public synchronized void process(ResponseBuilder rb) {
        // Should we run at all?
        if (QueryParamsSolr.shouldRunComponent(rb.req.getParams())) {
            IndexReader reader = rb.req.getSearcher().getIndexReader();
            BlackLabIndex index = searchManager.getEngine().getIndexFromReader(reader, true);
            WebserviceParams params = getParams(rb, index);
            if (!searchManager.getIndexManager().indexExists(params.getCorpusName())) {
                searchManager.getIndexManager().registerIndex(params.getCorpusName(), index);
            }
            DataStream ds = new DataStreamSolr(rb.rsp).startDocument("");

            // FIXME: Produce CSV output?
            //   Solr includes a CSV output type, but that seems to be geared towards outputting documents
            //   with their fields. Maybe there's some way to customize this, or add another output type for
            //   "blacklab-csv" output?
            //if (outputType == DataFormat.CSV)

            ds.startEntry("blacklab");
            try {
                boolean debugMode = isDebugMode(rb.req);
                String operation = QueryParamsSolr.getOperation(rb.req.getParams());
                switch (operation) {
                // "Root" endpoint
                case "server-info":
                    WebserviceRequestHandler.opServerInfo(params, debugMode, ds);
                    break;

                // Information about the corpus
                case "corpus-info":
                    WebserviceRequestHandler.opCorpusInfo(params, ds);
                    break;
                case "corpus-status":
                    WebserviceRequestHandler.opCorpusStatus(params, ds);
                    break;
                case "field-info":
                    WebserviceRequestHandler.opFieldInfo(params, ds);
                    break;

                // Find hits or documents
                case "hits":
                    // [grouped] hits
                    WebserviceRequestHandler.opHits(params, ds);
                    break;
                case "docs":
                    // [grouped] docs
                    WebserviceRequestHandler.opDocs(params, ds);
                    break;

                // Information about a document
                case "doc-contents":
                    WebserviceRequestHandler.opDocContents(params, ds);
                    break;
                case "doc-info":
                    WebserviceRequestHandler.opDocInfo(params, ds);
                    break;
                case "doc-snippet":
                    WebserviceRequestHandler.opDocSnippet(params, ds);
                    break;

                // Other search
                case "termfreq":
                    WebserviceRequestHandler.opTermFreq(params, ds);
                    break;
                case "autocomplete":
                    WebserviceRequestHandler.opAutocomplete(params, ds);
                    break;

                // Manage user corpora
                case "list-input-formats":
                    break;
                case "write-input-format":
                    break;
                case "input-format-info":
                    break;
                case "input-format-xslt":
                    break;
                case "delete-input-format":
                    break;

                case "none":
                    // do nothing
                    break;
                default:
                    throw new BadRequest("", "Unknown operation " + operation);
                }
            } catch (Exception e) {
                errorResponse(e, rb);
            }
            ds.endEntry().endDocument();
        }
    }

    private WebserviceParamsImpl getParams(ResponseBuilder rb, BlackLabIndex index) {
        QueryParamsSolr solrParams = new QueryParamsSolr(rb.req.getParams(), rb.req.getCore().getName(), index, searchManager);
        boolean isDocs = QueryParamsSolr.getOperation(rb.req.getParams()).startsWith("doc");
        WebserviceParamsImpl params = WebserviceParamsImpl.get(isDocs, isDebugMode(rb.req), solrParams);
        if (params.getDocumentFilterQuery().isEmpty()) {
            // No explicit bl.filter specified; use Solr's document results as our filter query
            DocSet docSet = rb.getResults() != null ? rb.getResults().docSet : null;
            params.setFilterQuery(new DocSetFilter(docSet, index.metadata().metadataDocId()));
        }
        return params;
    }

    private void errorResponse(Exception e, ResponseBuilder rb) {
        String code = (e instanceof BlsException) ? ((BlsException) e).getBlsErrorCode() : "INTERNAL_ERROR";
        errorResponse(code, e == null ? "UNKNOWN" : e.getMessage(), e, rb);
    }

    private void errorResponse(String code, String message, Exception e, ResponseBuilder rb) {
        NamedList<Object> err = new SimpleOrderedMap<>();
        err.add("code", code);
        err.add("message", message);
        if (e != null) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            System.err.println(sw);
            err.add("stackTrace", sw.toString());
        }
        rb.rsp.add("blacklab", Map.of("error", err));
    }

    private boolean isDebugMode(SolrQueryRequest req) {
        HttpSolrCall httpSolrCall = req.getHttpSolrCall();
        return httpSolrCall == null ? true : // testing
                searchManager.isDebugMode(ServletUtil.getOriginatingAddress(httpSolrCall.getReq()));
    }

    /////////////////////////////////////////////
    ///  SolrInfoBean
    ////////////////////////////////////////////

    @Override
    public String getDescription() {
        return "BlackLab search component";
    }

    @Override
    public Category getCategory() {
        return Category.OTHER;
    }

}
