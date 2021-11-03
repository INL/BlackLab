package nl.inl.blacklab.index;

import java.io.StringReader;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

final class DummyEntityResolver implements EntityResolver {
    @Override
    public InputSource resolveEntity(String publicId, String systemId) {
        return new InputSource(new StringReader("")); // ignore DTDs
    }
}
