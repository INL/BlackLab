package nl.inl.blacklab.debug;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.store.FSDirectory;

public class RunSpanTermQuery {

	public static void main(String[] args) throws IOException {
		String term = "koekenbakker";
		String fieldName = "contents%word@s";
		if (args.length >= 1)
			term = args[0];
		if (args.length >= 2)
			fieldName = args[1];

		// Open the index
		DirectoryReader reader = null;
		try {
			reader = DirectoryReader.open(FSDirectory.open(new File(".")));
		} catch (Exception e) {
			System.err.println("Error opening index; is the current directory a Lucene index?");
			usage();
			System.exit(1);
		}

		SpanQuery spanQuery = new SpanTermQuery(new Term(fieldName, term));
		spanQuery = (SpanQuery) spanQuery.rewrite(reader);

		Map<Term, TermContext> termContexts = new HashMap<Term, TermContext>();
		TreeSet<Term> terms = new TreeSet<Term>();
		spanQuery.extractTerms(terms);
		for (Term termForContext: terms) {
			termContexts.put(termForContext, TermContext.build(reader.getContext(), termForContext, true));
		}

		SlowCompositeReaderWrapper scrw = new SlowCompositeReaderWrapper(reader);
		try {
			Spans spans = spanQuery.getSpans(scrw.getContext(), scrw.getLiveDocs(), termContexts);
			System.out.println("Hits for '" + term + "'");
			while(spans.next()) {
				System.out.println(String.format("[%5d] %4d-%4d", spans.doc(), spans.start(), spans.end()));
			}
		} finally {
			scrw.close();
		}
	}

	private static void usage() {
		System.out.println("\nThis tool searches for a term and prints docs and positions.\n");
		System.out.println("  RunSpanTermQuery <term> [fieldName]");
	}

}
