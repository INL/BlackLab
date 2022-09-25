package nl.inl.blacklab.server.lib;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexAbstract;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.util.BlsUtils;

public class ResultDocInfo {

    private final SearchCreator params;

    private final String docPid;

    private final Set<MetadataField> metadataToWrite;

    private Document document;

    private Map<String, List<String>> metadata;

    private Integer lengthInTokens;

    private boolean mayView;

    public ResultDocInfo(SearchCreator params, String docPid, Set<MetadataField> metadataToWrite) throws BlsException {
        this.params = params;
        this.docPid = docPid;
        this.metadataToWrite = metadataToWrite;
        getDocInfo();
    }

    private void getDocInfo() throws BlsException {
        if (docPid.length() == 0)
            throw new BadRequest("NO_DOC_ID", "Specify document pid.");

        BlackLabIndex blIndex = params.blIndex();
        int luceneDocId = BlsUtils.getDocIdFromPid(blIndex, docPid);
        if (luceneDocId < 0)
            throw new NotFound("DOC_NOT_FOUND", "Document with pid '" + docPid + "' not found.");
        this.document = blIndex.luceneDoc(luceneDocId);
        if (document == null)
            throw new InternalServerError("Couldn't fetch document with pid '" + docPid + "'.",
                    "INTERR_FETCHING_DOCUMENT_INFO");

        metadata = new LinkedHashMap<>();
        for (MetadataField f: metadataToWrite) {
            if (f.name().equals("lengthInTokens") || f.name().equals("mayView"))
                continue;
            String[] values = document.getValues(f.name());
            if (values.length == 0)
                continue;
            metadata.put(f.name(), List.of(values));
        }
        String tokenLengthField = blIndex.mainAnnotatedField().tokenLengthField();
        lengthInTokens = null;
        if (tokenLengthField != null) {
            lengthInTokens =
                    Integer.parseInt(document.get(tokenLengthField)) - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
        }
        mayView = blIndex.mayView(document);

    }

    public String getPid() {
        return docPid;
    }

    public Document getDocument() {
        return document;
    }

    public Map<String, List<String>> getMetadata() {
        return metadata;
    }

    public Integer getLengthInTokens() {
        return lengthInTokens;
    }

    public boolean isMayView() {
        return mayView;
    }

}
