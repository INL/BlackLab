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
package nl.inl.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.junit.Assert;
import org.junit.Test;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

public class TestXmlUtil {

    @Test
    public void testXmlToPlainText() {
        // Remove tags
        Assert.assertEquals("test test test", XmlUtil.xmlToPlainText("test <bla>test</bla> test"));

        // Interpret entities
        Assert.assertEquals("test > test", XmlUtil.xmlToPlainText("test &gt; test"));

        // Interpret numerical entities
        Assert.assertEquals("test A test", XmlUtil.xmlToPlainText("test &#65; test"));

        // Interpret hex numerical entities
        Assert.assertEquals("test B test", XmlUtil.xmlToPlainText("test &#x42; test"));

        // Ignore entities inside tags
        Assert.assertEquals("test test", XmlUtil.xmlToPlainText("test <bla test=\"&quot;\" > test"));

        // Other whitespace characters normalized to space
        Assert.assertEquals("test test", XmlUtil.xmlToPlainText("test\ntest"));

        // Normalize whitespace; keep leading space
        Assert.assertEquals(" test test", XmlUtil.xmlToPlainText("\t\ttest \n\rtest"));

        // Replace with non-breaking spaces
        Assert.assertEquals("test\u00A0test", XmlUtil.xmlToPlainText("test test", true));

        // Replace with non-breaking spaces; keep trailing space
        Assert.assertEquals("test\u00A0test\u00A0", XmlUtil.xmlToPlainText("test test ", true));
    }

    @Test
    public void testYamlMultiLineWithCanonical() throws Exception {
        String []keys = new String[]{"SomeKeyString:\n\nAnotherLine", "SomeKeyInteger:\n\nAnotherLine","SomeKeyBoolean:\n\nAnotherLine", "Simple"};
        ObjectMapper jsonMapper = Json.getJsonObjectMapper();
        ObjectNode jsonRoot = jsonMapper.createObjectNode();
        jsonRoot.put(keys[0], "valstring");
        jsonRoot.put(keys[1], 20);
        jsonRoot.put(keys[2], true);
        jsonRoot.put(keys[3], "simple");

        ObjectMapper yamlObjectMapper = Json.getYamlObjectMapper();
        yamlObjectMapper.configure(JsonParser.Feature.STRICT_DUPLICATE_DETECTION, true);
        StringWriter swriter = new StringWriter();

        yamlObjectMapper.writeValue(swriter, jsonRoot);

        ObjectMapper readMapper =  Json.getYamlObjectMapper();
        ObjectNode readJsonRoot = (ObjectNode) readMapper.readTree(swriter.toString());
        Assert.assertEquals("valstring", Json.getString(readJsonRoot, keys[0], ""));
        Assert.assertEquals(20, Json.getInt(readJsonRoot, keys[1], 0));
        Assert.assertEquals(true, Json.getBoolean(readJsonRoot, keys[2], false));
        Assert.assertEquals("simple", Json.getString(readJsonRoot, keys[3], ""));
    }


}
