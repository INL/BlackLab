package nl.inl.blacklab.testutil;

import java.io.File;

import org.apache.logging.log4j.Level;

import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.util.LogUtil;

/** Simple test program to monitor opening a BlackLab corpus */
public class TestOpenCorpus {

    public static void main(String[] args) throws Exception {

        LogUtil.setupBasicLoggingConfig(Level.DEBUG);

        File indexDir = new File(args[0]);
        BlackLabIndex index = BlackLab.implicitInstance().open(indexDir);
        TextPattern tp = CorpusQueryLanguageParser.parse("[word=\"waterval\"]");
        QueryExecutionContext context = index.defaultExecutionContext(index.mainAnnotatedField());
        BLSpanQuery query = tp.translate(context);

        // Occasionally perform a simple search while we monitor init log messages. Ctrl+C to terminate.
        while (true) {
            Hits hits = index.search().find(query).execute();
            System.err.println("Found " + hits.size() + "hits.");
            Thread.sleep(100000);
        }
    }
}
