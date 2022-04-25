package org.ivdnt.blacklab.aggregator;

import javax.ws.rs.ext.ContextResolver;

import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.linking.DeclarativeLinkingFeature;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.message.DeflateEncoder;
import org.glassfish.jersey.message.GZipEncoder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.filter.EncodingFilter;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JsonMapperConfigurator;

import nl.inl.anw.api.resources.Articles;
import nl.inl.anw.api.resources.ExternalLinks;
import nl.inl.anw.api.resources.Index;
import nl.inl.anw.api.resources.Phases;
import nl.inl.anw.api.resources.Reports;
import nl.inl.anw.api.resources.Users;

/**
 * Add any classes that Jersey needs to be aware of here (or add packages in which to use auto-discovering)
 */
public class JerseyClassRegisterer extends ResourceConfig {

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
			mapper.registerModule(new JsonOrgModule());
		}

		@Override
		public ObjectMapper getContext(Class<?> type) {
			return mapper;
		}
	}

	public JerseyClassRegisterer() {
    	super(	// Enable gzip compression
    			EncodingFilter.class,
    			GZipEncoder.class,
    			DeflateEncoder.class,

    			MultiPartFeature.class,
    			JacksonFeature.class, // Enable Jackson as our JAXB provider
    			RolesAllowedDynamicFeature.class, // Enable authentication and user roles
                DeclarativeLinkingFeature.class, // Enable link injection in serialized resources

    			ObjectMapperContextResolver.class, // so Jackson can serialize JSONObject/JSONArray

    			OutputTypeFilter.class, // "outputtype" parameter overrides Accept header
    			CORSFilter.class, // add CORS headers to output

    			Index.class,
    			Articles.class,
    			Users.class,
    			Phases.class,
    			Reports.class,
    			ExternalLinks.class,

    			GenericExceptionMapper.class);
	}
}
