package org.ivdnt.blacklab.aggregator;

import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.ivdnt.blacklab.aggregator.resources.IndexResource;
import org.ivdnt.blacklab.aggregator.resources.RootResource;


/**
 * Add any classes that Jersey needs to be aware of here (or add packages in which to use auto-discovering)
 */
public class JerseyClassRegisterer extends ResourceConfig {

	/*
	// Required for Jackson to be able to serialize JSONObject/JSONArray.
	// Normally a default ObjectMapper is instanced by Jersey, however we need it to have access to the JsonOrgModule.
	// So provide one that does.
	private static class ObjectMapperContextResolver implements ContextResolver<ObjectMapper> {
		private ObjectMapper mapper;

		@SuppressWarnings("unused") // Is only called through reflection
		public ObjectMapperContextResolver() {
			// By default jackson ignores Jaxb annotations, causing things like @XmlAccessorType to not work
			// So we need to explicitly enable the annotations again
			mapper = new JsonMapperConfigurator(null, JacksonJaxbJsonProvider.DEFAULT_ANNOTATIONS).getDefaultMapper();
			//mapper.registerModule(new JsonOrgModule());
		}

		@Override
		public ObjectMapper getContext(Class<?> type) {
			return mapper;
		}
	}*/

	public JerseyClassRegisterer() {
    	super(
			JacksonFeature.class, // Enable Jackson as our JAXB provider

			OutputTypeFilter.class, // "outputformat" parameter overrides Accept header
			CORSFilter.class, // add CORS headers to output

			RootResource.class,
			IndexResource.class,

			GenericExceptionMapper.class);
	}
}
