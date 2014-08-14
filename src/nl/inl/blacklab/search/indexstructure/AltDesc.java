package nl.inl.blacklab.search.indexstructure;

/** Description of a property alternative */
public class AltDesc {
	/** Types of property alternatives */
	public enum AltType {
		UNKNOWN, SENSITIVE
	}

	/** name of this alternative */
	private String altName;

	/** type of this alternative */
	private AltType type;

	public AltDesc(String name) {
		altName = name;
		type = name.equals("s") ? AltType.SENSITIVE : AltType.UNKNOWN;
	}

	@Override
	public String toString() {
		return altName;
	}

	/** Get the name of this alternative
	 * @return the name
	 */
	public String getName() {
		return altName;
	}

	/** Get the type of this alternative
	 * @return the type
	 */
	public AltType getType() {
		return type;
	}
}