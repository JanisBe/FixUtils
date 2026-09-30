package com.fixutils.dictionary;

import com.fixutils.parser.TagValuePair;

import java.util.*;

public record FixDictionaryData(
        Map<Integer, FixFieldDescriptor> fields,
        List<Integer> headerTags,
        List<Integer> trailerTags,
        Map<String, List<Integer>> messageTags,
        List<Integer> allFieldTags
) {
    private static final List<Integer> DEFAULT_HEADER = List.of(
            8, 9, 35, 49, 56, 115, 128, 90, 91, 34, 50, 142, 57, 143, 116, 144, 129, 145, 43, 97, 52, 122, 212, 213, 347, 369
    );
    private static final List<Integer> DEFAULT_TRAILER = List.of(93, 89, 10);

    public static List<TagValuePair> defaultSort(List<TagValuePair> pairs) {
        if (pairs == null || pairs.size() <= 1) {
            return pairs != null ? new ArrayList<>(pairs) : List.of();
        }
        record RankedPair(TagValuePair pair, long rank) {
        }
        List<RankedPair> rankedList = new ArrayList<>(pairs.size());
        for (TagValuePair pair : pairs) {
            int tag = pair.tag();
            long rank;
            if (tag == 8) {
                rank = 0;
            } else if (tag == 9) {
                rank = 1;
            } else if (tag == 35) {
                rank = 2;
            } else if (tag == 10) {
                rank = 100_000_000L;
            } else {
                rank = 1_000_000L + tag;
            }
            rankedList.add(new RankedPair(pair, rank));
        }
        rankedList.sort(Comparator.comparingLong(RankedPair::rank));
        List<TagValuePair> result = new ArrayList<>(pairs.size());
        for (RankedPair rp : rankedList) {
            result.add(rp.pair());
        }
        return result;
    }

    public List<TagValuePair> sort(List<TagValuePair> pairs) {
        if (pairs == null || pairs.size() <= 1) {
            return pairs != null ? new ArrayList<>(pairs) : List.of();
        }

        String msgType = null;
        for (TagValuePair p : pairs) {
            if (p.tag() == 35) {
                msgType = p.value();
                break;
            }
        }

        List<Integer> effHeader = !headerTags.isEmpty() ? headerTags : DEFAULT_HEADER;
        List<Integer> effTrailer = !trailerTags.isEmpty() ? trailerTags : DEFAULT_TRAILER;
        List<Integer> bodyOrder = (msgType != null && messageTags.containsKey(msgType))
                ? messageTags.get(msgType)
                : List.of();

        Map<Integer, Integer> tagOccurrenceMap = new HashMap<>();

        record RankedPair(TagValuePair pair, long rank) {
        }
        List<RankedPair> rankedList = new ArrayList<>(pairs.size());

        for (TagValuePair pair : pairs) {
            int tag = pair.tag();
            int occurrence = tagOccurrenceMap.getOrDefault(tag, 0);
            tagOccurrenceMap.put(tag, occurrence + 1);

            long rank;
            if (tag == 8) {
                rank = 0;
            } else if (tag == 9) {
                rank = 1;
            } else if (tag == 10) {
                rank = 100_000_000L;
            } else {
                int headerIdx = effHeader.indexOf(tag);
                if (headerIdx >= 0) {
                    rank = 100L + headerIdx;
                } else {
                    int trailerIdx = effTrailer.indexOf(tag);
                    if (trailerIdx >= 0) {
                        rank = 50_000_000L + trailerIdx;
                    } else {
                        int bodyIdx = bodyOrder.indexOf(tag);
                        if (bodyIdx >= 0) {
                            rank = 1_000_000L + occurrence * 100_000L + bodyIdx;
                        } else {
                            int allIdx = allFieldTags.indexOf(tag);
                            if (allIdx >= 0) {
                                rank = 10_000_000L + occurrence * 100_000L + allIdx;
                            } else {
                                rank = 20_000_000L + occurrence * 100_000L + tag;
                            }
                        }
                    }
                }
            }
            rankedList.add(new RankedPair(pair, rank));
        }

        rankedList.sort(Comparator.comparingLong(RankedPair::rank));

        List<TagValuePair> result = new ArrayList<>(pairs.size());
        for (RankedPair rp : rankedList) {
            result.add(rp.pair());
        }
        return result;
    }
}
