package nl.inl.blacklab.instrumentation;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * RequestInstrumentationProvider provides information used
 * to augment logging messages and other instrumentation information.
 */
public interface RequestInstrumentationProvider {

    /**
     * Returns a request id that will be logged in each blacklab
     * request
     * @return the request id
     */
    Optional<String> getRequestID(HttpServletRequest request);

    /**
     * A RequestIdProvider that does not create a request id
     */
    static RequestInstrumentationProvider noOpProvider() {
        return new RequestInstrumentationProvider() {
            @Override
            public Optional<String> getRequestID(HttpServletRequest request) {
                return Optional.empty();
            }
        };
    }
}


