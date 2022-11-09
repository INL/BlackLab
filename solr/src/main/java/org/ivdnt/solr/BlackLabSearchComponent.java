package org.ivdnt.solr;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.solr.cloud.ZkController;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocList;
import org.apache.solr.util.plugin.SolrCoreAware;

import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.textpattern.TextPattern;

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
        SolrParams params = rb.req.getParams();
        boolean shouldRun = params.getBool("bl", false);
        if (shouldRun) {
            DocList results = null;
            if (rb.getResults() != null) {
                results = rb.getResults().docList;
            }
            //SimpleOrderedMap<String> data = new SimpleOrderedMap<>();
            List<String> data = new ArrayList<>();
            DocIterator it = results.iterator();
            IndexReader reader = rb.req.getSearcher().getIndexReader();
            BlackLabIndex index = BlackLab.indexFromReader(reader);
            String patt = params.get("bl.patt");
            if (patt != null) {
                TextPattern tp = CorpusQueryLanguageParser.parse(patt);
                QueryExecutionContext context = index.defaultExecutionContext(index.mainAnnotatedField());
                tp.translate(context);
                index.search().find
            }
            
            try {
                Transformer transformer = xslt.get(); //factory.newTransformer(new StreamSource(new FileInputStream(xsltFile)));
                while (it.hasNext()) {
                    // Get the XML from the response
                    Document doc = searcher.doc(it.next());
                    String xml = doc.get(inputField); // TODO: make field configurable
                    
                    // Transform the XML using the XSLT
                    Source source = new StreamSource(new StringReader(xml));
                    StringWriter html = new StringWriter();
                    Result result = new StreamResult(html);
                    transformer.reset();
                    transformer.transform(source, result);
                    
                    // Add to our output section in the same order as the results
                    data.add(html.toString());
                }
                rb.rsp.add("apply-xslt", data);
                
            } catch (TransformerException e) {
                throw new RuntimeException(e);
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
