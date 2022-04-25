package org.ivdnt.blacklab.aggregator.resources;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;

import nl.inl.anw.ArtikelDatabase;
import nl.inl.anw.ArtikelException;
import nl.inl.anw.ArtikelFatalException;
import nl.inl.anw.Pid;
import nl.inl.anw.api.representation.ExternalLinkObj;
import nl.inl.anw.api.representation.ExternalLinkResults;
import nl.inl.anw.externalLink.ExternalLink;
import nl.inl.anw.externalLink.ExternalLinkResource;
import nl.inl.anw.externalLink.ExternalLinkStatus;

@Path("/external-links")
public class ExternalLinks {

    static final int MAX_RECORDS = 10000;

    @GET
    @Path("/targets")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<String> listTargetResources() {
        return ArtikelDatabase.getExternalLinkResources().stream().map(lr -> lr.toString()).collect(Collectors.toList());
    }

    @GET
    @Path("/{resource}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public ExternalLinkResults list(
    		@PathParam("resource") String resourceName,
    		@DefaultValue("-1") @QueryParam("status") int status,
            @DefaultValue("0") @QueryParam("offset") int offset,
            @DefaultValue("100") @QueryParam("limit") int limit,
            @DefaultValue("") @QueryParam("dst_ids") String dstIds,
            @DefaultValue("0") @QueryParam("lastedit") int lastEditDays) throws ArtikelFatalException {
        List<Integer> ids = dstIds.isEmpty() ? null :
            Arrays.stream(dstIds.split(",")).map(strId -> Integer.parseInt(strId)).collect(Collectors.toList());
        return getExternalLinks(resourceName, status, offset, limit, lastEditDays, ids);
    }

    @GET
    @Path("/{resource}/{id}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public ExternalLinkResults get(
    		@PathParam("resource") String resourceName,
    		@PathParam("id") int dstId,
            @DefaultValue("-1") @QueryParam("status") int status,
            @DefaultValue("0") @QueryParam("offset") int offset,
            @DefaultValue("100") @QueryParam("limit") int limit,
            @DefaultValue("0") @QueryParam("lastedit") int lastEditDays) throws ArtikelFatalException {
        List<Integer> ids = Arrays.asList(dstId);
        return getExternalLinks(resourceName, status, offset, limit, lastEditDays, ids);
    }

    private static ExternalLinkResults getExternalLinks(String resourceName, int status, int offset, int limit,
            int lastEditDays, List<Integer> ids) throws ArtikelFatalException {
        if (limit < 0 || limit > MAX_RECORDS)
            limit = MAX_RECORDS;

        boolean hasNext = false;
        List<ExternalLinkObj> linkObjects;// = getExternalLinks(resourceName, ids, status, offset, limit, lastEditDays);
        ExternalLinkResource resource = ArtikelDatabase.getExternalLinkResource(resourceName);
        if (resource == null) {
            linkObjects = Collections.emptyList();
        } else {
            try (ArtikelDatabase db = ArtikelDatabase.getConnection()) {
                ExternalLinkStatus objStatus = status < 0 ? null : ArtikelDatabase.getExternalLinkStatus(status);
                List<ExternalLink> links = db.getExternalLinks(resource, ids, objStatus, offset, limit + 1, lastEditDays);

                hasNext = links.size() > limit;

                linkObjects = links.stream().limit(limit).map(l -> {
                    try {
                        Pid srcPid = db.getPid(l.getSrcPid());
                        int articleId = srcPid.getArticleId();
                        List<Pid> articlePids = db.getPidsForArticle(articleId, Arrays.asList("art"));
                        int articlePid = articlePids.isEmpty() ? -1 : articlePids.get(0).getId();
                        return new ExternalLinkObj(srcPid.getLemma(db), articlePid, srcPid.getId(), srcPid.getType().toString().toLowerCase(), srcPid.getDescription(), resource.toString(), l.getDstId(), l.getStatus().getId());
                    } catch (ArtikelException e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toList());
            }
        }

        return new ExternalLinkResults(linkObjects, resourceName, status, offset, limit, hasNext, StringUtils.join(ids, ","), lastEditDays);
    }
}
