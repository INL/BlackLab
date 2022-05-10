package org.ivdnt.blacklab.aggregator.logic;

import java.util.Arrays;
import java.util.Comparator;

import org.ivdnt.blacklab.aggregator.representation.Hit;

import nl.inl.blacklab.util.PropertySerializeUtil;

class HitComparators {

    public static Comparator<Hit> deserialize(String serialized) {
        if (PropertySerializeUtil.isMultiple(serialized)) {
            boolean reverse = false;
            if (serialized.startsWith("-(") && serialized.endsWith(")")) {
                reverse = true;
                serialized = serialized.substring(2, serialized.length() - 1);
            }
            Comparator<Hit> result = Arrays.asList(PropertySerializeUtil.splitMultiple(serialized))
                    .stream().map(ser -> deserialize(ser))
                    .reduce((a, b) -> a.thenComparing(b))
                    .orElseThrow();
            if (reverse)
                result = result.reversed();
            return result;
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
        case "decade": cmp = docFieldDecade(info); break;
        case "field": cmp = docField(info); break;
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
                cmp = hitTextAfter(annotation, sensitive, false, false);
                break;
            case "wordright":
                cmp = hitTextAfter(annotation, sensitive, false, true);
                break;
            default:
                throw new UnsupportedOperationException("Hit property not supported: " + type);
            }
        }
        return reverse ? cmp.reversed() : cmp;
    }

    private static Comparator<Hit> DOC_PID = (a, b) -> a.docPid.compareTo(b.docPid);

    private static Comparator<Hit> HIT_POSITION = (a, b) -> Long.compare(a.start, b.start);

    private static Comparator<Hit> hitMatchedText(String annotation, boolean sensitive) {
        return (a, b) -> {
            return a.match.compareTo(b.match, annotation, sensitive, false, false);
        };
    }

    private static Comparator<Hit> hitTextBefore(String annotation, boolean sensitive, boolean reverse, boolean stopAfterOne) {
        return (a, b) -> {
            return a.left.compareTo(b.left, annotation, sensitive, reverse, stopAfterOne);
        };
    }

    private static Comparator<Hit> hitTextAfter(String annotation, boolean sensitive, boolean reverse, boolean stopAfterOne) {
        return (a, b) -> {
            return a.right.compareTo(b.right, annotation, sensitive, reverse, stopAfterOne);
        };
    }

    /** String compare by metadata field */
    private static Comparator<Hit> docField(String field) {
        return new Comparator<Hit>() {
            @Override
            public int compare(Hit a, Hit b) {
                String sa = a.docInfo.get(field).getValue().get(0);
                String sb = b.docInfo.get(field).getValue().get(0);
                return sa.compareTo(sb);
            }
        };
    }

    /** Compare by decade for metadata field containing year */
    private static Comparator<Hit> docFieldDecade(String field) {
        return new Comparator<Hit>() {
            @Override
            public int compare(Hit a, Hit b) {
                int da = Integer.parseInt(a.docInfo.get(field).getValue().get(0)) / 10;
                int db = Integer.parseInt(b.docInfo.get(field).getValue().get(0)) / 10;
                return Integer.compare(da, db);
            }
        };
    }
}
