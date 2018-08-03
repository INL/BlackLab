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
package nl.inl.blacklab;

import java.io.IOException;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.spans.Spans;
import org.junit.Assert;

import nl.inl.blacklab.search.lucene.SpansInBuckets;
import nl.inl.util.StringUtil;

public class TestUtil {
    public static void assertEquals(Spans expected, Spans actual) throws IOException {
        assertEquals(expected, actual, false);
    }

    public static void assertEquals(Spans expected, Spans actual, boolean skipFirstNextDoc) throws IOException {
        int docNumber = 0, hitNumber;
        boolean firstDoc = true;
        while (true) {
            int actualDocId;
            if (firstDoc && skipFirstNextDoc) {
                // Actual Spans already skipped to document for testing. Don't .nextDoc() this time.
                firstDoc = false;
                actualDocId = actual.docID();
            } else {
                actualDocId = actual.nextDoc();
            }
            docNumber++;
            hitNumber = 0;
            Assert.assertEquals(StringUtil.ordinal(docNumber) + " doc id", expected.nextDoc(), actualDocId);
            Assert.assertEquals(StringUtil.ordinal(docNumber) + " doc id", expected.docID(), actual.docID());
            Assert.assertEquals(StringUtil.ordinal(docNumber) + " doc id", actualDocId, actual.docID());
            if (actualDocId == DocIdSetIterator.NO_MORE_DOCS)
                break;
            Assert.assertEquals(-1, actual.startPosition());
            Assert.assertEquals(-1, actual.endPosition());
            boolean first = true;
            while (true) {
                int actualStartPos = actual.nextStartPosition();
                if (first) {
                    // .nextDoc() should always place us in a document with at least 1 hit
                    first = false;
                    Assert.assertFalse(actualStartPos == Spans.NO_MORE_POSITIONS);
                }
                hitNumber++;
                Assert.assertEquals(hitDesc(docNumber, hitNumber) + ": start pos", expected.nextStartPosition(),
                        actualStartPos);
                Assert.assertEquals(hitDesc(docNumber, hitNumber) + ": start pos", expected.startPosition(),
                        actual.startPosition());
                Assert.assertEquals(hitDesc(docNumber, hitNumber) + ": start pos", actualStartPos,
                        actual.startPosition());
                Assert.assertEquals(hitDesc(docNumber, hitNumber) + ": end pos", expected.endPosition(),
                        actual.endPosition());
                if (actualStartPos == Spans.NO_MORE_POSITIONS) {
                    Assert.assertEquals(StringUtil.ordinal(docNumber) + " doc id", actualDocId, actual.docID());
                    Assert.assertEquals(hitDesc(docNumber, hitNumber) + ": start pos", Spans.NO_MORE_POSITIONS,
                            actual.startPosition());
                    Assert.assertEquals(hitDesc(docNumber, hitNumber) + ": end pos", Spans.NO_MORE_POSITIONS,
                            actual.endPosition());
                    break;
                }
            }
        }
    }

    private static String hitDesc(int docNumber, int hitNumber) {
        return StringUtil.ordinal(hitNumber) + " hit in " + StringUtil.ordinal(docNumber) + " doc";
    }

    public static void assertEquals(SpansInBuckets expected, SpansInBuckets actual) throws IOException {
        while (true) {
            int actualDocId = actual.nextDoc();
            Assert.assertEquals(expected.nextDoc(), actualDocId);
            Assert.assertEquals(expected.docID(), actual.docID());
            Assert.assertEquals(actualDocId, actual.docID());
            if (actualDocId == DocIdSetIterator.NO_MORE_DOCS)
                break;
            boolean first = true;
            while (true) {
                int nextBucketRv = actual.nextBucket();
                if (first) {
                    // .nextDoc() should always place us in a document with at least 1 bucket
                    first = false;
                    Assert.assertFalse(nextBucketRv == SpansInBuckets.NO_MORE_BUCKETS);
                }
                Assert.assertEquals(expected.nextBucket(), nextBucketRv);
                if (nextBucketRv == SpansInBuckets.NO_MORE_BUCKETS)
                    break;
                Assert.assertFalse(actual.bucketSize() == 0); // no empty buckets
                Assert.assertEquals(expected.bucketSize(), actual.bucketSize());
                for (int i = 0; i < actual.bucketSize(); i++) {
                    Assert.assertEquals(expected.startPosition(i), actual.startPosition(i));
                    Assert.assertEquals(expected.endPosition(i), actual.endPosition(i));
                }
            }
        }
    }

}
