package nl.inl.blacklab.index;

public class BLDocumentFactoryLucene implements BLDocumentFactory {
    public BLInputDocument create() {
        return new BLInputDocumentLucene();
    }
}
