package org.ivdnt.blacklab.solr;

import java.io.File;

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
        if (shouldRunComponent(rb)) {
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
        if (shouldRunComponent(rb)) {
            IndexReader reader = rb.req.getSearcher().getIndexReader();
            BlackLabIndex index = searchManager.getEngine().getIndexFromReader(reader, true);
            if (!searchManager.getIndexManager().indexExists(index.name())) {
                searchManager.getIndexManager().registerIndex(index);
            }
            WebserviceParamsSolr solrParams = new WebserviceParamsSolr(rb, index, searchManager);
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
                errorResponse(e.getBlsErrorCode(), e.getMessage(), rb);
                e.printStackTrace();
            } catch (Exception e) {
                errorResponse("INTERNAL_ERROR", e.getMessage(), rb);
                e.printStackTrace();
            }
            ds.endEntry().endDocument();
        }
    }

    private static boolean shouldRunComponent(ResponseBuilder rb) {
        return rb.req.getParams().getBool("bl", false);
    }

    private void errorResponse(String code, String message, ResponseBuilder rb) {
        NamedList<Object> err = new SimpleOrderedMap<>();
        err.add("errorCode", code);
        err.add("errorMessage", message);
        rb.rsp.add("blacklabResponse", err);
    }

    private void opHits(WebserviceParams params, DataStream ds) throws InvalidQuery {
        if (params.isCalculateCollocations()) {
            // Collocations request
            TermFrequencyList tfl = WebserviceOperations.calculateCollocations(params);
            DStream.collocationsResponse(ds, tfl);
        } else {
            // Hits request
            ResultHits resultHits = WebserviceOperations.getResultHits(params);
            DStream.hitsResponse(ds, resultHits, null);
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