package nl.inl.blacklab.interfaces.test;

import java.io.IOException;

import nl.inl.blacklab.interfaces.results.HitGroup;
import nl.inl.blacklab.interfaces.results.ResultProperty;
import nl.inl.blacklab.interfaces.results.ResultPropertyValue;

/** Property that looks at the document a hit occurs in */
public final class DocPropertyAuthor implements ResultProperty<HitGroup> {
    
    private static DocPropertyAuthor instance = new DocPropertyAuthor();
    
    public static DocPropertyAuthor get() {
        return instance;
    }
    
    @Override
    public ResultPropertyValue get(HitGroup result) {
        return ResultPropertyValueString.get(getAuthor(result));
    }

    private static String getAuthor(HitGroup result) {
        try {
            return ((ResultPropertyValueBlDoc)result.identity()).doc().luceneDoc().get("author");
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }
    
    @Override
    public int compare(HitGroup a, HitGroup b) {
        return getAuthor(a).compareTo(getAuthor(b));
    }

    @Override
    public String serialize() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isReverse() {
        return false;
    }

    @Override
    public String name() {
        return "author";
    }
}