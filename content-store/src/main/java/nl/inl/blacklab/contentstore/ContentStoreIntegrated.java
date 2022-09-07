package nl.inl.blacklab.contentstore;

import java.io.IOException;
import java.util.Set;

public class ContentStoreIntegrated implements ContentStore {
    @Override
    public int store(TextContent content) {
        return 0;
    }

    @Override
    public void storePart(TextContent content) {

    }

    @Override
    public String retrieve(int id) {
        return null;
    }

    @Override
    public String[] retrieveParts(int id, int[] start, int[] end) {
        return new String[0];
    }

    @Override
    public void close() {

    }

    @Override
    public void delete(int id) {

    }

    @Override
    public void clear() throws IOException {

    }

    @Override
    public Set<Integer> idSet() {
        return null;
    }

    @Override
    public boolean isDeleted(int id) {
        return false;
    }

    @Override
    public int docLength(int id) {
        return 0;
    }

    @Override
    public void initialize() {

    }
}
