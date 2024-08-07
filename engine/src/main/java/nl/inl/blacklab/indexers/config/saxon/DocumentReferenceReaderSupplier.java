package nl.inl.blacklab.indexers.config.saxon;

import java.io.Reader;
import java.util.function.Supplier;

public class DocumentReferenceReaderSupplier extends DocumentReferenceAbstract {

    private final Supplier<Reader> readerSupplier;

    public DocumentReferenceReaderSupplier(Supplier<Reader> readerSupplier) {
        this.readerSupplier = readerSupplier;
    }

    public Supplier<Reader> getBaseDocReaderSupplier() {
        return readerSupplier;
    }
}
