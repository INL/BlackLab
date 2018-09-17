package nl.inl.blacklab.server.exceptions;

public class ConfigurationException extends InternalServerError {

    public ConfigurationException() {
        super("Configuration exception", "INTERR_CONFIG", null);
    }

    public ConfigurationException(String msg) {
        super(msg, "INTERR_CONFIG", null);
    }

    public ConfigurationException(String msg, Throwable cause) {
        super(msg + (cause == null ? "" : " (" + cause + ")"), "INTERR_CONFIG", cause);
    }

}
