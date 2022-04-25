package org.ivdnt.blacklab.aggregator.resources;

import java.io.StringWriter;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang3.StringUtils;
import org.xml.sax.SAXException;

import nl.inl.anw.AnwArticle;
import nl.inl.anw.ArticleSelection;
import nl.inl.anw.ArticleStatus;
import nl.inl.anw.ArticleTransformer;
import nl.inl.anw.ArtikelDatabase;
import nl.inl.anw.ArtikelDatabase.WrappedResultSet;
import nl.inl.anw.ArtikelException;
import nl.inl.anw.ArtikelFase;
import nl.inl.anw.ArtikelFases;
import nl.inl.anw.ArtikelFlag;
import nl.inl.anw.ArtikelUserException;
import nl.inl.anw.Project;
import nl.inl.anw.Util;
import nl.inl.anw.api.ResponseMessage;
import nl.inl.anw.api.representation.Article;
import nl.inl.anw.api.representation.ArticleCreationRequest;
import nl.inl.anw.api.representation.ArticleResults;
import nl.inl.infotree.InfoTree;
import nl.inl.util.XmlUtil;

@Path("/articles")
public class Articles {

    // Neo:
    // - in [alpha]productie nemen (anwi-ontwikkelserver)
    // - testen met Lex'it link om artikelen aan te maken
    //
    // Later?
    // - elementvalue, checklemmalist: subsets(?)
    //
    // https://blog.mwaysolutions.com/2014/06/05/10-best-practices-for-better-restful-api/
    // WOULD BE NICE:
    // - X-Total-Count header to include total number of records?
    //   (requires extra count(*) query)

    private static final Map<String, String> sortNameToDbColumnMap = new HashMap<>();

    static {
        sortNameToDbColumnMap.put("editor", "redacteur");
        sortNameToDbColumnMap.put("mtime", "laatste_wijziging");
        sortNameToDbColumnMap.put("ctime", "create_time");
        sortNameToDbColumnMap.put("lockedBy", "gelockt_door");
        sortNameToDbColumnMap.put("phase", "artikelfase");

        // Switch to Saxon
        System.setProperty("javax.xml.transform.TransformerFactory", "net.sf.saxon.TransformerFactoryImpl");
        //System.setProperty("javax.xml.parsers.DocumentBuilderFactory", "net.sf.saxon.om.DocumentBuilderFactoryImpl");
    }

    @Context
    UriInfo uriInfo;

    /**
     * Retrieve a list of articles conforming to search parameters.
     *
     * @param request request object
     * @param lemmaSearch lemma search string (wildcards allowed)
     * @param articleId article id (NOT pid!) to search for
     * @param articlePid article pid to search for
     * @param lastEditDays edited int the last X days
     * @param editorUserName edited by
     * @param includePhaseIds phases to include
     * @param includeSubsetIds subsets to include
     * @param xmlLike LIKE query to add
     * @param xpath XPath query to add
     * @param offset first result to return
     * @param limit max. number of results to return
     * @param sort desired sort
     * @return list of articles
     * @throws Exception
     */
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response find(
            @Context HttpServletRequest request,
            @DefaultValue("") @QueryParam("lemma") String lemmaSearch,
            @DefaultValue("0") @QueryParam("id") int articleId,
            @DefaultValue("0") @QueryParam("pid") int articlePid,
            @DefaultValue("0") @QueryParam("lastedit") int lastEditDays,
            @DefaultValue("") @QueryParam("editor") String editorUserName,
            @DefaultValue("") @QueryParam("phase") String includePhaseIds,
            @DefaultValue("") @QueryParam("subset") String includeSubsetIds,
            @DefaultValue("") @QueryParam("xmllike") String xmlLike,
            @DefaultValue("") @QueryParam("xpath") String xpath,
            @DefaultValue("0") @QueryParam("offset") int offset,
            @DefaultValue("1000") @QueryParam("limit") int limit,
            @DefaultValue("") @QueryParam("sort") String sort
            ) throws Exception {

        try (ArtikelDatabase db = ArtikelDatabase.getConnection()) {

            // Create the selection
            ArticleSelection selection = new ArticleSelection();
            if (!lemmaSearch.isEmpty())
                selection.setLemmavorm(lemmaSearch);
            if (articlePid > 0)
                articleId = Util.getArticleIdFromPid(articlePid, db);
            if (articleId > 0)
                selection.setArticleId(articleId);
            if (lastEditDays > 0)
                selection.setDagenSindsLaatsteBewerking(lastEditDays);
            if (!editorUserName.isEmpty())
                selection.setRedacteur(ArtikelDatabase.getUser(editorUserName));
            if (!includePhaseIds.isEmpty()) {
                List<ArtikelFase> phases = Arrays.stream(includePhaseIds.split(","))
                        .map(id -> ArtikelDatabase.getArtikelFase(Integer.parseInt(id)))
                        .filter(fase -> fase != null)
                        .collect(Collectors.toList());
                selection.setFases(ArtikelFases.list(phases));
            }
            if (!includeSubsetIds.isEmpty()) {
                selection.setSubsets(includeSubsetIds.split(","));
            }
            if (!xmlLike.isEmpty()) {
                selection.setXmlLike(xmlLike);
            }
            if (!xpath.isEmpty())
                selection.setXpath(xpath);
            selection.setOffset(offset);
            selection.setLimit(limit + 1); // +1 to check if there's more records
            if (!sort.isEmpty() && sort.matches("^\\-?[a-zA-Z0-9]+$")) {
                if (sort.charAt(0) == '-') {
                    selection.setReverseOrder(true);
                    sort = sort.substring(1);
                }
                if (sortNameToDbColumnMap.containsKey(sort)) {
                    sort = sortNameToDbColumnMap.get(sort);
                }
                selection.setOrderBy(sort);
            } else {
                selection.setOrderByLemma();
            }

            // Find articles and create list of objects to serialize
            List<Article> results = new ArrayList<>();
            try (WrappedResultSet wrs = db.getLijstArtikelen(selection)) {
                ResultSet rs = wrs.resultSet();
                while (rs.next()) {
                    results.add(new Article(db, rs, xpath, false));
                }
            }
            boolean hasNextPage = results.size() > limit;
            if (hasNextPage)
                results = results.subList(0, limit);
            ArticleResults articleResults = new ArticleResults(results,
            		lemmaSearch, articleId, articlePid, lastEditDays, editorUserName,
            		includePhaseIds, includeSubsetIds, xmlLike, xpath, offset, limit, hasNextPage, sort);
            return Response.ok(articleResults).build();
        }
    }

    private static String getArticleXml(ArtikelDatabase db, AnwArticle articleByPid) {
        try {
            // NOTE: getting the infotree first applies inheritance, which we need to correctly display the article!
            //  (usually not a problem, except if information was added to the 'kopje' part after filling in senses)
            InfoTree infoTree = articleByPid.getInfoTree(db);
            StringWriter out = new StringWriter();
            infoTree.serialize(out, true, false);
            return out.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieve article XML by its lemma.
     *
     * @param lemma the article's lemma
     * @return article content
     * @throws ArtikelException
     */
    @GET
    @Path("/lemma:{lemma}")
    @Produces(MediaType.APPLICATION_XML)
    public String getXml(@PathParam("lemma") String lemma) throws ArtikelException {
        try (ArtikelDatabase db = ArtikelDatabase.getConnection()) {
            return getArticleXml(db, new AnwArticle(db, lemma));
        }
    }

    /**
     * Retrieve article HTML by its lemma.
     *
     * @param lemma the article's lemma
     * @return HTML rendition of article
     * @throws ArtikelException
     */
    @GET
    @Path("/lemma:{lemma}")
    @Produces(MediaType.TEXT_HTML)
    public String getHtml(@PathParam("lemma") String lemma) throws ArtikelException {
        try (ArtikelDatabase db = ArtikelDatabase.getConnection()) {
            AnwArticle article = new AnwArticle(db, lemma);
            String articleXml = getArticleXml(db, article);
            return ArticleTransformer.htmlFromArticle(articleXml, article.getArtikelFase(db).isKopjeFase());
        }
    }

    /**
     * Retrieve article XML by its pid (persistent identifier).
     *
     * @param articlePid the article's pid
     * @return article content
     * @throws ArtikelException
     */
    @GET
    @Path("/{pid}")
    @Produces(MediaType.APPLICATION_XML)
    public String getXml(@PathParam("pid") int articlePid) throws ArtikelException {
        try (ArtikelDatabase db = ArtikelDatabase.getConnection()) {
            return getArticleXml(db, Util.getArticleByPid(db, articlePid));
        }
    }

    /**
     * Retrieve article HTML by its pid (persistent identifier).
     *
     * @param articlePid the article's pid
     * @return HTML rendition of article
     * @throws ArtikelException
     */
    @GET
    @Path("/{pid}")
    @Produces(MediaType.TEXT_HTML)
    public String getHtml(@PathParam("pid") int articlePid) throws ArtikelException {
        try (ArtikelDatabase db = ArtikelDatabase.getConnection()) {
            AnwArticle article = Util.getArticleByPid(db, articlePid);
            String articleXml = getArticleXml(db, article);
            return ArticleTransformer.htmlFromArticle(articleXml, article.getArtikelFase(db).isKopjeFase());
        }
    }

    /**
     * Update article
     *
     * @param articlePid the article's pid
     * @param xml new content of the article. If empty: only resave article to update any external information.
     * @return success or failure response
     * @throws ArtikelException
     */
    @PUT
    @Path("/{pid}")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response putXml(@PathParam("pid") int articlePid, String xml) throws ArtikelException {
        try (ArtikelDatabase db = ArtikelDatabase.getConnection()) {

            // Find the articleId corresponding to the pid
            int articleId;
            try {
                articleId = Util.getArticleIdFromPid(articlePid, db);
            } catch (Exception e1) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ResponseMessage("Article with pid " + articlePid + " not found"))
                        .build();
            }

            // Update the article
            AnwArticle article = new AnwArticle(db, articleId);
            String lemma = article.getLemma();
            db.startTransaction();
            try {
                ArticleStatus editFlag = article.getArtikelStatus(db);
                if (editFlag != ArticleStatus.FREE) {
                    return Response.status(Response.Status.FORBIDDEN)
                            .entity(new ResponseMessage("Article with pid " + articlePid + " (" + lemma + ") is in use"))
                            .build();
                }

                String optAddMsg = "";
                InfoTree infoTree;
                if (StringUtils.isBlank(xml)) {
                    // No XML given. Read the previous version of the article.
                    infoTree = article.getInfoTree(db);
                    optAddMsg = " (resave)";
                } else {
                    // XML given. Parse it.
                    infoTree = AnwArticle.articleStringToInfoTree(xml);
                }
                Project.get().onOpenDocument(infoTree);  // update lexicon info
                article.putInfoTree(db, infoTree, true); // (will also add missing pids and update pid/link db tables)
                db.commit();
                return Response.ok(new ResponseMessage("Article with pid " + articlePid + " (" + lemma + ") succesfully updated" + optAddMsg))
                        .build();
            } catch (Exception e) {
                db.rollback();
                throw e;
            }
        } catch (ArtikelUserException e) {
            throw e;
        } catch (Exception e) {
            throw new ArtikelException("Error updating article", e);
        }
    }

    /**
     * Create article from template
     *
     * @param req request info
     * @return success or failure response. On success, the responseJSON contains the new article's properties
     * @throws ArtikelException
     */
    @POST
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response create(ArticleCreationRequest req) throws ArtikelException {
        try (ArtikelDatabase db = ArtikelDatabase.getConnection()) {

            // Check that the article doesn't exist yet
            String lemma = req.getLemma();
            if (lemma.length() == 0)
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ResponseMessage("You didn't specify a lemma"))
                        .build();
            if (AnwArticle.exists(db, lemma)) {
                return Response.status(Response.Status.CONFLICT)
                        .entity(new ResponseMessage("Article " + lemma + " already exists"))
                        .build();
            }

            // Create the article
            String xmlTemplate = req.getXmlTemplate();
            if (!isValidXml(xmlTemplate)) {
                throw new ArtikelException("Article is not valid XML");
            }
            AnwArticle article = AnwArticle.create(db, lemma, AnwArticle.Type.NORMAAL, xmlTemplate);

            // Set subset(s)
            List<String> subsets = req.getSubsets();
            if (subsets != null) {
                int flags = article.getArtikelFlags(db);
                int allSubsetsMask = ArtikelDatabase.getArtikelFlagsIdMap(ArtikelDatabase.FLAG_TYPE_SUBSET).values().stream()
                        .map(ArtikelFlag::getId).reduce(0, (a, b) -> a | b);
                flags = flags & ~allSubsetsMask; // clear all subsets
                int newFlags = subsets.stream()
                        .map(name -> {
                            ArtikelFlag flag = ArtikelDatabase.getArtikelFlag(name);
                            if (flag == null) {
                                throw new RuntimeException("Unknown subset specified: " + name);
                            }
                            return flag.getId();
                        })
                        .reduce(flags, (a, b) -> a | b);
                article.setArtikelFlags(db, newFlags);
            }

            // Return success response
            return Response.ok(new Article(db, article.getId(), null, false)).build();
        } catch (ArtikelUserException e) {
            throw e;
        } catch (Exception e) {
            throw new ArtikelException("Error creating article", e);
        }
    }

    private static boolean isValidXml(String xmlTemplate) {
        try {
            XmlUtil.parseXml(xmlTemplate);
            return true;
        } catch (SAXException e) {
            return false;
        }
    }

    /**
     * Delete article
     *
     * @param pid the article's pid
     * @return success or failure response
     * @throws ArtikelException
     */
    @DELETE
    @Path("/{pid}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response delete(@PathParam("pid") int pid) throws ArtikelException {
        try (ArtikelDatabase db = ArtikelDatabase.getConnection()) {

            // Find the articleId corresponding to the pid
            int articleId;
            try {
                articleId = Util.getArticleIdFromPid(pid, db);
                db.deleteArtikel(articleId);
                return Response.ok(new ResponseMessage("Article with pid " + pid + " deleted")).build();
            } catch (ArtikelUserException e) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ResponseMessage("Could not delete article with pid " + pid + "; is it still being linked to?"))
                        .build();
            } catch (Exception e) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ResponseMessage("Article with pid " + pid + " not found"))
                        .build();
            }

        } catch (Exception e) {
            throw new ArtikelException("Error creating article", e);
        }
    }

}
