package nl.inl.blacklab.testutil;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.analysis.BLDutchAnalyzer;
import nl.inl.blacklab.perdocument.DocPropertyComplexFieldLength;
import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.search.indexstructure.MetadataFieldDesc;
import nl.inl.util.LuceneUtil;

/**
 * Determine the number of tokens in the subcorpus defined by each of the metadatafield values.
 * (Only for those metadata fields that have a limited number of values, all of which were captured
 * in the index metadata file).
 */
public class TokensPerMetaValue {

	public static void main(String[] args) throws IOException, ParseException {

		String indexDir = "/home/jan/blacklab/gysseling/index";
		if (args.length >= 1)
			indexDir = args[0];
		String complexFieldName = "contents";
		if (args.length >= 2)
			complexFieldName = args[1];

		Searcher searcher = Searcher.open(new File(indexDir));
		try {
			// Loop over all metadata fields
			IndexStructure indexStructure = searcher.getIndexStructure();
			System.out.println("field\tvalue\tnumberOfDocs\tnumberOfTokens");
			for (String metaFieldName: indexStructure.getMetadataFields()) {
				// Check if this field has only a few values
				MetadataFieldDesc fd = indexStructure.getMetadataFieldDesc(metaFieldName);
				if (fd.isValueListComplete()) {
					// Loop over the values
					for (Map.Entry<String, Integer> entry: fd.getValueDistribution().entrySet()) {
						// Determine token count for this value
						String fieldName = fd.getName();
						Query filter = LuceneUtil.parseLuceneQuery("\"" + entry.getKey().toLowerCase() + "\"", new BLDutchAnalyzer(), fieldName);
						DocResults docs = searcher.queryDocuments(filter);
						int totalNumberOfTokens = docs.intSum(new DocPropertyComplexFieldLength(complexFieldName));
						System.out.println(fieldName + "\t" + entry.getKey() + "\t" + entry.getValue() + "\t" + totalNumberOfTokens);
					}
				}
			}
		} finally {
			searcher.close();
		}
	}
}
