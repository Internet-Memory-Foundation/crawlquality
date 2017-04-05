package net.internetmemory.crawlquality;

import net.internetmemory.mapred.reader.WarcRecord;
import net.internetmemory.simhash.SimhashFingerprint;
import net.internetmemory.util.scraping.HtmlUtils;
import net.internetmemory.utils.WarcReaderWrapper;
import org.jwat.warc.WarcReader;
import org.w3c.dom.Document;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

public class SimHashMain {
    /**
     * generate Document by content saved in byte[] and url String.
     */
    private static Document document(byte[] data, String url) throws Exception {
        String encoding = HtmlUtils.detectEncoding(data);
        return HtmlUtils.Jsoup.cleanAndParseStable(data, url, encoding, true);
    }

    /**
     * generate the md5 code for all resources and the simhash code for all HTML resources.
     */
    public static void hashAndPrint(String fileName) throws IOException {
        WarcReader reader = WarcReaderWrapper.getReaderFromFile(fileName);
        org.jwat.warc.WarcRecord rec = null;
        int nr_resources = 0;
        int nr_html_resources = 0;
        long sz_resources = 0;
        long sz_html_resources = 0;

        while ((rec = reader.getNextRecord()) != null) {
            try {
                WarcRecord warcRecord = WarcReaderWrapper.parseWarcRecord(rec);
                String url = warcRecord.getTargetURI();
                InputStream contentInputStream = warcRecord.getContent();

                long sz = warcRecord.getContentLength();
                sz_resources += sz;
                nr_resources++;

                byte[] content = null;
                byte[] verify = new byte[4];

                //note that there are several types of records, what we are interested in response records only
                if (WarcReaderWrapper.isARes(warcRecord)
                        && (url.startsWith("http://") || url.startsWith("https://"))) {
                    boolean found = false;
                    contentInputStream.read(verify, 0, 4);
                    for (int i = 0; i <= warcRecord.getContentLength() - "\r\n\r\n".length(); i++) {
                        if (verify[0] == 13 && verify[1] == 10 && verify[2] == 13 && verify[3] == 10) {
                            found = true;
                            content = new byte[(int) warcRecord.getContentLength() - i - 4];
                            contentInputStream.read(content);
                            break;
                        } else {
                            verify[0] = verify[1];
                            verify[1] = verify[2];
                            verify[2] = verify[3];
                            verify[3] = (byte) contentInputStream.read();
                        }
                    }
                    if (!found) {
                        System.err.println("HTTP payload not found " + url);
                        continue;
                    }

                    String mime = "N/A";
                    String hashCode = getMD5Hash(content);
                    mime = WarcReaderWrapper.mimeDetection.detectMimeType(content);
                    if (mime != null && mime.startsWith("text/html")) {
                        nr_html_resources++;
                        sz_html_resources += sz;
                        String date = warcRecord.getWarcDate();
                        SimhashFingerprint simHashCode = SimhashFingerprint.calculate(document(content, url));
                        System.out.println(
                                url
                                        + "\t"
                                        + date
                                        + "\t"
                                        + mime
                                        + "\t"
                                        + "md5:" + hashCode
                                        + "\t"
                                        + "simhash_v1_3:" + simHashCode
                        );
                    } else {
                        String date = warcRecord.getWarcDate();
                        System.out.println(
                                url
                                        + "\t"
                                        + date
                                        + "\t"
                                        + mime
                                        + "\t"
                                        + "md5:" + hashCode
                        );
                    }
                }
            } catch (Exception e) {
                System.err.println("Unexpected Exception: " + e);
                e.printStackTrace();
            }
        }

        System.err.println("number of resources:" + "\t" + nr_resources);
        System.err.println("number of HTML resources:" + "\t" + nr_html_resources);
        System.err.println("all resources size:" + "\t" + sz_resources);
        System.err.println("HTML resources size:" + "\t" + sz_html_resources);
    }

    /**
     * calculate the distances for the intersection urls in two captures.
     */
    public static Map<String, Integer> getDistancesSameKey(
            Map<String, SimhashFingerprint> m1, Map<String, SimhashFingerprint> m2) {
        Map<String, Integer> res = new HashMap<>();
        for (Map.Entry<String, SimhashFingerprint> record : m1.entrySet()) {
            if (m2.containsKey(record.getKey())) {
                SimhashFingerprint h1 = record.getValue();
                SimhashFingerprint h2 = m2.get(record.getKey());
                res.put(record.getKey(), h1.distance(h2));
            }
        }
        return res;
    }

    public static void printDistances(List<Map.Entry<String, Integer>> list) {
        for (Map.Entry<String, Integer> record : list) {
            System.out.println(record.getKey() + "\t" + record.getValue());
        }
    }

    public static void printRedundancy(List<Map.Entry<Integer, Integer>> list) {
        for (Map.Entry<Integer, Integer> record : list) {
            System.out.println(record.getKey() + " identical copies:" + "\t" + record.getValue() + " resources");
        }
    }

    /**
     * This function sort a Map object to a LinkedHashMap by value ascending order.
     */
    public static <T> List<Map.Entry<String, T>> sortRankAscByValue(Map<String, T> rank) {
        // map to list
        List<Map.Entry<String, T>> listSort = new ArrayList<>(rank.entrySet());
        listSort.sort(Comparator.comparingInt(x -> (Integer) x.getValue()));
        return listSort;
    }

    /**
     * This function sorts a Map object to a LinkedHashMap by key ascending order.
     */
    public static <T> List<Map.Entry<Integer, T>> sortRankAscByKey(Map<Integer, T> rank) {
        // map to list
        List<Map.Entry<Integer, T>> listSort = new ArrayList<>(rank.entrySet());
        Collections.sort(listSort, Comparator.comparingInt(Map.Entry::getKey));
        return listSort;
    }

    /**
     * load hashcode files and save the md5 and simhash code if it exists.
     */
    public static Map<String, URLInfo> loadHashes(String fn) throws IOException {
        Map<String, URLInfo> res = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(fn))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] words = line.split("\t");
                String m = words[3].substring("md5:".length());
                URLInfo i;
                if (words.length == 4) {
                    i = new URLInfo(words[0], words[2], m, null);
                } else {
                    SimhashFingerprint s = new SimhashFingerprint(
                            SimhashFingerprint.Algorithm.PLAIN_TEXT_SHINGLE3_V1,
                            new long[]{Long.parseLong(words[4].substring("simhash_v1_3:".length()))});
                    i = new URLInfo(words[0], words[2], m, s);
                }
                res.put(words[0], i);
            }
        }
        return res;
    }

    public static Map<String, SimhashFingerprint> simhashMapOfMap(Map<String, URLInfo> m) {
        return m.entrySet().stream().
                filter(e -> e.getValue().simhash != null).
                map(e -> new AbstractMap.SimpleImmutableEntry<>(e.getKey(), e.getValue().simhash)).
                collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
    }

    /**
     * Returns the content's MD5 as a String.
     */
    public static String getMD5Hash(byte[] content) throws NoSuchAlgorithmException {
        byte[] md5 = MessageDigest.getInstance("MD5").digest(content);
        return new BigInteger(1, md5).toString(16);
    }

    /**
     * Increments the count of hashCode in counts.
     */
    public static void incrCounts(String hashCode, Map<String, Integer> distribution) {
        if (distribution.containsKey(hashCode)) {
            distribution.put(hashCode, distribution.get(hashCode) + 1);
        } else {
            distribution.put(hashCode, 1);
        }
    }

    /**
     * Returns the distribution by count.
     */
    public static Map<Integer, Integer> countDistribution(Map<String, Integer> distribution) {
        Map<Integer, Integer> resultDistribution = new HashMap<>();
        for (Map.Entry<String, Integer> rec : distribution.entrySet()) {
            if (resultDistribution.containsKey(rec.getValue())) {
                resultDistribution.put(rec.getValue(), resultDistribution.get(rec.getValue()) + 1);
            } else {
                resultDistribution.put(rec.getValue(), 1);
            }
        }
        return resultDistribution;
    }

    public static Map<String, Map<Integer, Integer>> exactDuplicatesDistribution(Collection<URLInfo> uis) {
        Map<String, Integer> distributionAll = new HashMap<>();
        Map<String, Integer> distributionHTML = new HashMap<>();
        Map<String, Integer> distributionNotHTML = new HashMap<>();
        Map<String, Integer> distributionImg = new HashMap<>();

        for (URLInfo ui : uis) {
            String h = ui.md5;
            incrCounts(h, distributionAll);
            if (ui.mimeType.startsWith("text/html")) {
                incrCounts(h, distributionHTML);
            } else {
                incrCounts(h, distributionNotHTML);
            }
            if (ui.mimeType.startsWith("image/")) {
                incrCounts(h, distributionImg);
            }
        }

        Map<String, Map<Integer, Integer>> res = new HashMap<>();
        res.put("all", countDistribution(distributionAll));
        res.put("html", countDistribution(distributionHTML));
        res.put("not_html", countDistribution(distributionNotHTML));
        res.put("images", countDistribution(distributionImg));
        return res;
    }

    public static void printDistributions(Map<String, Map<Integer, Integer>> distributions) {
        for (Map.Entry<String, Map<Integer, Integer>> m : distributions.entrySet()) {
            System.out.println(m.getKey());
            printRedundancy(sortRankAscByKey(m.getValue()));
            System.out.println();
        }
    }

    public static void removeCloseHashes(Set<SimhashFingerprint> hashes, SimhashFingerprint h, int dist) {
        Set<SimhashFingerprint> toRemove = new HashSet<>();
        for (SimhashFingerprint e : hashes) {
            if (e.distance(h) < dist) {
                toRemove.add(e);
            }
        }
        hashes.removeAll(toRemove);
    }

    public static int uniqueSimhash(Set<SimhashFingerprint> hashes) {
        int nr_unique = 0;

        Set<SimhashFingerprint> toProcess = new HashSet<>(hashes);
        while (!toProcess.isEmpty()) {
            nr_unique++;
            SimhashFingerprint h = toProcess.iterator().next();
            removeCloseHashes(toProcess, h, 4);
        }
        return nr_unique;
    }

    public static long[] uniqueCounts(Collection<URLInfo> uis) {
        Map<String, Map<Integer, Integer>> dists = exactDuplicatesDistribution(uis);
        long nr_unique_html = dists.get("html").values().stream().mapToInt(Integer::intValue).sum();
        long nr_unique_not_html = dists.get("not_html").values().stream().mapToInt(Integer::intValue).sum();
        return new long[]{nr_unique_html, nr_unique_not_html};
    }

    public static void main(String[] args) {
        try {
            if (args[0].equals("-hash")) {
                hashAndPrint(args[1]);
            } else if (args[0].equals("-dists") || args[0].equals("-distances")) {
                Map<String, SimhashFingerprint> hashes1 = simhashMapOfMap(loadHashes(args[1]));
                Map<String, SimhashFingerprint> hashes2 = simhashMapOfMap(loadHashes(args[2]));
                Map<String, Integer> dists = getDistancesSameKey(hashes1, hashes2);
                List<Map.Entry<String, Integer>> listSort = sortRankAscByValue(dists);
                printDistances(listSort);
            } else if (args[0].equals("-redun") || args[0].equals("-redundancy")) {
                printDistributions(exactDuplicatesDistribution(loadHashes(args[1]).values()));
            } else if (args[0].equals("-diver") || args[0].equals("-diversity")) {
                Set<SimhashFingerprint> simhashes =
                        loadHashes(args[1]).entrySet().stream().
                                filter(e -> e.getValue().simhash != null).
                                map(e -> e.getValue().simhash).
                                collect(Collectors.toCollection(HashSet::new));
                long nr_unique_html = uniqueSimhash(simhashes);
                Map<String, Map<Integer, Integer>> dists = exactDuplicatesDistribution(loadHashes(args[1]).values());
                long nr_unique_not_html = dists.get("not_html").values().stream().mapToInt(Integer::intValue).sum();
                long nr_total_html = dists.get("html").entrySet().stream().map(kv -> kv.getKey() * kv.getValue()).
                        mapToInt(Integer::intValue).sum();
                long nr_total_not_html = dists.get("not_html").entrySet().stream().map(kv -> kv.getKey() * kv.getValue()).
                        mapToInt(Integer::intValue).sum();
                long nr_total = dists.get("all").entrySet().stream().map(kv -> kv.getKey() * kv.getValue()).
                        mapToInt(Integer::intValue).sum();
                float uniqueness = (float) (nr_unique_html + nr_unique_not_html) / nr_total;
                System.out.println("HTML resources unique / total: " +
                        nr_unique_html + " / " + nr_total_html + " = " + (float) nr_unique_html / nr_total_html);
                System.out.println("Non HTML resources unique / total: " +
                        nr_unique_not_html + " / " + nr_total_not_html + " = " + (float) nr_unique_not_html / nr_total_not_html);
                System.out.println("Global uniqueness: (" +
                        nr_unique_html + " + " + nr_unique_not_html + ") / " + nr_total + " = " + uniqueness);
            } else if (args[0].equals("-size")) {
                Map<String, URLInfo> ma = loadHashes(args[1]);
                long[] res = uniqueCounts(ma.values());
                long unique_html_a = res[0];
                long unique_non_html_a = res[1];

                Map<String, URLInfo> mb = loadHashes(args[2]);
                res = uniqueCounts(mb.values());
                long unique_html_b = res[0];
                long unique_non_html_b = res[1];

                Set<URLInfo> mboth = new HashSet<>(ma.values());
                mboth.addAll(mb.values());
                res = uniqueCounts(mboth);
                long unique_html_both = res[0];
                long unique_non_html_both = res[1];

                System.out.println("A: " + (float)(unique_html_a + unique_non_html_a) / (unique_html_both + unique_non_html_both));
                System.out.println("B: " + (float)(unique_html_b + unique_non_html_b) / (unique_html_both + unique_non_html_both));
            } else {
                System.err.println("Wrong parameters.");
                System.exit(2);
            }
            System.exit(0);
        } catch (IOException e) {
            System.err.println("Exiting, caught " + e);
            System.exit(1);
        } catch (OutOfMemoryError err) {
            System.err.println("OutOfMemoryError:");
            err.printStackTrace();
            System.exit(3);
        }
    }
}
