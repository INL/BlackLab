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
package nl.inl.blacklab.highlight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nl.inl.util.StringUtil;

/**
 * Performs highlighting of the contents of XML elements that we found hits in.
 *
 * NOTE: this class is not threadsafe. Use a separate instance per thread.
 */
public class XmlHighlighter {
	static enum TagType {
		EXISTING_TAG, HIGHLIGHT_START, HIGHLIGHT_END,
	}

	/**
	 * Helper class for highlighting: stores a span in the original content, be it a place to insert
	 * a highlight tag, or an existing tag in the original XML.
	 */
	static class TagLocation implements Comparable<TagLocation> {
		/** Counter for assigning unique id to objectNum */
		public static long n = 0;

		/**
		 * Whether this is an existing tag from the original content, a start highlight tag to be
		 * added, or an end highlight tag to be added.
		 */
		TagType type;

		/** Start position of tag in original content */
		int start;

		/**
		 * End position of tag in original content. NOTE: this only differs from start if type ==
		 * EXISTING_TAG. Highlight tags are not in the original content, so there start always
		 * equals end.
		 */
		int end;

		/**
		 * Unique id for each tag; used as a tie-breaker so sorting is always the same, and end tags
		 * always follow their start tags
		 */
		public long objectNum;

		public TagLocation(TagType type, int start, int end) {
			this.type = type;
			this.start = start;
			this.end = end;
			objectNum = n;
			n++;
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

	/**
	 * When cutting a fragment, this is the number of characters of overshoot allowed when trying to
	 * cut at a word boundary.
	 */
	private static final int OVERSHOOT_ALLOWED = 10;

	/**
	 * How deep are we inside highlighting tags?
	 */
	private int inHighlightTag;

	/**
	 * Where the highlighted content is built - therefore, this class is not threadsafe!
	 */
	StringBuilder b;

	/**
	 * Remove empty <hl></hl> tags after highlighting?
	 */
	private boolean removeEmptyHlTags = true;

	/**
	 * Given XML content and a sorted list of existing tags and highlight tags to be added, add the
	 * tags to the content so the well-formedness of the XML is not affected.
	 *
	 * Also offers the option of cutting the content to a number of characters (with possibly a
	 * small overshoot, because it will try to cut at a word boundary), ignoring tags and
	 * maintaining well-formedness.
	 *
	 * @param xmlContent
	 *            the XML content to highlight
	 * @param tags
	 *            the existing tags and highlight tags to add. This list must be sorted!
	 * @param preferredLength
	 *            after how many characters of text content to cut this fragment. Just set to
	 *            elementContent.size() if you don't want to do any cutting.
	 * @return the highlighted XML content.
	 */
	private String highlightInternal(String xmlContent, List<TagLocation> tags, int preferredLength) {
		int positionInContent = 0;
		b = new StringBuilder();
		inHighlightTag = 0;
		int visibleCharsAdded = 0;
		boolean addVisibleChars = true;
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
				if (visibleCharsAdded + visibleChars.length() >= preferredLength) {
					visibleChars = StringUtil.abbreviate(visibleChars, preferredLength
							- visibleCharsAdded, OVERSHOOT_ALLOWED, false);
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
	 * @param xmlContent
	 *            the content we're highlighting
	 * @param tag
	 *            the existing tag or highlight tag to add
	 */
	private void processTag(String xmlContent, TagLocation tag) {
		switch (tag.type) {
		case HIGHLIGHT_START:
			startHighlight();
			break;
		case EXISTING_TAG:
			existingTag(xmlContent.substring(tag.start, tag.end));
			break;
		case HIGHLIGHT_END:
			endHighlight();
			break;
		}
	}

	/** Add highlight tag if not already added; increment depth */
	private void startHighlight() {
		if (inHighlightTag == 0)
			b.append(startHighlightTag);
		inHighlightTag++;
	}

	/** Decrement depth; End highlight if we're at level 0 */
	private void endHighlight() {
		inHighlightTag--;
		if (inHighlightTag == 0)
			b.append(endHighlightTag);
	}

	/**
	 * We encountered a tag in the content. If we're inside a highlight tag, ends the current
	 * highlight, add the existing tag and restart the highlighting.
	 *
	 * @param str
	 *            the existing tag encountered.
	 */
	private void existingTag(String str) {
		if (inHighlightTag > 0)
			b.append(endHighlightTag);
		b.append(str);
		if (inHighlightTag > 0)
			b.append(startHighlightTag);
	}

	/**
	 * The start and end character position of a hit, used for highlighting the content.
	 */
	public static class HitSpan {
		private int startChar, endChar;

		public int getStartChar() {
			return startChar;
		}

		public int getEndChar() {
			return endChar;
		}

		public HitSpan(int startChar, int endChar) {
			this.startChar = startChar;
			this.endChar = endChar;
		}
	}

	private static void addHitPositionsToTagList(List<TagLocation> tags, List<HitSpan> hitSpans) {
		for (HitSpan hit : hitSpans) {
			final int a = hit.getStartChar();
			if (a < 0) // non-highlighting element, for example: searching for example date range
				continue;
			tags.add(new TagLocation(TagType.HIGHLIGHT_START, a, a));
			final int b = hit.getEndChar();
			tags.add(new TagLocation(TagType.HIGHLIGHT_END, b, b));
		}
	}

	/**
	 * Given XML content, make a list of tag locations in this content.
	 *
	 * @param elementContent
	 *            the XML content
	 * @return the list of tag locations, each with type EXISTING_TAG.
	 */
	private static List<TagLocation> makeTagList(String elementContent) {
		List<TagLocation> tags = new ArrayList<TagLocation>();
		Pattern xmlTags = Pattern.compile("<[^>]+>");
		Matcher m = xmlTags.matcher(elementContent);
		while (m.find()) {
			tags.add(new TagLocation(TagType.EXISTING_TAG, m.start(), m.end()));
		}
		return tags;
	}

	/**
	 * Highlight a string containing XML tags. The result is still well-formed XML.
	 *
	 * @param elementContent
	 *            the string to highlight
	 * @param hits
	 *            where the highlighting tags should go
	 * @return the highlighted string
	 */
	public String highlight(String elementContent, List<HitSpan> hits) {
		String highlighted = highlightAndCut(elementContent, hits, elementContent.length());

		if (removeEmptyHlTags) {
			// Because of the way the highlighting (and maintaining of well-formedness) occurs,
			// empty highlight tags may have arisen. Remove these.
			highlighted = highlighted.replaceAll(startHighlightTag + "(\\s*)" + endHighlightTag,
					"$1");
		}

		return highlighted;
	}

	private String highlightAndCut(String elementContent, List<HitSpan> hits, int preferredLength) {
		// Find all tags in the content and put their positions in a list
		List<TagLocation> tags = makeTagList(elementContent);

		// 2. Put the positions of our hits in the same list and sort it
		addHitPositionsToTagList(tags, hits);
		Collections.sort(tags);

		// Add all the highlight tags in the list into the content,
		// taking care to mainting well-formedness around existing tags
		return highlightInternal(elementContent, tags, preferredLength);
	}

	/**
	 * Cut a string after a specified number of non-tag characters, preferably at a word boundary,
	 * keeping all tags after the cut intact. The result is still well-formed XML.
	 *
	 * @param elementContent
	 *            the string to cut
	 * @param preferredLength
	 *            the preferred length of the string
	 * @return the cut string
	 */
	public String cutAroundTags(String elementContent, int preferredLength) {
		// "Abuse" the highlighting function to safely cut the content down to the preferred length
		return highlightAndCut(elementContent, new ArrayList<HitSpan>(), preferredLength);
	}

	public static void main(String[] args) {
		XmlHighlighter h = new XmlHighlighter();
		System.out
				.println(h
						.cutAroundTags(
								"<lidwoord>The</lidwoord> <adjectief>quick</adjectief> <adjectief>brown</adjectief> <substantief>fox</substantief>",
								9));
	}

	public void setRemoveEmptyHlTags(boolean c) {
		removeEmptyHlTags = c;
	}
}
