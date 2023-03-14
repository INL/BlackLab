package org.ivdnt.blacklab.solr;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.NotImplementedException;
import org.apache.lucene.index.IndexReader;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.ContentStream;
import org.apache.solr.handler.loader.ContentStreamLoader;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.update.AddUpdateCommand;
import org.apache.solr.update.CommitUpdateCommand;
import org.apache.solr.update.processor.UpdateRequestProcessor;

import nl.inl.blacklab.index.BLIndexObjectFactorySolr;
import nl.inl.blacklab.index.BLIndexWriterProxySolr;
import nl.inl.blacklab.index.BLInputDocumentSolr;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.indexers.config.InputFormatReader;
import nl.inl.blacklab.search.BlackLabEngine;
import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;

/**
 * This class is the main entrypoint for SOLR indexing in BlackLab.
 * What happens is:
 * - solrconfig.xml specifies a requesthandler for /update {@link BLSolrRequestHandlerUpdate}
 * - that class registers a loader for content-type xml, which is this class
 * - this class is supposed to parse the xml into a SolrInputDocument and pass it on to the UpdateRequestProcessor
 *
 * The UpdateRequestProcessor is actually a chain, more specifically, whatever is returned by the configured UpdateRequestProcessorChain.
 * The process of how the chain is configured/created is described here: https://solr.apache.org/guide/8_1/update-request-processors.html
 */
public class BLSolrXMLLoader extends ContentStreamLoader {



    @Override
    public void load(SolrQueryRequest req, SolrQueryResponse rsp, ContentStream stream, UpdateRequestProcessor processor) throws Exception {
        BlackLabSearchComponent searchComponent = (BlackLabSearchComponent) req.getCore().getSearchComponent(BlackLabSearchComponent.COMPONENT_NAME);
        BlackLabEngine blacklab = searchComponent.getSearchManager().blackLabInstance();
        blacklab.setIndexObjectFactory(BLIndexObjectFactorySolr.INSTANCE);
        
        // find the directory
        SolrParams params = req.getParams();
        IndexReader reader = req.getSearcher().getIndexReader();

        // todo something like "bl.method"
        if ("add".equals(params.get("bl.format"))) {
            // parse request body as inputformat
            byte[] file = stream.getStream().readAllBytes();

            String name = params.get("bl.filename");
            if (DocumentFormats.isSupported(name)) {
                return;
            }
            ConfigInputFormat f = new ConfigInputFormat(name);
            try {
                InputFormatReader.read(new InputStreamReader(new ByteArrayInputStream(file)), false, f, __ -> Optional.empty());
            } catch (Exception e) {
                InputFormatReader.read(new InputStreamReader(new ByteArrayInputStream(file)), true, f, __ -> Optional.empty());
            }
            DocumentFormats.registerFormat(f);
            return;
        }

        ConfigInputFormat format = DocumentFormats.getConfigInputFormat(params.get("bl.format"));
        if (format == null) {
            // format isn't recognized by name, try loading it as string (it might be the contents of the file).
            format = new ConfigInputFormat("");
            String formatString = params.get("bl.format");
            boolean isJson = formatString.trim().charAt(0) == '{';
            Reader r = new StringReader(formatString);
            InputFormatReader.read(r, isJson, format, linkedFormat -> null);
        }
        
        String fileName = params.get("bl.filename");
        String indexName = req.getCore().getName();
        try (BlackLabIndexWriter index = blacklab.openForWriting(indexName, reader, format)) {
            Indexer indexer = Indexer.create(index, params.get("bl.format"));
            InputStream is = stream.getStream();

            indexer.index(fileName, is);
            // Do this after indexing, so the metadata is up-to-date
            synchronizeSolrSchema(index, req);

            // now, after updating the schema to add new fields, actually send the documents down the chain.
            BLIndexWriterProxySolr blSolrWriter = (BLIndexWriterProxySolr) index.writer();
            for (BLInputDocumentSolr doc : blSolrWriter.getPendingAddDocuments()) {
                AddUpdateCommand cmd = new AddUpdateCommand(null);
                cmd.solrDoc = doc.getDocument();
                cmd.overwrite = true;
                cmd.setReq(req);
                processor.processAdd(cmd);
                // ends up in DirectUpdateHandler2::addDoc0
                // eventually gets to DefaultIndexingChain::processDocument
                // fields go to DefaultIndexingChain::processField
            }

            processor.processCommit(new CommitUpdateCommand(req, false));

            IOUtils.closeQuietly(is);
        }
    }

    // Code adapted from AddSchemaFieldsUpdateProcessor
    void synchronizeSolrSchema(BlackLabIndexWriter index, SolrQueryRequest req) {
        IndexSchema oldSchema = req.getSchema();
        IndexMetadataWriter metadata = index.metadata();

        synchronized (oldSchema.getSchemaUpdateLock()) {
            List<SchemaField> newFields = new ArrayList<>();

            // Register special fields, token length (length of document), content store contents, and content store id.
            // these are not listed in the index metadata, because they are internal blacklab fields
            // so they won't appear during the generic field registration loop
            // but we still have to tell solr about them!
            for (AnnotatedField af : metadata.annotatedFields()) {
                Map<String, Object> options = new HashMap<>();
                options.put("docValues", true);
                if (!oldSchema.hasExplicitField(af.tokenLengthField())) {
                    SchemaField newField = oldSchema.newField(af.tokenLengthField(), "metadata_numeric", options);
                    newFields.add(newField);
                }
                if (af.hasContentStore() && !oldSchema.hasExplicitField(AnnotatedFieldNameUtil.contentStoreField(af.name()))) {
                    SchemaField newField = oldSchema.newField(AnnotatedFieldNameUtil.contentStoreField(af.name()), "metadata_untokenized", Collections.emptyMap());
                    newFields.add(newField);
                }
                if (!oldSchema.hasExplicitField(af.contentIdField())) {
                    SchemaField newField = oldSchema.newField(af.contentIdField(), "metadata_numeric", options);
                    newFields.add(newField);
                }
            }

            // Now tell solr about all of the regular metadata in documents.
            metadata.metadataFields().stream().filter(mf -> !oldSchema.hasExplicitField(mf.name())).forEach(mf -> {
                String fieldType = "";
                if (mf.equals(metadata.metadataFields().pidField()))
                    fieldType = "metadata_pid";
                else if (mf.type().equals(FieldType.UNTOKENIZED))
                    fieldType = "metadata_untokenized";
                else if (mf.type().equals(FieldType.TOKENIZED))
                    fieldType = "metadata_tokenized";
                else if (mf.type().equals(FieldType.NUMERIC))
                    fieldType = "metadata_numeric";
                else
                    throw new NotImplementedException("Unknown FieldType");
                SchemaField newField = oldSchema.newField(mf.name(), fieldType, Collections.emptyMap());
                newFields.add(newField);
            });

            // and finally, tell solr about the annotations (e.g. word, lemma, pos, and their various sensitivities)
            metadata.annotatedFields().stream()
                .flatMap(af -> af.annotations().stream())
                .flatMap(annot -> annot.sensitivities().stream())
                .filter(annotSensitivity -> !oldSchema.hasExplicitField(annotSensitivity.luceneField()))
                .forEach(missingAnnot -> {

                    // these properties have been adapted from
                    boolean offsets =
                            missingAnnot.annotation().field().mainAnnotation() == missingAnnot.annotation();
                    boolean contentstore = false; // I think, since the contentstore field is not listed in the indexmetadata object
                    boolean indexed = !contentstore; // gotten from BLFieldTypeLucene
                    boolean tokenized = indexed; // annotations are always tokenized? (TODO doublecheck)

                    // see FieldProperties for supported options.
                    Map<String, Object> fieldAttributes = new HashMap<>();
                    fieldAttributes.put("indexed", indexed);
                    fieldAttributes.put("tokenized", indexed);
                    fieldAttributes.put("stored", contentstore);
                    fieldAttributes.put("binary", false);
                    fieldAttributes.put("omitNorms", true); // always true
                    fieldAttributes.put("omitTermFreqAndPositions", !(indexed || tokenized));
                    fieldAttributes.put("termVectors", indexed);
                    fieldAttributes.put("termPositions", indexed);
                    fieldAttributes.put("termOffsets", indexed && offsets);
                    fieldAttributes.put("multiValued", true); // I suppose this is always true for annotations
                    fieldAttributes.put("sortMissingFirst", false);
                    fieldAttributes.put("sortMissingLast", false);
                    fieldAttributes.put("required", false);
                    fieldAttributes.put("omitPositions", !(indexed || offsets));
                    fieldAttributes.put("storeOffsetsWithPositions", offsets);
                    fieldAttributes.put("docValues", false);
                    fieldAttributes.put("termPayloads", missingAnnot.luceneField().contains("starttag")); // FIXME: is correct (only the inline tags have payloads currently), but this way of determining is icky
                    fieldAttributes.put("useDocValuesAsStored", false);
                    fieldAttributes.put("large", false);
                    fieldAttributes.put("uninvertible", false);

                    SchemaField f = oldSchema.newField(missingAnnot.luceneField(), "annotation", fieldAttributes);
                    newFields.add(f);
                });

            IndexSchema newSchema = oldSchema.addFields(newFields, Collections.emptyMap(), true);
            if (newSchema == null) {
                throw new RuntimeException("Error adding new fields to SOLR schema");
            }
            req.getCore().setLatestSchema(newSchema);
            req.updateSchemaToLatest();
        }
    }
}
