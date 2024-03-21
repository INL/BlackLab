package nl.inl.blacklab.indexers.config.saxon;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.xml.sax.Locator;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import net.sf.saxon.om.NodeInfo;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/** Tracks characters positions for each tag in an XML document.
 * This is for an XML document in a character array.
 */
public class CharPositionsTrackerImpl implements CharPositionsTracker {

    /**
     * Estimate of characters per line, for initial sizing of list
     */
    private static final int AVERAGE_LINE_LENGTH_ESTIMATE = 40;

    /**
     * Estimate of characters per open bracket, for initial sizing of list
     */
    private static final int AVERAGE_CHARS_PER_TAG_ESTIMATE = 20;

    /**
     * For each line, starting from 0, store the STARTING (cumulative) character position.
     * Index 0 therefore always has the value 0.
     * End-of-line characters are included in the count.
     * Used to translate line/column into character position.
     */
    private IntList lineNumberToCharPos;

    /**
     * The positions of all open brackets &lt; in the document.
     * We use these to find the starting character position of a tag.
     */
    private IntList openBracketPositions;

    /**
     * Connects recorded SAX positions (just after start and end tags) to the calculated
     * characters positions of those tags.
     */
    private Map<Integer, StartEndPos> startEndPosMap;

    /**
     * Our element stack while parsing, so we can fill in the end tag character position when we encounter it.
     */
    private final Deque<StartEndPos> elStack = new ArrayDeque<>();

    /**
     * We need to be able to find the start char. position of a tag, but we only know the end position.
     * This iterator helps us find the corresponding start.
     */
    private final IntIterator tagPosIterator;

    /**
     * The current open bracket position above iterator is on.
     */
    private int currentTagPos;

    public CharPositionsTrackerImpl(char[] document) {
        int lengthInChars = document.length;

        // Find all open brackets and line endings
        openBracketPositions = new IntArrayList(lengthInChars / AVERAGE_CHARS_PER_TAG_ESTIMATE);
        lineNumberToCharPos = new IntArrayList(lengthInChars / AVERAGE_LINE_LENGTH_ESTIMATE);
        lineNumberToCharPos.add(0); // first line always starts at character 0
        for (int i = 0; i < lengthInChars; i++) {
            char c = document[i];
            if (c == '<') {
                // Keep track of open bracket positions, so we can find the start of a tag
                openBracketPositions.add(i);
            } else if (c == '\r' || c == '\n') {
                // Keep track of line ending positions (or next-line-start-positions if you prefer)
                if (c == '\r') {
                    if (i < lengthInChars - 1 && document[i + 1] == '\n')
                        i++; // windows-style line ending; also skip newline
                }
                lineNumberToCharPos.add(i + 1); // store character position after EOL char(s)
            }
        }

        // Initialize tag iterator
        tagPosIterator = openBracketIterator();
        assert tagPosIterator.hasNext();
        currentTagPos = tagPosIterator.nextInt();

        // The map where we keep track of the real start and end positions of tags (as opposed to what Saxon provides,
        // which is always the position just after the tag)
        startEndPosMap = new HashMap<>(document.length / AVERAGE_CHARS_PER_TAG_ESTIMATE);
    }

    public CharPositionsTrackerImpl(Reader document) {
        // Find all open brackets and line endings
        openBracketPositions = new IntArrayList();
        lineNumberToCharPos = new IntArrayList();
        lineNumberToCharPos.add(0); // first line always starts at character 0
        int readAheadChar = 0;
        int position = 0;
        try {
            while (true) {
                int iCurrentChar;
                if (readAheadChar != 0) {
                    // We already read the next char. Consume it now.
                    iCurrentChar = readAheadChar;
                    readAheadChar = 0;
                } else {
                    // Read the next char.
                    iCurrentChar = document.read();
                }
                if (iCurrentChar < 0)
                    break; // end of document
                char currentChar = (char)iCurrentChar;
                if (currentChar == '<') {
                    // Keep track of open bracket positions, so we can find the start of a tag
                    openBracketPositions.add(position);
                } else if (currentChar == '\r' || currentChar == '\n') {
                    // Keep track of line ending positions (or next-line-start-positions if you prefer)
                    if (currentChar == '\r') {
                        readAheadChar = document.read();
                        if (readAheadChar == '\n') {
                            position++; // windows-style line ending; also skip newline
                            readAheadChar = 0;
                        }
                    }
                    lineNumberToCharPos.add(position + 1); // store character position after EOL char(s)
                }
                position++;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Initialize tag iterator
        tagPosIterator = openBracketIterator();
        assert tagPosIterator.hasNext();
        currentTagPos = tagPosIterator.nextInt();

        // The map where we keep track of the real start and end positions of tags (as opposed to what Saxon provides,
        // which is always the position just after the tag)
        startEndPosMap = new HashMap<>(position / AVERAGE_CHARS_PER_TAG_ESTIMATE);
    }

    @Override
    public IntIterator openBracketIterator() {
        return openBracketPositions.intIterator();
    }

    @Override
    public int charPosForLineAndCol(NodeInfo nodeInfo) {
        return charPosForLineAndCol(nodeInfo.getLineNumber(), nodeInfo.getColumnNumber());
    }

    /**
     * translation of recorded line and column number to character position in the document
     */
    @Override
    public int charPosForLineAndCol(int lineNumber, int columnNumber) {
        Integer charPosStartOfLine = lineNumberToCharPos.get(lineNumber - 1);
        // Note that we subtract 1 at the end because Saxon indicates the columnNumber just AFTER the tag
        // (JN: or because columnNumber is 1-based, like line number, and we want a character position that is 0-based?)
        return charPosStartOfLine + columnNumber - 1;
    }

    @Override
    public int charPosForLineAndCol(Locator locator) {
        return charPosForLineAndCol(locator.getLineNumber(), locator.getColumnNumber());
    }

    @Override
    public void putStartEndPos(int end, StartEndPos startEndPos) {
        startEndPosMap.put(end, startEndPos);
    }

    /**
     * find where in the character[] of the source the closing tag ends
     *
     * @param nodeInfo the node to find the endtag for
     */
    int findClosingTagPosition(NodeInfo nodeInfo) {
        return startEndPosMap.get(charPosForLineAndCol(nodeInfo)).endPos;
    }

    /**
     * find the position of the starting character (&lt;) of a node in the characters of a document.
     * Note that CR and LF are included in the count. It is recomended to cache this number for use in
     * clients.
     */
    @Override
    public int getNodeStartPos(NodeInfo node) {
        return startEndPosMap.get(charPosForLineAndCol(node)).startPos;
    }

    /**
     * find the position of the end character (>) of a node in the characters of a document.
     * Note that CR and LF are included in the count. It is recomended to cache this number for use in
     * clients.
     */
    @Override
    public int getNodeEndPos(NodeInfo node) {
        return findClosingTagPosition(node);
    }

    /**
     * A new start tag was found while parsing.
     *
     * Note that this must be called linearly because we iterate through the tag positions we indexed earlier.
     *
     * @param qName tag name
     * @param locator locator for the line and col
     */
    @Override
    public void addNextStartElement(String qName, Locator locator) {
        int end = charPosForLineAndCol(locator);
        int begin = end;
        // Now find the character position of the start of this tag
        // (last < character before the end position)
        while (currentTagPos < end) {
            begin = currentTagPos;
            currentTagPos = tagPosIterator.nextInt();
        }
        // NOTE more testing needed for self closing tags
        if (begin == end) {
            throw new BlackLabRuntimeException(String.format("No '<' found for %s at line %d, col %d, charpos %d",
                    qName, locator.getLineNumber(), locator.getColumnNumber(), end));
        }
        StartEndPos startEndPos = new StartEndPos(begin);
        elStack.push(startEndPos);
        putStartEndPos(end, startEndPos);
    }

    /**
     * A new end tag was found while parsing.
     *
     * Updates the end position of the corresponding start tag.
     *
     * @param locator locator for the line and col
     */
    @Override
    public void addNextEndElement(Locator locator) {
        elStack.pop().setEndPos(charPosForLineAndCol(locator));
    }
}
