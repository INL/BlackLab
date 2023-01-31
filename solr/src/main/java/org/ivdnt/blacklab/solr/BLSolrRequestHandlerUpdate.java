package org.ivdnt.blacklab.solr;

import java.util.Map;

import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.UpdateRequestHandler;
import org.apache.solr.handler.loader.ContentStreamLoader;

public class BLSolrRequestHandlerUpdate extends UpdateRequestHandler {

    @Override
    protected Map<String, ContentStreamLoader> createDefaultLoaders(NamedList args) {
        SolrParams p = null;
        if(args!=null) {
            p = args.toSolrParams();
        }
        Map<String, ContentStreamLoader> registry = super.createDefaultLoaders(args);
        registry.put("application/xml", new BLSolrXMLLoader().init(p) );
        registry.put("text/xml", registry.get("application/xml") );
        return registry;
    }
}
