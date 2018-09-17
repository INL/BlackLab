package nl.inl.blacklab.requestlogging;

import java.io.Closeable;
import java.io.IOException;

public interface SearchLogger extends Closeable {

    void log(LogLevel level, String line);

    void setResultsFound(int resultsFound);

    @Override
    void close() throws IOException;
    
    @Override
    String toString();

}
