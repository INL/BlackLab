package org.ivdnt.blacklab.solr;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.lucene.index.IndexReader;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.core.SolrCore;
import org.apache.solr.core.SolrResourceLoader;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.util.plugin.SolrCoreAware;

import nl.inl.blacklab.instrumentation.RequestInstrumentationProvider;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.server.lib.results.ApiVersion;
import nl.inl.blacklab.server.lib.results.DStream;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.search.UserRequest;
import nl.inl.blacklab.server.util.WebserviceUtil;

public class BlackLabSearchComponent extends SearchComponent implements SolrCoreAware {

    public static final String COMPONENT_NAME = "blacklab-search";

    /** Default path and name for config file to read. */
    public static final String DEFAULT_CONFIG_PATH = "conf/blacklab-webservice.yaml";

    /** The core we're attached to. */
    private SolrCore core;

    /** Our search manager object. */
    private SearchManager searchManager;

    private String configFilePath;

    private RequestInstrumentationProvider instrumentationProvider = RequestInstrumentationProvider.noOpProvider();

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

        BLSConfig config = getConfig(core);

        // Instantiate our search manager from the config
        config.setIsSolr(true);
        searchManager = new SearchManager(config);
        instrumentationProvider = WebserviceUtil.createInstrumentationProvider(config);
    }

    private BLSConfig getConfig(SolrCore core) {
        // Find and load config file
        boolean isJson = configFilePath.endsWith(".json");
        SolrResourceLoader resourceLoader = core.getResourceLoader();
        BLSConfig config;
        if (resourceLoader.resourceLocation(configFilePath) != null)  {
            try (InputStream is = resourceLoader.openResource(configFilePath)) {
                InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                config = BLSConfig.read(reader, isJson);
                System.err.println("##### Loaded BLS config file " + configFilePath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            config = new BLSConfig(); // Default config if no config file found
            System.err.println("##### no BLS config file found at " + configFilePath);
        }
        return config;
    }

//    /**
//     * Find file in conf dir.
//     *
//     * @param path path relative to conf
//     * @return the file
//     */
//    protected File findFile(String path) {
//        ZkController zkController = core.getCoreContainer().getZkController();
//        if (zkController != null) {
//          throw new UnsupportedOperationException("Zookeeper not yet supported");
//        }
//        return new File(core.getResourceLoader().getConfigDir(), path);
//    }

    /**
     * Called when component is instantiated.
     * <p>
     * Should interpret and store arguments.
     *
     * @param args arguments (from solrconfig.xml)
     */
    @Override
    public void init(NamedList args) {
        configFilePath = (String)args.get("configFile", DEFAULT_CONFIG_PATH);

//      SolrParams initArgs = args.toSolrParams();
//      System.out.println("Parameters: " + initArgs);
//      if (initArgs.get("xsltFile") == null || initArgs.get("inputField") == null) {
//          throw new RuntimeException("ApplyXsltComponent needs xsltFile and inputField parameters!");
//      }
//      xsltFilePath = initArgs.get("xsltFile");
//      inputField = initArgs.get("inputField");

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

            // We keep setting the cache for every request; the cache should probably be owner by the
            // BlackLabEngine, and set automatically when the BlackLabIndex is instantiated.
            // For now, this doesn't cause any problems, it's just messy.
            index.setCache(searchManager.getBlackLabCache());

            UserRequest userRequest = new UserRequestSolr(rb, this);
            WebserviceParams params = userRequest.getParams(index, null);
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
                boolean debugMode = userRequest.isDebugMode();
                switch (params.getOperation()) {
                // "Root" endpoint
                case SERVER_INFO:
                    WebserviceRequestHandler.opServerInfo(params, debugMode, ds);
                    break;

                // Information about the corpus
                case CORPUS_INFO:
                    WebserviceRequestHandler.opCorpusInfo(params, ds);
                    break;
                case CORPUS_STATUS:
                    WebserviceRequestHandler.opCorpusStatus(params, ds);
                    break;
                case FIELD_INFO:
                    WebserviceRequestHandler.opFieldInfo(params, ds);
                    break;

                // Find hits or documents
                case HITS: case HITS_CSV: case HITS_GROUPED:
                    // [grouped] hits
                    WebserviceRequestHandler.opHits(params, ds);
                    break;
                case DOCS: case DOCS_CSV: case DOCS_GROUPED:
                    // [grouped] docs
                    WebserviceRequestHandler.opDocs(params, ds);
                    break;

                // Information about a document
                case DOC_CONTENTS:
                    WebserviceRequestHandler.opDocContents(params, ds);
                    break;
                case DOC_INFO:
                    WebserviceRequestHandler.opDocInfo(params, ds);
                    break;
                case DOC_SNIPPET:
                    WebserviceRequestHandler.opDocSnippet(params, ds);
                    break;

                // Other search
                case TERMFREQ:
                    WebserviceRequestHandler.opTermFreq(params, ds);
                    break;
                case AUTOCOMPLETE:
                    WebserviceRequestHandler.opAutocomplete(params, ds);
                    break;

                // Manage user corpora
                case LIST_INPUT_FORMATS:
                    WebserviceRequestHandler.opListInputFormats(params, ds);
                    break;
                case INPUT_FORMAT_INFO:
                    WebserviceRequestHandler.opInputFormatInfo(params, ds);
                    break;
                case INPUT_FORMAT_XSLT:
                    break;

                case WRITE_INPUT_FORMAT:
                case DELETE_INPUT_FORMAT:
                case CREATE_CORPUS:
                case DELETE_CORPUS:
                case ADD_TO_CORPUS:
                case CORPUS_SHARING:
                    throw new UnsupportedOperationException("Not (yet) supported: " + params.getOperation());

                case CACHE_INFO:
                    WebserviceRequestHandler.opCacheInfo(params, ds);
                    break;

                case CLEAR_CACHE:
                    WebserviceRequestHandler.opClearCache(params, ds, debugMode);
                    break;

                case STATIC_RESPONSE:
                case DEBUG:
                case NONE:
                    // do nothing
                    break;
                }
            } catch (Exception e) {
                errorResponse(e, rb);
            }
            ds.endEntry().endDocument();
        }
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

    public SearchManager getSearchManager() {
        return searchManager;
    }

    public RequestInstrumentationProvider getInstrumentationProvider() {
        return instrumentationProvider;
    }
}
