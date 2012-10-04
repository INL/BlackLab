/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.queryParser.corpusql;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;

public class TestParser {
	public static void main(String[] args) throws IOException, InterruptedException {
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
				CorpusQueryLanguageParser parser = new CorpusQueryLanguageParser(new StringReader(
						expr));
				System.out.println("Result: " + parser.query() + "\n");
			} catch (TokenMgrError e) {
				e.printStackTrace(System.err);
			} catch (ParseException e) {
				e.printStackTrace(System.err);
			}
			Thread.sleep(100);
		}
	}
}
