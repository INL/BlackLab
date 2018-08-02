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
package nl.inl.blacklab.index;

/**
 * Used to report progress while indexing, so we can give feedback to the user.
 */
public class IndexListenerReportConsoleVerbose extends IndexListener {
    @Override
    public synchronized void fileStarted(String name) {
        super.fileStarted(name);
        System.out.println("File started: " + name);
    }

    @Override
    public synchronized void fileDone(String name) {
        super.fileDone(name);
        System.out.println("File done: " + name);
    }

    @Override
    public synchronized void charsDone(long charsDone) {
        super.charsDone(charsDone);
        long elapsed = getElapsed();
        if (elapsed == 0)
            elapsed = 1;
        long chars = getCharsProcessed();
        System.out.printf("%04d  Chars done total: %d, %d CPS%n", elapsed, chars, chars / elapsed);
    }

    private long getElapsed() {
        return (System.currentTimeMillis() - getIndexStartTime()) / 1000;
    }

    @Override
    public void closeEnd() {
        super.closeEnd();
        System.out.println("Closing index complete.");
    }

    @Override
    public void closeStart() {
        super.closeStart();
        System.out.println("Closing index...");
    }

    @Override
    public synchronized void documentDone(String name) {
        super.documentDone(name);
        System.out.println("Document done: " + name);
    }

    @Override
    public synchronized void documentStarted(String name) {
        super.documentStarted(name);
        System.out.println("Document started: " + name);
    }

    @Override
    public void indexEnd() {
        super.indexEnd();
        System.out.println("Done indexing.");
    }

    @Override
    public void indexerClosed() {
        super.indexerClosed();
        System.out.println("Indexer closed.");
    }

    @Override
    public void indexerCreated(Indexer indexer) {
        super.indexerCreated(indexer);
        System.out.println("Indexer created.");
    }

    @Override
    public void indexStart() {
        super.indexStart();
        System.out.println("Start indexing.");
    }

    @Override
    public synchronized void luceneDocumentAdded() {
        super.luceneDocumentAdded();
        // System.out.println("Lucene doc added.");
    }

    @Override
    public synchronized void tokensDone(int n) {
        super.tokensDone(n);
        System.out.println("Tokens done total: " + getTokensProcessed());
    }
}
