package nl.inl.blacklab.server.exceptions;


public class ConfigurationException extends InternalServerError {

	public ConfigurationException() {
		super("Configuration exception", 29, null);
	}

	public ConfigurationException(String msg) {
		super(msg, 29, null);
	}

	public ConfigurationException(String msg, Throwable cause) {
		super(msg + (cause == null ? "" : " (" + cause + ")"), 29, cause);
	}

}
