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
package nl.inl.blacklab.search;


/**
 * A concordance (left context, hit text, right context).
 * Hits class matches this to the Hit.
 */
public class Concordance {

	/** Left side of concordance */
	public String left;

	/** Hit text of concordance */
	public String hit;

	/** Right side of concordance */
	public String right;

	/**
	 * Construct a hit object
	 *
	 * @param conc
	 *            concordance information
	 */
	public Concordance(String[] conc) {
		this.left = conc[0];
		this.hit = conc[1];
		this.right = conc[2];
	}

	@Override
	public String toString() {
		return String.format("conc: %s [%s] %s", left, hit, right);
	}

}
