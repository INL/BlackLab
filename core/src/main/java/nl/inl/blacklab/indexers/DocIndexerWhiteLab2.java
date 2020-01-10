/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.blacklab.indexers;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.xml.sax.Attributes;

import nl.inl.blacklab.index.DocIndexerXmlHandlers;
import nl.inl.blacklab.index.DocWriter;
import nl.inl.blacklab.index.HookableSaxHandler.ContentCapturingHandler;
import nl.inl.blacklab.index.HookableSaxHandler.ElementHandler;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;

/**
 * Index a FoLiA file for WhiteLab 2.0. For information about FoLiA, see
 * http://proycon.github.io/folia/ For information about WhiteLab, see
 * https://github.com/Taalmonsters/WhiteLab
 */
public class DocIndexerWhiteLab2 extends DocIndexerXmlHandlers {

    public static String getDisplayName() {
        return "OpenSonar/CGN indexer for WhiteLab2 application";
    }

    public static String getDescription() {
        return "Indexes FoLiA with linked CMDI. Specifically geared towards the Dutch OpenSonar and CGN corpora.";
    }

    String xmlid;

    String wordform;

    String pos;

    String lemma;

    String phonetic;

    String speaker;

    String beginTime;

    String endTime;

    List<String> ids;

    boolean lemPosProblemReported = false;

    int numPhonAnnotations = 0;

    /**
     * If we have 1 PoS annotation, use pos tags without a set attribute. If we have
     * more than one, we use pos tags with
     * set="http://ilk.uvt.nl/folia/sets/frog-mbpos-cgn"
     */
    int numPosAnnotations = 0;

    /**
     * If we have 1 lemma annotation, use lemma tags without a set attribute. If we
     * have more than one, we use pos tags with
     * set="http://ilk.uvt.nl/folia/sets/frog-mblem-nl"
     */
    int numLemmaAnnotations = 0;

    /** Are we capturing the features inside a pos element? */
    boolean capturePosFeatures = false;

    /**
     * The features inside the current pos element, to be indexed as subproperties
     */
    Map<String, String> posFeatures = new HashMap<>();

    public DocIndexerWhiteLab2(DocWriter indexer, String fileName, Reader reader) {
        super(indexer, fileName, reader);

        // Get handles to the default properties (the main one & punct)
        final AnnotationWriter propMain = mainAnnotation();
        final AnnotationWriter propPunct = punctAnnotation();

        // Add some extra properties
        final AnnotationWriter propLemma = addAnnotation("lemma");
        final AnnotationWriter propPartOfSpeech = addAnnotation("pos");
        final AnnotationWriter propPhonetic = addAnnotation("phonetic");
        final AnnotationWriter propXmlid = addAnnotation("xmlid");
        final AnnotationWriter propSpeaker = addAnnotation("speaker");
        final AnnotationWriter propBeginTime = addAnnotation("begin_time");
        final AnnotationWriter propEndTime = addAnnotation("end_time");

        registerContentsField();
        
        // Doc element: the individual documents to index
        addHandler("/FoLiA", new DocumentElementHandler());

        // PoS annotation metadata: see which annotation we need to use.
        addHandler("pos-annotation", new ElementHandler() {
            @Override
            public void startElement(String uri, String localName,
                    String qName, Attributes attributes) {
                super.startElement(uri, localName, qName, attributes);
                numPosAnnotations++;
            }
        });

        // Lemma annotation metadata: see which annotation we need to use.
        addHandler("lemma-annotation", new ElementHandler() {
            @Override
            public void startElement(String uri, String localName,
                    String qName, Attributes attributes) {
                super.startElement(uri, localName, qName, attributes);
                numLemmaAnnotations++;
            }
        });

        addHandler("phon-annotation", new ElementHandler() {
            @Override
            public void startElement(String uri, String localName,
                    String qName, Attributes attributes) {
                super.startElement(uri, localName, qName, attributes);
                numPhonAnnotations++;
            }
        });

        // Word elements: index as main contents
        addHandler("w", new WordHandlerBase() {

            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) {
                super.startElement(uri, localName, qName, attributes);
                xmlid = attributes.getValue("xml:id");
                wordform = "";
                pos = "";
                lemma = "";
                phonetic = "";
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                super.endElement(uri, localName, qName);
                if (wordform.length() > 0) {

                    propMain.addValue(wordform);
                    propXmlid.addValue(xmlid.split("\\.", 2)[1]);
                    ids.add(xmlid);
                    propPartOfSpeech.addValue(pos);
                    for (Entry<String, String> e : posFeatures.entrySet()) {
                        // Add the separate PoS features as extra values at this position.
                        String sep = AnnotatedFieldNameUtil.SUBANNOTATION_SEPARATOR;
                        propPartOfSpeech.addValue(sep + e.getKey() + sep + e.getValue(), 0);
                    }
                    propLemma.addValue(lemma);

                    if (pos.length() == 0 || lemma.length() == 0) {
                        if (!lemPosProblemReported) {
                            lemPosProblemReported = true;
                            System.err.println(
                                    "Word without Pos (set=http://ilk.uvt.nl/folia/sets/frog-mbpos-cgn) and/or lemma (set=http://ilk.uvt.nl/folia/sets/frog-mblem-nl "
                                            + "at " + describePosition());
                        }
                    }

                    if (numPhonAnnotations > 0)
                        propPhonetic.addValue(phonetic);
                    else
                        propPhonetic.addValue("");

                    if (speaker != null)
                        propSpeaker.addValue(speaker);
                    else
                        propSpeaker.addValue("");

                    propPunct.addValue(" ");
                } else {
                    ids.add("");
                }
            }
        });

        // lemma element: contains lemma
        addHandler("lemma", new ElementHandler() {
            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) {
                super.startElement(uri, localName, qName, attributes);
                String set = attributes.getValue("set");
                boolean isSetLess = set == null || set.length() == 0;
                boolean isFrog = !isSetLess && set.equals("http://ilk.uvt.nl/folia/sets/frog-mblem-nl");
                if (numLemmaAnnotations > 2 && isFrog ||
                        numLemmaAnnotations == 1 && isSetLess) {
                    // If there were 2 lemma annotation meta declarations,
                    // we should use the frog ones; if only 1, the ones
                    // without a "set" attribute.
                    lemma = attributes.getValue("class");
                    if (lemma == null)
                        lemma = "";
                }
            }
        });

        // pos element: contains part of speech
        addHandler("pos", new ElementHandler() {
            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) {
                super.startElement(uri, localName, qName, attributes);
                String set = attributes.getValue("set");
                boolean isSetLess = set == null || set.length() == 0;
                boolean isFrog = !isSetLess && set.equals("http://ilk.uvt.nl/folia/sets/frog-mbpos-cgn");
                if (numPosAnnotations > 2 && isFrog ||
                        numPosAnnotations == 1 && isSetLess) {
                    // If there were 2 pos annotation meta declarations,
                    // we should use the frog ones; if only 1, the ones
                    // without a "set" attribute.
                    pos = attributes.getValue("class");
                    if (pos == null)
                        pos = "";
                    // Capture the features inside this pos element (and add the head PoS now)
                    capturePosFeatures = true;
                    posFeatures.clear();
                    String posHead = pos.contains("(") ? pos.substring(0, pos.indexOf('(')).trim() : pos;
                    posFeatures.put("head", posHead);
                }
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                capturePosFeatures = false; // we have all the features
                super.endElement(uri, localName, qName);
            }

        });

        // pos/feat element: contains a part of speech feature
        addHandler("pos/feat", new ElementHandler() {
            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) {
                super.startElement(uri, localName, qName, attributes);
                if (capturePosFeatures) {
                    String featSubset = attributes.getValue("subset");
                    String featClass = attributes.getValue("class");
                    posFeatures.put(featSubset, featClass);
                }
            }
        });

        // ph = phonetic
        addHandler("ph", new ContentCapturingHandler() {
            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) {
                super.startElement(uri, localName, qName, attributes);
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                super.endElement(uri, localName, qName);
                phonetic = getElementContent();
            }
        });

        addHandler("timesegment", new ContentCapturingHandler() {
            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) {
                super.startElement(uri, localName, qName, attributes);
                beginTime = attributes.getValue("begintime");
                endTime = attributes.getValue("endtime");
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                super.endElement(uri, localName, qName);
                beginTime = null;
                endTime = null;
            }
        });

        addHandler("timesegment/wref", new ContentCapturingHandler() {
            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) {
                super.startElement(uri, localName, qName, attributes);
                String wref = attributes.getValue("id");
                String wid = ids.remove(0);
                while (!wid.equals(wref)) {
                    propBeginTime.addValue("", 1);
                    propEndTime.addValue("", 1);
                    wid = ids.remove(0);
                }
                propBeginTime.addValue(beginTime, 1);
                propEndTime.addValue(endTime, 1);
            }
        });

        // t (token) element directly under w (word) element: contains the word form
        addHandler("w/t", new ContentCapturingHandler() {

            /**
             * Tokens with a class attribute are (usually?) the original scanned token
             * before correction, so we skip them
             */
            boolean isOcr;

            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) {
                super.startElement(uri, localName, qName, attributes);
                isOcr = attributes.getValue("class") != null;
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                super.endElement(uri, localName, qName);
                if (!isOcr)
                    wordform = getElementContent();
            }
        });

        // Sentence tags: index as tags in the content
        addHandler("s", new InlineTagHandler() {
            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) {
                speaker = attributes.getValue("speaker");
                ids = new ArrayList<>();
                if (insideElement())
                    super.startElement(uri, localName, qName, attributes);
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                speaker = null;
                while (!ids.isEmpty()) {
                    ids.remove(0);
                    propBeginTime.addValue("");
                    propEndTime.addValue("");
                }
                if (insideElement())
                    super.endElement(uri, localName, qName);
            }
        });

        // Paragraph tags: index as tags in the content
        addHandler("p", new InlineTagHandler());

        // <event/> tags: index as tags in the content
        addHandler("event", new InlineTagHandler());

        // meta elements: metadata fields
        // [NOT USED FOR OPENSONAR..?]
        addHandler("meta", new ContentCapturingHandler() {

            private String metadataFieldName;

            /** Open tag: add metadata field */
            @Override
            public void startElement(String uri, String localName, String qName, Attributes attributes) {
                super.startElement(uri, localName, qName, attributes);
                metadataFieldName = attributes.getValue("id");
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                super.endElement(uri, localName, qName);
                if (metadataFieldName != null)
                    addMetadataField(metadataFieldName, getElementContent());
            }
        });
    }

    public static void main(String[] args) {
        System.out.println("NL B".replaceAll("[\\s\\./]", "_"));
        System.out.println("NL/B".replaceAll("[\\s\\./]", "_"));
        System.out.println("a.b.c.d".replaceAll("[\\s\\./]", "_"));
    }
}
