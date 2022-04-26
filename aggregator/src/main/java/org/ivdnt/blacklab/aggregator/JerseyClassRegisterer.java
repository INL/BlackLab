package org.ivdnt.blacklab.aggregator;

import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.ivdnt.blacklab.aggregator.resources.Aggregator;
import org.ivdnt.blacklab.aggregator.resources.Root;


/**
 * Add any classes that Jersey needs to be aware of here (or add packages in which to use auto-discovering)
 */
public class JerseyClassRegisterer extends ResourceConfig {

	public JerseyClassRegisterer() {
    	super(
    			JacksonFeature.class, // Enable Jackson as our JAXB provider

    			OutputTypeFilter.class, // "outputtype" parameter overrides Accept header
    			CORSFilter.class, // add CORS headers to output

				Root.class,
    			Aggregator.class,

    			GenericExceptionMapper.class);
	}
}
