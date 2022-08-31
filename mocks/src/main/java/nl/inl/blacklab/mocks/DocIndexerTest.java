package nl.inl.blacklab.mocks;

import java.io.Reader;

import org.apache.commons.lang3.StringUtils;
import org.xml.sax.Attributes;

import nl.inl.blacklab.index.DocIndexerXmlHandlers;
import nl.inl.blacklab.index.DocWriter;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.index.annotated.AnnotationSensitivities;

public class DocIndexerTest extends DocIndexerXmlHandlers {
    public DocIndexerTest(DocWriter indexer, String fileName, Reader reader) {
        super(indexer, fileName, reader);

        // Get handles to the default properties (the main one & punct)
        final AnnotationWriter propMain = mainAnnotation();
        final AnnotationWriter propPunct = punctAnnotation();

        // Add some extra properties
        final AnnotationWriter propLemma = addAnnotation("lemma", AnnotationSensitivities.SENSITIVE_AND_INSENSITIVE);
        final AnnotationWriter propPartOfSpeech = addAnnotation("pos", AnnotationSensitivities.ONLY_INSENSITIVE);
        
        registerContentsField();

        // Doc element: the individual documents to index
        addHandler("/doc", new DocumentElementHandler());

        // Word elements: index as main contents
        addHandler("w", new WordHandlerBase() {

            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) {
                super.startElement(uri, localName, qName, attributes);
                addOneOrMoreValues(propLemma, StringUtils.defaultIfEmpty(attributes.getValue("l"), ""));
                addOneOrMoreValues(propPartOfSpeech, StringUtils.defaultIfEmpty(attributes.getValue("p"), ""));
                propPunct.addValue(consumeCharacterContent());
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                super.endElement(uri, localName, qName);
                addOneOrMoreValues(propMain,consumeCharacterContent());
            }
        });

        // Sentence and entity tags: index as inline tags (used in tests)
        addHandler("s", new InlineTagHandler());
        addHandler("entity", new InlineTagHandler());

    }

    private void addOneOrMoreValues(AnnotationWriter annot, String strValues) {
        String[] values = strValues.split("\\|", -1);
        int posIncr = 1;
        for (String value: values) {
            annot.addValue(value, posIncr);
            posIncr = 0;
        }
    }

}
