package org.ivdnt.blacklab.solr;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Optional;

import org.apache.lucene.index.IndexReader;
import org.apache.solr.cloud.ZkController;
import org.apache.solr.common.StringUtils;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.search.DocSet;
import org.apache.solr.util.plugin.SolrCoreAware;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.server.lib.WebserviceParamsImpl;
import nl.inl.blacklab.server.lib.results.DStream;
import nl.inl.blacklab.server.lib.results.ResultHits;
import nl.inl.blacklab.server.lib.results.ResultHitsGrouped;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;
import nl.inl.blacklab.server.search.SearchManager;

public class BlackLabSearchComponent extends SearchComponent implements SolrCoreAware {

    public static final String COMPONENT_NAME = "blacklab-search";

    /** The core we're attached to. */
    private SolrCore core;

    /** Our search manager object. */
    private SearchManager searchManager;

    public BlackLabSearchComponent() {
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
        if (WebserviceParamsSolr.shouldRunComponent(rb.req.getParams())) {
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
        if (WebserviceParamsSolr.shouldRunComponent(rb.req.getParams())) {
            IndexReader reader = rb.req.getSearcher().getIndexReader();
            BlackLabIndex index = searchManager.getEngine().getIndexFromReader(reader, true);
            if (!searchManager.getIndexManager().indexExists(index.name())) {
                searchManager.getIndexManager().registerIndex(index);
            }
            WebserviceParamsSolr solrParams = new WebserviceParamsSolr(rb.req.getParams(), index, searchManager);
            WebserviceParamsImpl params = WebserviceParamsImpl.get(false, true, solrParams);

            if (!solrParams.has("filter")) {
                // No bl.filter specified; use Solr's document results as our filter query
                DocSet docSet = rb.getResults() != null ? rb.getResults().docSet : null;
                params.setFilterQuery(new DocSetFilter(docSet));
            }

            String operation = solrParams.getOperation();
            if (StringUtils.isEmpty(operation))
                operation = "hits";
            DataStream ds = new DataStreamSolr(rb.rsp).startDocument("");
            ds.startEntry("blacklabResponse");
            try {
                switch (operation) {
                case "hits":
                    opHits(params, ds);
                    break;
                case "none":
                    // do nothing
                    break;
                default:
                    throw new BadRequest("", "Unknown operation " + operation);
                }
            } catch (BlsException e) {
                errorResponse(e, rb);
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
        rb.rsp.add("blacklabResponse", Map.of("error", err));
    }

    private void opHits(WebserviceParams params, DataStream ds) throws InvalidQuery {
        if (params.isCalculateCollocations()) {
            // Collocations request
            TermFrequencyList tfl = WebserviceOperations.calculateCollocations(params);
            DStream.collocationsResponse(ds, tfl);
        } else {
            // Hits request

            Optional<String> viewgroup = params.getViewGroup();
            boolean returnListOfGroups = false;
            if (params.getGroupProps().isPresent()) {
                // This is a grouping operation
                if (viewgroup.isEmpty()) {
                    // We want the list of groups, not the contents of a single group
                    returnListOfGroups = true;
                }
            } else if (viewgroup.isPresent()) {
                // "viewgroup" parameter without "group" parameter; error.
                throw new BadRequest("ERROR_IN_GROUP_VALUE",
                        "Parameter 'viewgroup' specified, but required 'group' parameter is missing.");
            }
            // FIXME: Produce CSV output?
            //if (outputType == DataFormat.CSV)
            //    handlerName += "-csv";

            if (returnListOfGroups) {
                ResultHitsGrouped hitsGrouped = WebserviceOperations.hitsGrouped(params);
                DStream.hitsGroupedResponse(ds, hitsGrouped);
            } else {
                ResultHits resultHits = WebserviceOperations.getResultHits(params);
                DStream.hitsResponse(ds, resultHits);
            }
        }
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
