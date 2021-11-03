package nl.inl.blacklab.exceptions;

/**
 * Server overloaded, e.g. tried to queue a search but already at maximum queued searches.
 */
public class ServerOverloaded extends BlackLabRuntimeException {

    public ServerOverloaded(String msg) {
        super(msg);
    }
}
