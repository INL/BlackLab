package nl.inl.blacklab.search.grouping;

public class HitPropValueString extends HitPropValue {
	String value;

	public HitPropValueString(String value) {
		this.value = value;
	}

	@Override
	public int compareTo(Object o) {
		return HitPropValue.collator.compare(value, ((HitPropValueString)o).value);
	}

	@Override
	public int hashCode() {
		return value.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return value.equals(((HitPropValueString)obj).value);
	}

	@Override
	public String toString() {
		return value;
	}
}
