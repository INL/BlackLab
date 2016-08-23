/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.blacklab.search.grouping;


/**
 * Abstract base class for a property of a hit, like document title, hit text, right context, etc.
 */
public abstract class GroupProperty {

	static GroupPropertyIdentity propIdentity = new GroupPropertyIdentity();

	static GroupPropertySize propSize = new GroupPropertySize();

	public static GroupPropertyIdentity identity() { return propIdentity; }

	public static GroupPropertySize size() { return propSize; }

	/** Reverse comparison result or not? */
	protected boolean reverse = false;

	public abstract HitPropValue get(Group result);

	public abstract int compare(Group a, Group b);

	public boolean defaultSortDescending() {
		return reverse;
	}

	public abstract String serialize();

	/**
	 * Used by subclasses to add a dash for reverse when serializing
	 * @return either a dash or the empty string
	 */
	protected String serializeReverse() {
		return reverse ? "-" : "";
	}

	public static GroupProperty deserialize(String serialized) {
		boolean reverse = false;
		if (serialized.length() > 0 && serialized.charAt(0) == '-') {
			reverse = true;
			serialized = serialized.substring(1);
		}
		GroupProperty result;
		if (serialized.equalsIgnoreCase("identity"))
			result = new GroupPropertyIdentity();
		else
			result = new GroupPropertySize();
		result.setReverse(reverse);
		return result;
	}

	/**
	 * Is the comparison reversed?
	 * @return true if it is, false if not
	 */
	public boolean isReverse() {
		return reverse;
	}

	/**
	 * Set whether to reverse the comparison.
	 * @param reverse if true, reverses comparison
	 */
	public void setReverse(boolean reverse) {
		this.reverse = reverse;
	}

	@Override
	public String toString() {
		return serialize();
	}

}
