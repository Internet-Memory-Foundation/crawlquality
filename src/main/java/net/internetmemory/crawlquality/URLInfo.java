package net.internetmemory.crawlquality;

import net.internetmemory.simhash.SimhashFingerprint;

/**
 * Created by zunzun on 03/04/17.
 */
public class URLInfo {
    public String url;
    public String mimeType;
    public String md5;
    public SimhashFingerprint simhash;

    public URLInfo(String u, String mt, String m, SimhashFingerprint s) {
        url = u;
        mimeType = mt;
        md5 = m;
        simhash = s;
    }
}