package nl.inl.blacklab.indexers.config.saxon;

import org.xml.sax.Locator;

import it.unimi.dsi.fastutil.ints.IntIterator;
import net.sf.saxon.om.NodeInfo;

/** Tracks characters positions for each tag in an XML document. */
public interface CharPositionsTracker {
    IntIterator openBracketIterator();

    int charPosForLineAndCol(NodeInfo nodeInfo);

    int charPosForLineAndCol(int lineNumber, int columnNumber);

    int charPosForLineAndCol(Locator locator);

    void putStartEndPos(int end, CharPositionsTrackerImpl.StartEndPos startEndPos);

    int getNodeStartPos(NodeInfo node);

    int getNodeEndPos(NodeInfo node);

    void addNextStartElement(String qName, Locator locator);

    void addNextEndElement(Locator locator);

    class StartEndPos {
        final int startPos;
        int endPos;

        public StartEndPos(int startPos) {
            this.startPos = startPos;
        }

        public void setEndPos(int endPos) {
            this.endPos = endPos;
        }
    }
}
