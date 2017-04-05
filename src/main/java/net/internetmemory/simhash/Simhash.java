package net.internetmemory.simhash;

import java.util.Set;

public class Simhash {
    /**
     * Calculates a simhash value (long) for a given input set of hashes, also longs.
     * Long hashes should be produced by some other means.
     */
    public static long calculate(Set<Long> hashes) {
        long[] v = new long[64];
        for (int i = 0; i < v.length; i++) {
            v[i] = 0;
        }
        for (Long hash : hashes) {
            long current = hash;
            for (int bit = 0; bit < 64; bit++) {
                if ((current & 1) == 0) {
                    v[bit] += 1;
                } else {
                    v[bit] -= 1;
                }
                current >>>= 1;
            }
        }
        long hashValue = 0;
        for (int bit = 0; bit < 64; bit++) {
            if (v[bit] > 0) {
                hashValue |= 1;
            }
            if (bit != 63) {
                hashValue <<= 1;
            }
        }
        return hashValue;
    }
}
