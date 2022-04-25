import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

import org.glassfish.jersey.linking.Binding;
import org.glassfish.jersey.linking.InjectLink;
import org.glassfish.jersey.linking.InjectLink.Style;

import nl.inl.anw.ArtikelDatabase;
import nl.inl.anw.ArtikelDatabase.WrappedResultSet;
import nl.inl.anw.User;
import nl.inl.anw.Util;
import nl.inl.anw.api.representation.ExceptionObj;
import nl.inl.anw.api.representation.LogRecord;
import nl.inl.anw.util.Bijbelhaken;

@Path("/reports")
public class Reports {
    
    @XmlAccessorType(XmlAccessType.FIELD)
    @SuppressWarnings("unused")
    public static class ArticleElementValue {

        // This should be a link to /articles/{pid}
        @InjectLink(
            resource = Articles.class,
            method = "getXml",
            style = Style.ABSOLUTE_PATH,
            bindings = {
                @Binding(name="pid", value="${instance.pid}")
            }
        )
        private URI href;
        
        @XmlTransient
        private int pid;
        
        private String lemma;

        private String value;
        
        public ArticleElementValue(int pid, String lemma, String value) {
            super();
            this.pid = pid;
            this.lemma = lemma;
            this.value = value;
        }
        
        // required for Jersey
        public ArticleElementValue() {}

        public int getPid() {
            return pid;
        }

    }
    
    /**
     * Find articles with the specified element, and return the element value.
     * 
     * Used for Leescommentaar, among potential other uses.
     * 
     * @param elementName element to look for
     * @return list of articles with the element values
     * @throws Exception
     */
    @GET
    @Path("/elementValue")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<ArticleElementValue> elementValues(@DefaultValue("") @QueryParam("element") String elementName) throws Exception {
        if (!elementName.matches("^[a-zA-Z0-9\\-]+$"))
            throw new IllegalArgumentException("Illegal element name");
        try (ArtikelDatabase db = ArtikelDatabase.getConnection()) {
            List<ArticleElementValue> results = new ArrayList<>();
            try (WrappedResultSet wrs = db.getReportElementValues(elementName)) {
                ResultSet rs = wrs.resultSet();
                while (rs.next()) {
                    int pid = rs.getInt("pid");
                    String lemma = new String(rs.getBytes("lemma"), StandardCharsets.UTF_8);
                    byte[] v = rs.getBytes("value");
                    String value;
                    if (v != null)
                        value = v == null ? null : new String(v, StandardCharsets.UTF_8);
                    else {
                        // HACK: MariaDB has a problem with deeply-nested XML, and will sometimes return NULL for this reason (warning: too deep XML)
                        // Instead, fetch the XML and execute xpath ourselves
                        value = Util.evaluateXpath(db, lemma, "//" + elementName);
                    }
                    results.add(new ArticleElementValue(pid, lemma, value));
                }
                return results;
            }
        }
    }
    
    /**
     * Get list of recent exceptions.
     * 
     * @return recent exceptions
     * @throws Exception
     */
    @GET
    @Path("/exceptions")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<ExceptionObj> exceptions() throws Exception {
        try (ArtikelDatabase db = ArtikelDatabase.getConnection()) {
            List<ExceptionObj> results = new ArrayList<>();
            try (WrappedResultSet wrs = db.getRecentExceptions(1000)) {
                ResultSet rs = wrs.resultSet();
                while (rs.next()) {
                    Date errorTime = rs.getDate("errortime");
                    User editor = ArtikelDatabase.getUser(rs.getInt("editor"));
                    String lemma = new String(rs.getBytes("lemma"), StandardCharsets.UTF_8);
                    String text = new String(rs.getBytes("exceptiontext"), StandardCharsets.UTF_8);
                    results.add(new ExceptionObj(errorTime, editor, lemma, text));
                }
            }
            return results;
        }
    }
    
    /**
     * Get list of recent log records.
     * 
     * @return recent exceptions
     * @throws Exception
     */
    @GET
    @Path("/log")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<LogRecord> log() throws Exception {
        try (ArtikelDatabase db = ArtikelDatabase.getConnection()) {
            List<LogRecord> results = new ArrayList<>();
            try (WrappedResultSet wrs = db.getRecentLogRecords(1000)) {
                ResultSet rs = wrs.resultSet();
                while (rs.next()) {
                    int level = rs.getInt("level");
                    Date time = rs.getDate("tijdstip");
                    User user = ArtikelDatabase.getUser(rs.getInt("user"));
                    byte[] l = rs.getBytes("lemma");
                    String lemma = l == null ? "" : new String(l, StandardCharsets.UTF_8);
                    int pid = rs.getInt("articlePid");
                    String actie = new String(rs.getBytes("actie"), StandardCharsets.UTF_8);
                    String info = new String(rs.getBytes("info"), StandardCharsets.UTF_8);
                    results.add(new LogRecord(level, time, user, lemma, pid, actie, info));
                }
            }
            return results;
        }
    }
    
    /**
     * Given a list of lemmas, return the list of lemmas that don't exist
     * 
     * @param list list to check
     * @return list of lemmas that don't exist
     * @throws Exception
     */
    @POST
    @Path("/checkLemmaList")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<String> checkLemmaListPost(String list) throws Exception {
        List<String> lemmas = Arrays.asList(list.split("[\\r\\n;]+"));
        try (ArtikelDatabase db = ArtikelDatabase.getConnection()) {
            return db.reportNonexistentLemmas(lemmas);
        }
    }
    
    /**
     * Given a list of lemmas, return the list of lemmas that don't exist
     * 
     * @param list list to check
     * @return list of lemmas that don't exist
     * @throws Exception
     */
    @GET
    @Path("/checkLemmaList")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<String> checkLemmaListGet(@QueryParam("list") String list) throws Exception {
        List<String> lemmas = Arrays.asList(list.split("[\\r\\n;]+"));
        try (ArtikelDatabase db = ArtikelDatabase.getConnection()) {
            return db.reportNonexistentLemmas(lemmas);
        }
    }
    
    /**
     * Expand the "bijbelhaken" syntax
     * 
     * @param input input value
     * @return expanded value
     * @throws Exception
     */
    @GET
    @Path("/bijbelhaken")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<String> bijbelhaken(@QueryParam("input") String input) throws Exception {
        return Bijbelhaken.expand(input);
    }
}
