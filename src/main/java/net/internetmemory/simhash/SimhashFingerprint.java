package net.internetmemory.simhash;

import com.google.common.base.Preconditions;
import net.internetmemory.utils.NodeVisitorUtils;
import net.internetmemory.utils.TextExtractionVisitor;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * This class represents a simhash fingerprint. Provides static factory methods and methods
 * for measuring distance.
 *
 * API is designed so it can be used with fingerprints of arbitrary size (i.e. simhash is an
 * array of longs).
 *
 */
public class SimhashFingerprint {
    public static class Error extends RuntimeException {}

    public enum Algorithm {
        PLAIN_TEXT_SHINGLE3_V1 {
            @Override
            long[] calculateHash(Document document) {
                Node node = document.getDocumentElement();
                TextExtractionVisitor visitor = new TextExtractionVisitor();
                boolean result = NodeVisitorUtils.traverseNodes(visitor, node);
                if (!result) {
                    throw new Error();
                }
                List<String> tokens = tokenize(visitor.getTitle());
                tokens.addAll(tokenize(visitor.getBody()));

                return new long[] { new TextSimhash(3).calculate(tokens) };
            }
        };

        /**
         * Calculates an actual fingerprint
         *
         * @param document source document
         * @return fingeprint
         */
        abstract long[] calculateHash(Document document);
    }

    private final String algorithm;
    private final long[] simhash;

    public SimhashFingerprint(Algorithm algorithm, long[] simhash) {
        this.algorithm = algorithm.name();
        this.simhash = simhash;
    }

    /**
     * @return an actual simhash fingerprint
     */
    public long[] simhash() { return simhash; }

    /**
     * @return fingerprint size (bits)
     */
    public int size() { return 64; }

    /**
     * @return an algorithm that's been used to calculate the fingerprint
     */
    public String algorithm() { return algorithm; }

    /**
     * Measures distance between fingerprints. Two fingerprints must be comparable, i.e.
     * have the same algorithm and size, otherwise an exception is thrown.
     *
     * @param other another simhash fingerprint
     * @return a distance - number of different bits in the fingerprints
     */
    public int distance(SimhashFingerprint other) {
        Preconditions.checkArgument(other.algorithm == algorithm,
                "Fingerprints have different algorithms and therefore " +
                        "distance can not be measured.");
        Preconditions.checkArgument(simhash.length == other.simhash.length,
                "Fingerprints have different size.");

        Preconditions.checkState(simhash.length == 1,
                "Fingerprints with lengths other than 64 bits are not supported for now");

        return Long.bitCount(simhash[0] ^ other.simhash[0]);
    }

    /**
     * Calculates fingerprint from a given document using a given algorithm
     *
     * @param document a document to build a fingerprint from
     * @param algorithm an algorithm to use
     * @return a calculated fingerprint
     */
    public static SimhashFingerprint calculate(Document document, Algorithm algorithm) {
        return new SimhashFingerprint(algorithm, algorithm.calculateHash(document));
    }

    /**
     * Calculates fingerprint from a given document using a given algorithm
     *
     * @param document a document to build a fingerprint from
     * @return a calculated fingerprint
     */
    public static SimhashFingerprint calculate(Document document) {
        return calculate(document, Algorithm.PLAIN_TEXT_SHINGLE3_V1);
    }

    private static List<String> tokenize(String str) {
        Tokenizer tokenizer = new ICUTokenizer(new StringReader(str));
        CharTermAttribute charTermAttribute1 = tokenizer.addAttribute(CharTermAttribute.class);

        List<String> result = new ArrayList<>();

        try {
            tokenizer.reset();

            while (tokenizer.incrementToken()) {
                String term = charTermAttribute1.toString().toLowerCase();
                result.add(term);
            }

        } catch (IOException ignored) {
        }

        return result;
    }

    @Override
    public String toString() {
        return Long.toString(simhash[0]);
    }
}
