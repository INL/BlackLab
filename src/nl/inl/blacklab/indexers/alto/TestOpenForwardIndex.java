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
package nl.inl.blacklab.indexers.alto;

import java.io.File;

import nl.inl.blacklab.forwardindex.ForwardIndex;

/**
 * Test program for analyzing performance of opening forward index (old).
 */
public class TestOpenForwardIndex {
	public static void main(String[] args) throws Exception {
		File dir = new File("D:\\dev\\blacklab\\pagexml\\index\\forward");
		ForwardIndex fi = ForwardIndex.open(dir);

		Thread.sleep(1000 * 60); // Allow time to analyze memory

		fi.close();
	}
}
