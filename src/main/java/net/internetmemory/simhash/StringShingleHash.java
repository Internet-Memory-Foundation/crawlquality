package net.internetmemory.simhash;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class StringShingleHash {

    /**
     * Hashes a n-gram made of Strings.
     *
     * @param shingle
     * @return
     */
    public static long calculate(List<String> shingle) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        for (String s : shingle) {
            md.update(Ints.toByteArray(s.hashCode()));
        }
        return Longs.fromByteArray(md.digest());
    }
}
