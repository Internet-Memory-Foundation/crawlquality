package net.internetmemory.simhash;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Shingles {
    /**
     * Builds a set of shingles for a given list.
     *
     * Essentially it goes with a fixed size window through a given list and it builds a set
     * of all possible n-grams.
     */
    public static <E> Set<List<E>> build(List<E> din, int windowSize) {
        Set<List<E>> result = new HashSet<>();
        for (int from = 0; from < din.size() - windowSize; from++) {
            int to = from + windowSize;
            result.add(din.subList(from, to));
        }
        return result;
    }
}
