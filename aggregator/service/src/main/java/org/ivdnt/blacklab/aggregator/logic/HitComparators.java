package org.ivdnt.blacklab.aggregator.logic;

import java.util.Comparator;
import java.util.Map;

import org.ivdnt.blacklab.aggregator.representation.DocInfo;
import org.ivdnt.blacklab.aggregator.representation.Hit;

import nl.inl.blacklab.util.PropertySerializeUtil;

public class HitComparators {

    public static Comparator<Hit> DOC_PID = Comparator.comparing(hit -> hit.docPid);

    public static Comparator<Hit> HIT_POSITION = Comparator.comparingLong(hit -> hit.start);

    public static Comparator<Hit> hitMatchedText(String annotation, boolean sensitive) {
        return (a, b) -> {
            return a.match.compareTo(b.match, annotation, sensitive, false, false);
        };
    }

    public static Comparator<Hit> hitTextBefore(String annotation, boolean sensitive, boolean reverse, boolean stopAfterOne) {
        return (a, b) -> {
            return a.left.compareTo(b.left, annotation, sensitive, reverse, stopAfterOne);
        };
    }

    public static Comparator<Hit> hitTextAfter(String annotation, boolean sensitive, boolean reverse, boolean stopAfterOne) {
        return (a, b) -> {
            return a.right.compareTo(b.right, annotation, sensitive, reverse, stopAfterOne);
        };
    }

    /** String compare by metadata field */
    public static Comparator<Hit> docField(String field, Map<String, DocInfo> docInfos) {
        return (a, b) -> {
                    String sa = docInfos.get(a.docPid).get(field).getValue().get(0);
                    String sb = docInfos.get(b.docPid).get(field).getValue().get(0);
                    return sa.compareTo(sb);
                };
    }

    /** Compare by decade for metadata field containing year */
    public static Comparator<Hit> docFieldDecade(String field, Map<String, DocInfo> docInfos) {
        return (a, b) -> {
                int da = Integer.parseInt(docInfos.get(a.docPid).get(field).getValue().get(0)) / 10;
                int db = Integer.parseInt(docInfos.get(b.docPid).get(field).getValue().get(0)) / 10;
                return Integer.compare(da, db);
            };
    }

    public static Comparator<Hit> deserialize(String serialized, Map<String, DocInfo> docInfos) {
        if (PropertySerializeUtil.isMultiple(serialized)) {
            throw new UnsupportedOperationException("Multiple sort/group not supported");
        }

        String[] parts = PropertySerializeUtil.splitPartFirstRest(serialized);
        String type = parts[0].toLowerCase();
        boolean reverse = false;
        if (type.length() > 0 && type.charAt(0) == '-') {
            reverse = true;
            type = type.substring(1);
        }
        String info = parts.length > 1 ? parts[1] : "";
        Comparator<Hit> cmp;
        switch(type) {
        case "hitposition": cmp = HIT_POSITION; break;
        case "decade": cmp = docFieldDecade(info, docInfos); break;
        case "field": cmp = docField(info, docInfos); break;
        case "doc": case "docid": cmp = DOC_PID; break;
        default:
            // Context property. Find annotation and sensitivity.
            parts = PropertySerializeUtil.splitParts(info);
            String annotation = parts[0];
            if (annotation.length() == 0)
                throw new UnsupportedOperationException("Specify annotation for sort/group prop!");
            boolean sensitive = parts.length > 1 ? parts[1].equals("s") : true;
            switch (type) {
            case "left":
                cmp = hitTextBefore(annotation, sensitive, true, false);
                break;
            case "wordleft":
                cmp = hitTextBefore(annotation, sensitive, true, true);
                break;
            case "hit":
                cmp = hitMatchedText(annotation, sensitive);
                break;
            case "right":
                cmp = hitTextBefore(annotation, sensitive, false, false);
                break;
            case "wordright":
                cmp = hitTextBefore(annotation, sensitive, false, true);
                break;
            default:
                throw new UnsupportedOperationException("Hit property not supported: " + type);
            }
        }
        return reverse ? cmp.reversed() : cmp;
    }

    private HitComparators() {}

}
