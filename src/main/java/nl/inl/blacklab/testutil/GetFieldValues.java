package nl.inl.blacklab.testutil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DocumentStoredFieldVisitor;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.search.Searcher;

public class GetFieldValues {
	public static void main(String[] args) throws Exception {

		if (args.length < 2) {
			System.err.println("Usage: GetFieldValues <indexDir> <fieldName1> <fieldName2> ...");
			return;
		}

		File indexDir = new File(args[0]);
		List<String> fieldNames = new ArrayList<>();
		for (int i = 1; i < args.length; i++) {
			fieldNames.add(args[i]);
		}

		Map<String, Set<String>> fieldValues = new HashMap<>();
		Searcher searcher = Searcher.open(indexDir);
		try {
			IndexReader r = searcher.getIndexReader();

			Set<String> fieldsToLoad = new HashSet<>();
			for (String fieldToLoad: fieldNames) {
				fieldsToLoad.add(fieldToLoad);
			}
			/* OLD:
			HashSet<String> lazyFieldsToLoad = new HashSet<String>();
			FieldSelector fieldSelector = new SetBasedFieldSelector(fieldsToLoad, lazyFieldsToLoad);
			*/
			DocumentStoredFieldVisitor fieldVisitor = new DocumentStoredFieldVisitor(fieldsToLoad);

			int numDocs = r.numDocs();
			for (int i = 1; i < numDocs; i++) {
				//Document d = r.document(i, fieldSelector);
				r.document(i, fieldVisitor);
				Document d = fieldVisitor.getDocument();
				for (String fieldName: fieldNames) {
					String value = d.get(fieldName);
					if (value != null) {
						Set<String> uniq;
						uniq = fieldValues.get(fieldName);
						if (uniq == null) {
							uniq = new TreeSet<>(); // TreeSet auto-sorts
							fieldValues.put(fieldName, uniq);
						}
						uniq.add(value);
					}
				}
			}
		} finally {
			searcher.close();
		}

		for (Map.Entry<String,Set<String>> e: fieldValues.entrySet()) {
			System.out.println("\n### " + e.getKey() + ":");
			for (String term: e.getValue()) {
				System.out.println(term);
			}
		}
	}
}
