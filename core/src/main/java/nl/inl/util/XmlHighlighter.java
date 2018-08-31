/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * Performs highlighting of the contents of XML elements that we found hits in.
 *
 * NOTE: this class is not threadsafe. Use a separate instance per thread.
 */
public class XmlHighlighter {
    /**
     * How to deal with non-well-formed snippets: by e.g. adding an open tag at the
     * beginning for an unmatched closing tag, or by removing the unmatched closing
     * tag.
     */
    public enum UnbalancedTagsStrategy {
    ADD_TAG,
    REMOVE_TAG
    }

    enum TagType {
        EXISTING_TAG, // an existing tag
        HIGHLIGHT_START, // insert <hl> tag here
        HIGHLIGHT_END, // insert </hl> tag here
        FIX_START, // insert start tag here to fix well-formedness
        FIX_END, // insert end tag here to fix well-formedness
        REMOVE_EXISTING_TAG // remove an unbalanced tag to fix well-formedness
    }

    /**
     * Helper class for highlighting: stores a span in the original content, be it a
     * place to insert a highlight tag, or an existing tag in the original XML.
     */
    static class TagLocation implements Comparable<TagLocation> {
        /** Counter for assigning unique id to objectNum */
        private static long n = 0;
        
        static synchronized long getNextUniqueId() {
            return n++;
        }

        /**
         * Whether this is an existing tag from the original content, a start highlight
         * tag to be added, or an end highlight tag to be added.
         */
        TagType type;

        /** Start position of tag in original content */
        int start;

        /**
         * End position of tag in original content. NOTE: this only differs from start
         * if type == EXISTING_TAG. Highlight tags are not in the original content, so
         * there start always equals end.
         */
        int end;

        /**
         * Start position of matching tag (the close to this open tag, or vice versa) in
         * original content. A negative value indicates that this tag was unmatched
         * (which might happen if we're highlighting snippets of a document).
         */
        int matchingTagStart;

        /**
         * Unique id for each tag; used as a tie-breaker so sorting is always the same,
         * and end tags always follow their start tags
         */
        public long objectNum;

        /**
         * For FIX_START/END tags, indicate the tag name to use when insert. For other
         * types, not used.
         */
        String name;

        public TagLocation(TagType type, int start, int end) {
            this.type = type;
            this.start = start;
            this.end = end;
            matchingTagStart = -1; // unmatched tag (until we find its match)
            objectNum = getNextUniqueId();
        }

        @Override
        public int compareTo(TagLocation o) {
            if (this == o)
                return 0;
            int a = start, b = o.start;
            if (a == b) {
                a = end;
                b = o.end;
                if (a == b) {
                    // use the objectNum as a tie breaker so sort is always the same,
                    // and end tags always follow their start tags
                    return (int) (objectNum - o.objectNum);
                }
            }
            return a - b;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + end;
            result = prime * result + matchingTagStart;
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            result = prime * result + (int) (objectNum ^ (objectNum >>> 32));
            result = prime * result + start;
            result = prime * result + ((type == null) ? 0 : type.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            TagLocation other = (TagLocation) obj;
            if (end != other.end)
                return false;
            if (matchingTagStart != other.matchingTagStart)
                return false;
            if (name == null) {
                if (other.name != null)
                    return false;
            } else if (!name.equals(other.name))
                return false;
            if (objectNum != other.objectNum)
                return false;
            if (start != other.start)
                return false;
            return type == other.type;
        }

        @Override
        public String toString() {
            return type + "@" + start;
        }

    }

    /**
     * The XML tag to add to the content to signal where highlighting should start.
     */
    private static final String startHighlightTag = "<hl>";

    /**
     * The XML tag to add to the content to signal where highlighting should end.
     */
    private static final String endHighlightTag = "</hl>";

    /** How deep are we inside highlighting tags? */
    private int inHighlightTag;

    /**
     * Where the highlighted content is built - therefore, this class is not
     * threadsafe!
     */
    StringBuilder b;

    /** Remove empty <hl></hl> tags after highlighting? */
    private boolean removeEmptyHlTags = true;

    /**
     * How to fix well-formedness problems? If true, we remove the unbalanced tags;
     * if false (the default) we add extra tags at the start or end to rebalance it.
     */
    private UnbalancedTagsStrategy unbalancedTagsStrategy = UnbalancedTagsStrategy.ADD_TAG;

    /**
     * The outer (usually, only) highlight tag we're inside of, or null if we're not
     * highlighting.
     */
    private TagLocation outerHighlightTag = null;

    /**
     * Given XML content and a sorted list of existing tags and highlight tags to be
     * added, add the tags to the content so the well-formedness of the XML is not
     * affected.
     *
     * Also offers the option of cutting the content to a number of characters (with
     * possibly a small overshoot, because it will try to cut at a word boundary),
     * ignoring tags and maintaining well-formedness.
     *
     * @param xmlContent the XML content to highlight
     * @param tags the existing tags and highlight tags to add. This list must be
     *            sorted!
     * @param stopAfterChars after how many characters of text content to cut this
     *            fragment. -1 = no cutting.
     * @return the highlighted XML content.
     */
    private String highlightInternal(String xmlContent, List<TagLocation> tags, int stopAfterChars) {
        if (stopAfterChars < 0)
            stopAfterChars = xmlContent.length();
        int positionInContent = 0;
        b = new StringBuilder();
        inHighlightTag = 0;
        int visibleCharsAdded = 0;
        boolean addVisibleChars = true; // keep adding text content until we reach the preferred length
        boolean wasCut = false;
        for (TagLocation tag : tags) {
            if (tag.start < positionInContent) {
                System.out.println("ERROR IN HIGHLIGHTING");
                // NOTE: before, this used to happen very occasionally. Probably fixed now,
                // but just in case it's not, let's avoid a nasty exception.
                continue; // skip tag
            }
            if (addVisibleChars) {
                String visibleChars = xmlContent.substring(positionInContent, tag.start);
                if (visibleCharsAdded + visibleChars.length() >= stopAfterChars) {
                    visibleChars = StringUtils.abbreviate(visibleChars, "", stopAfterChars - visibleCharsAdded);
                    if (visibleChars.length() < tag.start - positionInContent)
                        wasCut = true;
                    addVisibleChars = false;
                }
                b.append(visibleChars);
                visibleCharsAdded += visibleChars.length();
            } else {
                if (positionInContent < tag.start) {
                    wasCut = true;
                }
            }
            processTag(xmlContent, tag);
            positionInContent = tag.end;
        }
        b.append(xmlContent.substring(positionInContent));
        final String optionalEllipsis = wasCut ? "..." : "";
        return b.toString().trim() + optionalEllipsis;
    }

    /**
     * Decide what to do based on the tag type.
     *
     * @param xmlContent the content we're highlighting
     * @param tag the existing tag or highlight tag to add
     */
    private void processTag(String xmlContent, TagLocation tag) {
        switch (tag.type) {
        case HIGHLIGHT_START:
            startHighlight(tag);
            break;
        case EXISTING_TAG:
            existingTag(tag, xmlContent.substring(tag.start, tag.end));
            break;
        case HIGHLIGHT_END:
            endHighlight();
            break;
        case FIX_START:
            existingTag(tag, "<" + tag.name + ">");
            break;
        case FIX_END:
            existingTag(tag, "</" + tag.name + ">");
            break;
        case REMOVE_EXISTING_TAG:
            // Simply don't add the tag
            break;
        }
    }

    /**
     * Add highlight tag if not already added; increment depth
     * 
     * @param tag where the tag occurs
     */
    private void startHighlight(TagLocation tag) {
        if (inHighlightTag == 0) {
            b.append(startHighlightTag);
            outerHighlightTag = tag;
        }
        inHighlightTag++;
    }

    /** Decrement depth; End highlight if we're at level 0 */
    private void endHighlight() {
        inHighlightTag--;
        if (inHighlightTag == 0) {
            b.append(endHighlightTag);
            outerHighlightTag = null;
        }
    }

    /**
     * We encountered a tag in the content. If we're inside a highlight tag, ends
     * the current highlight, add the existing tag and restart the highlighting.
     * 
     * @param tag where the tag occurs
     * @param str the existing tag encountered.
     */
    private void existingTag(TagLocation tag, String str) {
        boolean suspendHighlighting = false;

        if (inHighlightTag > 0) {
            // We should possibly suspend highlighting for this tag to maintain well-formedness.
            // Check the current (outer) highlighting span and see if our matching tag is inside or outside this highlighting span.
            if (outerHighlightTag.start > tag.matchingTagStart
                    || outerHighlightTag.matchingTagStart <= tag.matchingTagStart) {
                // Matching tag is outside the highlighting span; highlighting must be suspended to maintain well-formedness.
                suspendHighlighting = true;
            }
        }

        if (suspendHighlighting)
            b.append(endHighlightTag);
        b.append(str);
        if (suspendHighlighting)
            b.append(startHighlightTag);
    }

    /**
     * The start and end character position of a hit, used for highlighting the
     * content.
     */
    public static class HitCharSpan {
        private int startChar, endChar;

        public int getStartChar() {
            return startChar;
        }

        public int getEndChar() {
            return endChar;
        }

        public HitCharSpan(int startChar, int endChar) {
            this.startChar = startChar;
            this.endChar = endChar;
        }
    }

    private static void addHitPositionsToTagList(List<TagLocation> tags, List<HitCharSpan> hitSpans, int offset,
            int length) {
        for (HitCharSpan hit : hitSpans) {
            final int a = hit.getStartChar() - offset;
            if (a < 0)
                continue; // outside highlighting range, or non-highlighting element (e.g. searching for example date range)
            final int b = hit.getEndChar() - offset;
            if (b > length)
                continue; // outside highlighting range
            TagLocation start = new TagLocation(TagType.HIGHLIGHT_START, a, a);
            start.matchingTagStart = b;
            tags.add(start);
            TagLocation end = new TagLocation(TagType.HIGHLIGHT_END, b, b);
            end.matchingTagStart = a;
            tags.add(end);
        }
    }

    /**
     * Given XML content, make a list of tag locations in this content.
     *
     * Note that the XML content is assumed to be (part of) a well-formed XML
     * document. This way we can highlight a whole document or part of a document.
     * It's therefore okay if we encounter close tags at the start that we haven't
     * seen an open tag for, or open tags at the end that we'll never see a close
     * tag for, but if there are other tag errors (e.g. hierarchy errors such as
     * &lt;i&gt;&lt;b&gt;&lt;/i&gt;&lt;/b&gt;) the behaviour of the highlighter is
     * undefined.
     *
     * @param elementContent the XML content
     * @return the list of tag locations, each with type EXISTING_TAG.
     */
    private List<TagLocation> makeTagList(String elementContent) {
        List<TagLocation> tags = new ArrayList<>();

        // Regex for finding all XML tags.
        // Group 1 indicates if this is an open or close tag
        // Group 2 is the tag name
        Pattern xmlTags = Pattern.compile("<(?![!\\?])\\s*(/?)\\s*([^>\\s]+)(\\s+[^>]*)?>");

        Matcher m = xmlTags.matcher(elementContent);
        List<TagLocation> openTagStack = new ArrayList<>(); // keep track of open tags
        int fixStartTagObjectNum = -1; // when adding start tags to fix well-formedness, number backwards (for correct sorting)
        while (m.find()) {
            TagLocation tagLocation = new TagLocation(TagType.EXISTING_TAG, m.start(), m.end());

            // Keep track of open tags, so we know if the tags are matched
            boolean isOpenTag = m.group(1).length() == 0;
            boolean isSelfClosing = isOpenTag && isSelfClosing(m.group());
            if (isOpenTag) {
                if (!isSelfClosing) {
                    // Open tag. Add to the stack.
                    openTagStack.add(tagLocation);
                    tagLocation.name = m.group(2); // remember in case there's no close tag
                } else {
                    // Self-closing tag. Don't add to stack, link to self
                    tagLocation.matchingTagStart = tagLocation.start;
                }
            } else {
                // Close tag. Did we encounter a matching open tag?
                TagLocation openTag = null;
                if (!openTagStack.isEmpty()) {
                    // Yes, this tag is matched. Find matching tag and link them.
                    openTag = openTagStack.remove(openTagStack.size() - 1);
                    openTag.name = null; // no longer necessary to remember tag name
                } else {
                    // Unmatched closing tag.
                    if (unbalancedTagsStrategy == UnbalancedTagsStrategy.REMOVE_TAG) {
                        // Remove it.
                        tagLocation.type = TagType.REMOVE_EXISTING_TAG;
                    } else {
                        // Insert a dummy open tag at the start
                        // of the content to maintain well-formedness
                        openTag = new TagLocation(TagType.FIX_START, 0, 0);
                        openTag.name = m.group(2); // we need to know what tag to insert
                        openTag.objectNum = fixStartTagObjectNum; // to fix sorting
                        fixStartTagObjectNum--;
                        tags.add(openTag);
                    }
                }
                if (openTag != null) {
                    // Link the matching tags together
                    openTag.matchingTagStart = tagLocation.start;
                    tagLocation.matchingTagStart = openTag.start;
                }
            }

            // Add tag to the tag list
            tags.add(tagLocation);
        }
        // Close any tags still open, in the correct order (for well-formedness)
        for (int i = openTagStack.size() - 1; i >= 0; i--) {
            if (unbalancedTagsStrategy == UnbalancedTagsStrategy.REMOVE_TAG) {
                // Remove the unbalanced tag
                openTagStack.get(i).type = TagType.REMOVE_EXISTING_TAG;
            } else {
                // Add a close tag at the end to fix the unbalanced tag
                TagLocation tagLocation = new TagLocation(TagType.FIX_END, elementContent.length(),
                        elementContent.length());
                tagLocation.name = openTagStack.get(i).name; // we remembered this for this case
                tags.add(tagLocation);
            }
        }
        return tags;
    }

    /**
     * Determines if a tag is a self-closing tag (ends with "/&gt;")
     * 
     * @param tag the tag
     * @return true iff it is self-closing
     */
    private static boolean isSelfClosing(String tag) {
        // Start at the second to last character (skip the '>') and look for slash.
        for (int i = tag.length() - 2; i >= 0; i--) {
            switch (tag.charAt(i)) {
            case '/':
                // Yes, self-closing tag
                return true;
            case ' ':
            case '\t':
            case '\n':
            case '\r':
                // Whitespace; continue
                break;
            default:
                // We found an attribute or the tag name before encountering a slash, so it's not self-closing.
                return false;
            }
        }
        return false;
    }

    /**
     * Highlight a string containing XML tags. The result is still well-formed XML.
     *
     * @param elementContent the string to highlight
     * @param hits where the highlighting tags should go
     * @return the highlighted string
     */
    public String highlight(String elementContent, List<HitCharSpan> hits) {
        return highlight(elementContent, hits, 0);
    }

    /**
     * Highlight part of an XML document.
     *
     * You cut the XML yourself and supply the part you wish to highlight, along
     * with the offset of where you cut (so we know where the highlight tags should
     * go).
     *
     * Missing tags at the beginning or end of the part will be corrected. As long
     * as you cut at tag boundaries (i.e. not within a tag), the result of this
     * method will still be well-formed XML.
     *
     * @param partialContent the (partial) XML to cut and highlight.
     * @param hits the hits to use for highlighting, or null for no highlighting
     * @param offset position of the first character in the string (i.e. what to
     *            subtract from Hit positions to highlight)
     * @return the highlighted (part of the) XML string
     */
    public String highlight(String partialContent, List<HitCharSpan> hits, int offset) {

        // Find all tags in the content and put their positions in a list
        List<TagLocation> tags = makeTagList(partialContent);

        // 2. Put the positions of our hits in the same list and sort it
        if (hits != null)
            addHitPositionsToTagList(tags, hits, offset, partialContent.length());
        tags.sort(Comparator.naturalOrder());

        // Add all the highlight tags in the list into the content,
        // taking care to mainting well-formedness around existing tags
        String highlighted = highlightInternal(partialContent, tags, -1);

        if (removeEmptyHlTags) {
            // Because of the way the highlighting (and maintaining of well-formedness) occurs,
            // empty highlight tags may have arisen. Remove these.
            highlighted = highlighted.replaceAll(startHighlightTag + "(\\s*)" + endHighlightTag,
                    "$1");
        }

        return highlighted;
    }

    /**
     * Cut a string after a specified number of non-tag characters, preferably at a
     * word boundary, keeping all tags after the cut intact. The result is still
     * well-formed XML.
     *
     * You might use this to show the first few lines of an XML document on the
     * results page.
     *
     * @param elementContent the string to cut
     * @param stopAfterChars after how many non-tag characters we should stop (-1
     *            for no limit)
     * @return the cut string
     */
    public String cutAroundTags(String elementContent, int stopAfterChars) {
        // Find all tags in the content and put their positions in a list
        List<TagLocation> tags = makeTagList(elementContent);
        tags.sort(Comparator.naturalOrder());

        // Add all the highlight tags in the list into the content,
        // taking care to mainting well-formedness around existing tags
        return highlightInternal(elementContent, tags, stopAfterChars);
    }

    public static void main(String[] args) {
        XmlHighlighter h = new XmlHighlighter();
        String xml = "<zin><lidwoord>The</lidwoord> <adjectief>quick</adjectief> " +
                "<adjectief>brown</adjectief> <substantief>fox</substantief></zin>";
        List<HitCharSpan> hitSpans = new ArrayList<>();
        hitSpans.add(new HitCharSpan(41, 46));
        hitSpans.add(new HitCharSpan(101, 124));
        String result = h.highlight(xml, hitSpans, 0);
        System.out.println(result);
    }

    /**
     * Set whether or not to remove empty <hl></hl> tags at the end of highlighting
     * (which can form due to the process).
     *
     * @param c true iff empty hl tags should be removed
     */
    public void setRemoveEmptyHlTags(boolean c) {
        removeEmptyHlTags = c;
    }

    /**
     * Make a cut XML fragment well-formed.
     *
     * The only requirement is that tags are intact (i.e. xmlFragment doesn't start
     * with "able cellpadding='3'&gt;" or end with "&lt;/bod".
     *
     * The fragment is made well-formed by adding open tags to the beginning or
     * close tags to the end. It is therefore not a generic way of making any
     * non-well-formed document well-formed, it just works for cutting out part of a
     * well-formed document.
     *
     * @param xmlFragment
     * @return a well-formed fragment
     */
    public String makeWellFormed(String xmlFragment) {
        return highlight(xmlFragment, null, 0);
    }

    /**
     * Get how well-formedness problems are fixed
     * 
     * @return the strategy we're using now.
     */
    public UnbalancedTagsStrategy getUnbalancedTagsStrategy() {
        return unbalancedTagsStrategy;
    }

    /**
     * Set how to fix well-formedness problems.
     * 
     * @param strategy what to do when encountering unbalanced tags.
     */
    public void setUnbalancedTagsStrategy(UnbalancedTagsStrategy strategy) {
        this.unbalancedTagsStrategy = strategy;
    }

}
