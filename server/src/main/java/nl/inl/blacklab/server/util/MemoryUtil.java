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
package nl.inl.blacklab.server.util;

/**
 * Utilities to do with JVM memory.
 */
public class MemoryUtil {
    /** Handle to interface with the Java VM environment */
    private static Runtime runtime = Runtime.getRuntime();

    private MemoryUtil() {
    }

    /**
     * Returns the amount of memory that can still be allocated before we get the
     * OutOfMemory exception.
     *
     * @return the amount of memory that can still be allocated
     */
    public static long getFree() {
        return runtime.freeMemory() + (runtime.maxMemory() - runtime.totalMemory());
    }

}
