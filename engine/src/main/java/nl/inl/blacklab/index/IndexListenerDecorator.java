package nl.inl.blacklab.index;

import java.io.File;

/**
 * Decorate an IndexListener.
 */
public abstract class IndexListenerDecorator extends IndexListener {

    IndexListener subject;

    public IndexListenerDecorator(IndexListener subject) {
        this.subject = subject;
    }

    @Override
    public synchronized void fileStarted(String name) {
        subject.fileStarted(name);
    }

    @Override
    public synchronized void fileDone(String name) {
        subject.fileDone(name);
    }

    @Override
    public synchronized void charsDone(long charsDone) {
        subject.charsDone(charsDone);
    }

    @Override
    public synchronized void documentStarted(String name) {
        subject.documentStarted(name);
    }

    @Override
    public synchronized void documentDone(String name) {
        subject.documentDone(name);
    }

    @Override
    public synchronized long getFilesProcessed() {
        return subject.getFilesProcessed();
    }

    @Override
    public synchronized long getCharsProcessed() {
        return subject.getCharsProcessed();
    }

    @Override
    public synchronized long getTokensProcessed() {
        return subject.getTokensProcessed();
    }

    @Override
    public synchronized long getDocsDone() {
        return subject.getDocsDone();
    }

    @Override
    public void indexStart() {
        subject.indexStart();
    }

    @Override
    public void indexEnd() {
        subject.indexEnd();
    }

    @Override
    public void closeStart() {
        subject.closeStart();
    }

    @Override
    public void closeEnd() {
        subject.closeEnd();
    }

    @Override
    public long getIndexTime() {
        return subject.getIndexTime();
    }

    @Override
    public long getOptimizeTime() {
        return subject.getOptimizeTime();
    }

    @Override
    public long getCloseTime() {
        return subject.getCloseTime();
    }

    @Override
    public void indexerCreated(Indexer indexer) {
        subject.indexerCreated(indexer);
    }

    @Override
    public void indexerClosed() {
        subject.indexerClosed();
    }

    @Override
    public long getTotalTime() {
        return subject.getTotalTime();
    }

    @Override
    public void luceneDocumentAdded() {
        subject.luceneDocumentAdded();
    }

    @Override
    public synchronized void tokensDone(int n) {
        subject.tokensDone(n);
    }

    @Override
    public synchronized boolean errorOccurred(Throwable e, String path, File f) {
        return subject.errorOccurred(e, path, f);
    }

    @Override
    public int getErrors() {
        return subject.getErrors();
    }

    @Override
    public void rollbackStart() {
        subject.rollbackStart();
    }

    @Override
    public void rollbackEnd() {
        subject.rollbackEnd();
    }

}
