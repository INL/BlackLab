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
package nl.inl.blacklab.queryParser.corpusql;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.textpattern.TextPattern;

public class TestParser {
    public static void main(String[] args) throws IOException {
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            System.out.print("CorpusQL> ");
            System.out.flush();
            String expr = stdin.readLine();
            if (expr.length() == 0) {
                System.out.println("EXIT");
                break;
            }
            try {
                TextPattern result = CorpusQueryLanguageParser.parse(expr);
                System.out.println("Result: " + result + "\n");
            } catch (InvalidQuery e) {
                e.printStackTrace(System.err);
            }
            System.out.flush();
            System.err.flush();
        }
    }
}
