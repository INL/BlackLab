package nl.inl.blacklab.indexers.config.saxon;

import org.xml.sax.Locator;

import it.unimi.dsi.fastutil.longs.LongIterator;
import net.sf.saxon.om.NodeInfo;

/** Tracks characters positions for each tag in an XML document. */
public interface CharPositionsTracker {
    LongIterator openBracketIterator();

    long charPosForLineAndCol(NodeInfo nodeInfo);

    long charPosForLineAndCol(int lineNumber, int columnNumber);

    long charPosForLineAndCol(Locator locator);

    void putStartEndPos(long end, StartEndPos startEndPos);

    long getNodeStartPos(NodeInfo node);

    long getNodeEndPos(NodeInfo node);

    void addNextStartElement(String qName, Locator locator);

    void addNextEndElement(Locator locator);

    class StartEndPos {
        final long startPos;
        long endPos;

        public StartEndPos(long startPos) {
            this.startPos = startPos;
        }

        public void setEndPos(long endPos) {
            this.endPos = endPos;
        }
    }
}
