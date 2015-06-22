package nl.inl.blacklab.debug;

import java.io.File;
import java.io.IOException;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import nl.inl.util.LuceneUtil;
import nl.inl.util.StringUtil;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocsAndPositionsEnum;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.store.FSDirectory;

public class RunTermQuery {

	static int matchingDoc = -1;

	static int matchPosition = -1;

	static boolean docsFound = false;

	public static void main(String[] args) throws IOException {
		String word = "koekenbakker";
		String fieldName = "contents%word@s";
		if (args.length >= 1)
			word = args[0];
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

		Term term = new Term(fieldName, word);
		System.out.println("TERM: " + term.text());

		System.out.println("  Total frequency: " + term.text() + ": " + reader.totalTermFreq(term) + "\n");

		doQuery(term, reader);
		doSpanQuery(term, reader);
		if (matchingDoc != -1)
			doTermVector(matchingDoc, term, reader);

		if (matchPosition != -1)
			doSnippet(matchingDoc, matchPosition, term, reader);
	}

	private static void doSnippet(int docId, int position, Term term,
			DirectoryReader reader) {

		System.out.println("\nSNIPPET");

		int first = position - 10;
		if (first < 0)
			first = 0;
		int number = 20;
		String[] words = LuceneUtil.getWordsFromTermVector(reader, docId, term.field(), first, first + number, true);

		// Print result
		int i = 0;
		StringBuilder b = new StringBuilder();
		for (String word: words) {
			if (word == null)
				word = "[MISSING]";
			b.append(i + first).append(":").append(word).append(" ");
			i++;
		}
		System.out.println(StringUtil.wrapText(b.toString(), 80));
	}

	private static void doTermVector(int doc, Term term, DirectoryReader reader) {
		System.out.println("\nTERM VECTOR FOR DOC " + doc);
		String luceneName = term.field();
		String word = term.text();
		try {
			org.apache.lucene.index.Terms terms = reader.getTermVector(doc, luceneName);
			if (terms == null) {
				System.out.println("Field " + luceneName + " has no Terms");
				return;
			}
			System.out.println(
				"  Doc count:           " + terms.getDocCount() + "\n" +
				"  Sum doc freq:        " + terms.getSumDocFreq() + "\n" +
				"  Sum total term freq: " + terms.getSumDocFreq() + "\n" +
				"  Has offsets:         " + terms.hasOffsets() + "\n" +
				"  Has payloads:        " + terms.hasPayloads() + "\n" +
				"  Has positions:       " + terms.hasPositions() + "\n");

			TermsEnum termsEnum = terms.iterator(null);
			if (!termsEnum.seekExact(term.bytes(), true)) {
				System.out.println("Term " + word + " not found.");
				return;
			}

			System.out.println("\n" +
				"  TERM '" + termsEnum.term().utf8ToString() + "':\n" +
				"    Doc freq:        " + termsEnum.docFreq() + "\n" +
				"    Ord:             " + termsEnum.ord() + "\n" +
				"    Total term freq: " + termsEnum.totalTermFreq() + "\n");

			if (terms.hasPositions()) {
				// Verzamel concordantiewoorden uit term vector
				DocsAndPositionsEnum docPosEnum = termsEnum.docsAndPositions(null, null);
				System.out.println("    POSITIONS:");
				while (docPosEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {

					System.out.println(
							"      Doc id: " + docPosEnum.docID() + "\n" +
							"      Freq:   " + docPosEnum.freq() + "\n");

					for (int i = 0; i < docPosEnum.freq(); i++)  {
						int position = docPosEnum.nextPosition();
						int start = docPosEnum.startOffset();
						if (start >= 0) {
							// Character offsets stored
							int end = docPosEnum.startOffset();
							System.out.println("      " + position + " (offsets: " + start + "-" + end + ")");
						} else {
							// No character offsets stored
							System.out.println("      " + position);
						}
						if (matchPosition < 0)
							matchPosition = position;
					}
				}
			} else {
				// No positions
				DocsEnum docsEnum = termsEnum.docs(null, null);
				System.out.println("\n    DOCS:");
				while (docsEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
					System.out.println(
							"      Doc id: " + docsEnum.docID() + "\n" +
							"      Freq:   " + docsEnum.freq() + "\n");
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void doQuery(Term term, DirectoryReader reader) throws IOException {
		Query query = new TermQuery(term);
		query = query.rewrite(reader);
		System.out.println("REGULAR QUERY");

		IndexSearcher searcher = new IndexSearcher(reader);
		final BitSet bits = new BitSet(reader.maxDoc());
		docsFound = false;
		searcher.search(query, new Collector() {
			private int docBase;

			@Override
			public void setScorer(Scorer scorer) {
				// ignore scorer
			}

			// accept docs out of order (for a BitSet it doesn't matter)
			@Override
			public boolean acceptsDocsOutOfOrder() {
				return true;
			}

			@Override
			public void collect(int doc) {
				bits.set(doc + docBase);
				System.out.println(String.format("  doc %7d", doc + docBase));
				matchingDoc = doc + docBase;
				docsFound = true;
			}

			@Override
			public void setNextReader(AtomicReaderContext context) {
				this.docBase = context.docBase;
			}
		});
		if (!docsFound)
			System.out.println("  (no matching docs)");
		System.out.println("");
	}

	private static void doSpanQuery(Term term, DirectoryReader reader) throws IOException {
		SpanQuery spanQuery = new SpanTermQuery(term);
		spanQuery = (SpanQuery) spanQuery.rewrite(reader);

		Map<Term, TermContext> termContexts = new HashMap<Term, TermContext>();
		TreeSet<Term> terms = new TreeSet<Term>();
		spanQuery.extractTerms(terms);
		for (Term termForContext: terms) {
			termContexts.put(termForContext, TermContext.build(reader.getContext(), termForContext, true));
		}

		System.out.println("SPANQUERY");

		System.out.println("USING LEAVES:");
		boolean hitsFound = false;
		for (AtomicReaderContext arc: reader.leaves()) {
			Spans spans = spanQuery.getSpans(arc, arc.reader().getLiveDocs(), termContexts);
			while(spans.next()) {
				int doc = arc.docBase + spans.doc();
				System.out.println(String.format("  doc %7d, pos %4d-%4d", doc, spans.start(), spans.end()));
				hitsFound = true;
			}
		}
		if (!hitsFound)
			System.out.println("  (no hits)");
		System.out.println("");

		System.out.println("USING SLOWCOMPOSITEREADERWRAPPER:");
		@SuppressWarnings("resource")
		SlowCompositeReaderWrapper scrw = new SlowCompositeReaderWrapper(reader);
		Spans spans = spanQuery.getSpans(scrw.getContext(), scrw.getLiveDocs(), termContexts);
		hitsFound = false;
		while(spans.next()) {
			System.out.println(String.format("  doc %7d, pos %4d-%4d", spans.doc(), spans.start(), spans.end()));
			hitsFound = true;
		}
		if (!hitsFound)
			System.out.println("  (no hits)");
		System.out.println("");
	}

	private static void usage() {
		System.out
				.println("\nThis tool searches for a term and prints index information for this term.\n");
		System.out.println("  RunTermQuery <term> [fieldName]");
	}

}
