package net.internetmemory.simhash;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A class to simplify computing a simhash over a text document already parsed into a list of tokens.
 */
public class TextSimhash {
    private final int windowSize;

    /**
     *
     * @param windowSize window size to build shingles
     */
    public TextSimhash(int windowSize) {
        this.windowSize = windowSize;
    }

    /**
     * Calulates simhash value
     *
     * @param tokens an input text document. You must tokenize it yourself and convert to
     *             lowercase if the case is not important for you.
     * @return
     */
    public long calculate(List<String> tokens) {
        Set<List<String>> shingles = Shingles.build(tokens, windowSize);
        Set<Long> hashes = new HashSet<>();
        for (List<String> shingle : shingles) {
            hashes.add(StringShingleHash.calculate(shingle));
        }
        return Simhash.calculate(hashes);
    }
}
