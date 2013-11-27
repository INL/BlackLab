package nl.inl.blacklab.testutil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import nl.inl.blacklab.search.Searcher;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.document.SetBasedFieldSelector;
import org.apache.lucene.index.IndexReader;

public class GetFieldValues {
	public static void main(String[] args) throws Exception {

		if (args.length < 2) {
			System.err.println("Usage: GetFieldValues <indexDir> <fieldName1> <fieldName2> ...");
			return;
		}

		File indexDir = new File(args[0]);
		List<String> fieldNames = new ArrayList<String>();
		for (int i = 1; i < args.length; i++) {
			fieldNames.add(args[i]);
		}

		Map<String, Set<String>> fieldValues = new HashMap<String, Set<String>>();
		Searcher.setAutoWarmForwardIndices(false);
		Searcher searcher = Searcher.open(indexDir);
		try {
			IndexReader r = searcher.getIndexReader();

//			String[] values = FieldCache.DEFAULT.getStrings(r, fieldName);
//			for (int i = 0; i < values.length; i++) {
//				String value = values[i];
//				if (value != null)
//					uniq.add(value);
//			}

			HashSet<String> fieldsToLoad = new HashSet<String>();
			for (String fieldToLoad: fieldNames) {
				fieldsToLoad.add(fieldToLoad);
			}
			HashSet<String> lazyFieldsToLoad = new HashSet<String>();
			FieldSelector fieldSelector = new SetBasedFieldSelector(fieldsToLoad, lazyFieldsToLoad);

			int numDocs = r.numDocs();
			for (int i = 1; i < numDocs; i++) {
//				if (i % 1000 == 0)
//					System.out.println(i + "...");
				Document d = r.document(i, fieldSelector);
				for (String fieldName: fieldNames) {
					String value = d.get(fieldName);
					if (value != null) {
						Set<String> uniq;
						uniq = fieldValues.get(fieldName);
						if (uniq == null) {
							uniq = new TreeSet<String>(); // TreeSet auto-sorts
							fieldValues.put(fieldName, uniq);
						}
						uniq.add(value);
					}
				}
			}

//			TermEnum te = r.terms();
//			boolean found = false;
//			while (te.next()) {
//				Term term = te.term();
//				if (term.field().equals(fieldName)) {
//					found = true;
//					uniq.add(term.text());
//				} else if (found) {
//					// We've seen all values for the field we're interested in.
//					break;
//				}
//			}
//			te.close();
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
