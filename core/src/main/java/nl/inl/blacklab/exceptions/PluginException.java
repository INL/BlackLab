package nl.inl.blacklab.exceptions;

public class PluginException extends BlackLabException {
    public PluginException() {
        super();
    }

    public PluginException(String message, Throwable cause) {
        super(message, cause);
    }

    public PluginException(String message) {
        super(message);
    }

    public PluginException(Throwable cause) {
        super(cause);
    }
}