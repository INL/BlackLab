package nl.inl.blacklab.indexers.config.saxon;

import java.io.IOException;
import java.io.Reader;
import java.util.function.Supplier;

import org.apache.commons.io.IOUtils;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

public class DocumentReferenceReaderProvider extends DocumentReferenceAbstract {

    private final Supplier<Reader> readerProvider;

    public DocumentReferenceReaderProvider(Supplier<Reader> readerProvider) {
        this.readerProvider = readerProvider;
    }

    char[] getBaseDocument() {
        try {
            return IOUtils.toCharArray(readerProvider.get());
        } catch (IOException e) {
            throw new BlackLabRuntimeException(e);
        }
    }

    XIncludeResolver getDummyXIncludeResolver() {
        return new XIncludeResolver() {
            @Override
            public Reader getDocumentReader() {
                return readerProvider.get();
            }

            @Override
            public boolean anyXIncludesFound() {
                return false;
            }
        };
    }
}
