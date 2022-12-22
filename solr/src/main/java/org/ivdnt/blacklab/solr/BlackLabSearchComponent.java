package org.ivdnt.blacklab.solr;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Query;
import org.apache.solr.cloud.ZkController;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocList;
import org.apache.solr.util.plugin.SolrCoreAware;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryFiltered;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.searches.SearchEmpty;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.server.lib.WebserviceParamsImpl;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;

public class BlackLabSearchComponent extends SearchComponent implements SolrCoreAware {

    public static final String COMPONENT_NAME = "blacklab-search";

    /** The core we're attached to. */
    private SolrCore core;

    public BlackLabSearchComponent() {
    }

    /**
     * Called when component is assigned to a core.
     *
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
     *
     * Should interpret and store arguments.
     *
     * @param args arguments (from solrconfig.xml)
     */
    @Override
    public void init(@SuppressWarnings("rawtypes") NamedList args) {
      SolrParams initArgs = args.toSolrParams();
      System.out.println("Parameters: " + initArgs);
//      if (initArgs.get("xsltFile") == null || initArgs.get("inputField") == null) {
//          throw new RuntimeException("ApplyXsltComponent needs xsltFile and inputField parameters!");
//      }
//      xsltFilePath = initArgs.get("xsltFile");
//      inputField = initArgs.get("inputField");

    }

    /**
     * Prepare request.
     *
     * Called after request received, but before processing by any of the components.
     *
     * Allows us to customize how other components will process this request, so we'll
     * get the data we need for our operation.
     *
     * @param rb response builder object where we can find request and indicate our needs
     */
    @Override
    public void prepare(ResponseBuilder rb) {
        rb.setNeedDocList(true);

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
     *
     * Called after previous (e.g. standard Solr) components have run.
     *
     * @param rb response builder where we can find request and results from previous components
     * @throws IOException
     */
    @Override
    public synchronized void process(ResponseBuilder rb) throws IOException {
        // Should we run at all?
        boolean shouldRun = rb.req.getParams().getBool("bl", false);
        if (shouldRun) {
            IndexReader reader = rb.req.getSearcher().getIndexReader();
            BlackLabIndex index = BlackLab.indexFromReader(reader, true);
            WebserviceParamsSolr solrParams = new WebserviceParamsSolr(rb, index);
            WebserviceParams params = WebserviceParamsImpl.get(false, true,
                    solrParams);
            DocList docList = null;
            if (rb.getResults() != null) {
                docList = rb.getResults().docList;
            }
            DocIterator it = docList.iterator();
            Query docFilterQuery = null; // TODO: write Query class that filters on docList
            String field = solrParams.bl("pattfield", index.mainAnnotatedField() == null ? "contents" : index.mainAnnotatedField().name());
            String patt = solrParams.bl("patt", "");
            String operation = solrParams.bl("op", "hits");
            switch (operation) {
            case "hits":
                opHits(index, field, patt, docFilterQuery, rb);
                break;
            case "none":
                // do nothing
                break;
            default:
                errorResponse("Unknown operation " + operation, rb);
            }
        }
    }

    private void errorResponse(String message, ResponseBuilder rb) {
        NamedList<String> err = new NamedList<>();
        err.add("errorMessage", message);
        rb.rsp.add("blacklabResponse", err);
    }

    private void opHits(BlackLabIndex index, String field, String patt, Query docFilterQuery, ResponseBuilder rb)
            throws IOException {

        if (StringUtils.isEmpty(patt)) {
            errorResponse("No pattern given", rb);
            return;
        }
        // Perform pattern search
        try {
            TextPattern tp = CorpusQueryLanguageParser.parse(patt);
            QueryExecutionContext context = index.defaultExecutionContext(index.annotatedField(field));
            BLSpanQuery query = tp.translate(context);
            if (docFilterQuery != null)
                query = new SpanQueryFiltered(tp.translate(context), docFilterQuery);
            SearchEmpty search = index.search();
            Hits hits = search.find(query).execute();
            List<NamedList<Object>> hitList = new ArrayList<>();
            for (Hit hit: hits) {
                NamedList<Object> hitDesc = new NamedList<>();
                String docPid = WebserviceOperations.getDocumentPid(index, hit.doc(), null);
                hitDesc.add("doc", docPid);
                hitDesc.add("start", hit.start());
                hitDesc.add("end", hit.end());
                hitList.add(hitDesc);
            }
            rb.rsp.add("blacklabResponse", hitList);
        } catch (InvalidQuery e) {
            throw new IOException("Error exexcuting BlackLab query", e);
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
