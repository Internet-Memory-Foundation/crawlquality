package net.internetmemory.crawlquality;

import net.internetmemory.sections.ReadableContentExtractor;
import net.internetmemory.sections.Sections;
import net.internetmemory.utils.WarcRecord;
import net.internetmemory.utils.Html;
import net.internetmemory.simhash.SimhashFingerprint;
import net.internetmemory.utils.HtmlUtils;
import net.internetmemory.utils.WarcReaderWrapper;
import org.apache.commons.lang.ArrayUtils;
import org.jwat.warc.WarcReader;

import java.io.*;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

public class SimHashGenerator {
    /**
     * generate Document by content saved in byte[] and url String.
     */
    public static org.w3c.dom.Document document(byte[] data, String url) throws Exception {
        String encoding = HtmlUtils.detectEncoding(data);
        return HtmlUtils.Jsoup.cleanAndParseStable(data, url, encoding, true);
    }

    /**
     * generate the md5 code for all resources and the simhash code for all HTML resources.
     */
    public static void hashAndPrint(String fileName) throws IOException {
        WarcReader reader = WarcReaderWrapper.getReaderFromFile(fileName);
        org.jwat.warc.WarcRecord rec;
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

                List<Byte> contentHeader = new ArrayList<>();
                byte[] content = null;
                byte[] verify = new byte[4];

                if (warcRecord.getWarcRecordType().equals("metadata")
                        && (url.startsWith("http://") || url.startsWith("https://"))) {
                    new BufferedReader(new InputStreamReader(contentInputStream)).lines() // lines
                            .map(l -> l.split(":", 2)) // key-value pairs
                            .filter(kv -> kv.length == 2 && kv[0].trim().equals("outlink")) // outlinks
                            .forEach(kv -> System.out.println(url + " -> " + kv[1].trim()));
                } else if (WarcReaderWrapper.isARes(warcRecord)
                        && (url.startsWith("http://") || url.startsWith("https://"))) {
                    boolean found = false;
                    contentInputStream.read(verify, 0, 4);

                    for (Byte b : verify) {
                        contentHeader.add(b);
                    }

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
                            contentHeader.add(verify[3]);
                        }
                    }
                    if (!found) {
                        System.err.println("HTTP payload not found " + url);
                        continue;
                    }

                    Byte[] bytes = contentHeader.toArray(new Byte[contentHeader.size()]);
                    String contentHeaderUTF8 = new String(ArrayUtils.toPrimitive(bytes), StandardCharsets.UTF_8);
                    String status = null;
                    String location = null;
                    if (contentHeaderUTF8.split("\r\n")[0].startsWith("HTTP")) {
                        status = contentHeaderUTF8.split("\r\n")[0].split(" ")[1];
                        if (status.startsWith("3")) {
                            location = Arrays.stream(contentHeaderUTF8.split("\r\n")) //lines
                                    .map(v -> v.split(":", 2)) // split in 2
                                    .filter(v -> v.length == 2 && v[0].startsWith("Location")) //find location
                                    .map(v -> v[1].trim()).collect(Collectors.toList())
                                    .iterator().next();
                            location = Html.absoluteUrl(url, location);
                        }
                    } else {
                        System.err.println("error HTTP Header");
                    }

                    String mime = "N/A";
                    String hashCode = getMD5Hash(content);
                    mime = WarcReaderWrapper.mimeDetection.detectMimeType(content);
                    String date = warcRecord.getWarcDate();
                    System.out.print(
                            url
                                    + "\t"
                                    + date
                                    + "\t"
                                    + "tika_mime_t:" + mime
                                    + "\t"
                                    + "md5:" + hashCode
                                    + "\t"
                                    + "status:" + status
                                    + "\t"
                                    + "location:" + location);
                    if (mime != null && mime.startsWith("text/html")) {
                        nr_html_resources++;
                        sz_html_resources += sz;

                        // calculate 2 simhash codes.
                        SimhashFingerprint simHashCodePage =
                                    SimhashFingerprint.calculate(document(content, url));
                        SimhashFingerprint simHashCodeMainText = null;
                        try {
                            simHashCodeMainText =
                                    SimhashFingerprint.calculate(ReadableContentExtractor.text(content, url));
                        } catch (Exception e) {
                            System.err.println(e);
                            e.printStackTrace();
                        }
                        String optSectionHeadUrl = Sections.extractSectionHeadUrl(content, url);
                        System.out.print(
                                "\t"
                                        + "simhash_v1_3:" + simHashCodePage
                                        + "\t"
                                        + "simhash_v1_3_snacktory:" + simHashCodeMainText
                                        + "\t"
                                        + "section:" + optSectionHeadUrl
                        );
                    }
                    System.out.println();
                }
            } catch (Exception e) {
                System.err.println("Unexpected Exception: " + e);
                e.printStackTrace();
                System.out.println();
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
            Map<String, URLInfo> m1, Map<String, URLInfo> m2) {
        Map<String, Integer> res = new HashMap<>();
        for (Map.Entry<String, URLInfo> record : m1.entrySet()) {
            if (m2.containsKey(record.getKey())) {
                URLInfo ui1 = record.getValue();
                URLInfo ui2 = m2.get(record.getKey());
                res.put(record.getKey(), ui1.distance(ui2));
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

    public static <T> List<Map.Entry<Integer, T>> sortRankAscByKey(Map<Integer, T> rank) {
        // map to list
        List<Map.Entry<Integer, T>> listSort = new ArrayList<>(rank.entrySet());
        Collections.sort(listSort, Comparator.comparingInt(Map.Entry::getKey));
        return listSort;
    }

    /**
     * load hashcode files and save the md5 and simhash code if it exists.
     */
    public static Map<String, URLInfo> loadHashes(
            String fn, boolean skipErrorStatus, boolean onlyHtml) throws IOException {
        Map<String, URLInfo> res = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(fn))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] words = line.split("\t");
                if (words.length >= 2 && !words[1].equals("->")) {
                    Map<String, String> kvs =
                            Arrays.stream(Arrays.copyOfRange(words, 2, words.length))
                                    .collect(Collectors.toMap(
                                            kv -> kv.split(":", 2)[0],
                                            kv -> kv.split(":", 2)[1]));
                    int st = Integer.valueOf(kvs.get("status"));
                    String path;
                    try {
                        path = new URI(words[0]).getPath();
                    } catch (URISyntaxException e) {
                        System.err.println("Could not parse URL " + words[0] + " " + e);
                        path = "";
                    }
                    if ((skipErrorStatus && st >= 400)
                            || (onlyHtml
                                && (! kvs.get("tika_mime_t").startsWith("text/html")
                                    || path.toLowerCase().endsWith(".js")
                                    || path.toLowerCase().endsWith(".css")))) {
                        continue;
                    }
                    String m = kvs.get("md5");
                    String redir = kvs.get("location");
                    String section =  !kvs.containsKey("section") || kvs.get("section").equals("null")
                            ? null :  kvs.get("section");
                    URLInfo i;
                    SimhashFingerprint sh = optSimhash("simhash_v1_3", kvs);
                    SimhashFingerprint shb = optSimhash("simhash_v1_3_snacktory", kvs);
                    i = new URLInfo(words[0], st, kvs.get("tika_mime_t"), redir, m, sh, shb, section);
                    res.put(words[0], i);
                }
            }
        }
        return res;
    }

    public static SimhashFingerprint optSimhash(String optHash, Map<String, String> kvs) {
        if (optHash == null || ! kvs.containsKey(optHash) || kvs.get(optHash).equals("null")) {
            return null;
        }
        return new SimhashFingerprint(
            SimhashFingerprint.Algorithm.PLAIN_TEXT_SHINGLE3_V1,
            new long[]{Long.parseLong(kvs.get(optHash))});
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
    public static void incrCounts(String hash, Map<String, Integer> distribution) {
        if (distribution.containsKey(hash)) {
            distribution.put(hash, distribution.get(hash) + 1);
        } else {
            distribution.put(hash, 1);
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

    public static int countAll(Map<Integer, Integer> dists) {
        return dists.entrySet().stream().map(kv -> kv.getKey() * kv.getValue()).mapToInt(Integer::intValue).sum();
    }

    public static int countUnique(Map<Integer, Integer> dists) {
        return dists.values().stream().mapToInt(Integer::intValue).sum();
    }

    public static void printDistributions(Map<String, Map<Integer, Integer>> distributions) {
        for (Map.Entry<String, Map<Integer, Integer>> m : distributions.entrySet()) {
            System.out.println(m.getKey());
            printRedundancy(sortRankAscByKey(m.getValue()));
            System.out.println();
        }
    }

    public static Long[] diversity(Collection<URLInfo> uis) {
        long nr_unique_html = nrUniqueSimhashes(uis.stream().filter(u -> u.simhash != null).collect(Collectors.toSet()));
        Map<String, Map<Integer, Integer>> dists = exactDuplicatesDistribution(uis);

        long nrUniqueNotHtml = countUnique(dists.get("not_html"));
        long nrTotalHtml = countAll(dists.get("html"));
        long nrTotalNotHtml = countAll(dists.get("not_html"));
        long nrTotal = countAll(dists.get("all"));
        return new Long[]{nr_unique_html, nrUniqueNotHtml, nrTotalHtml, nrTotalNotHtml, nrTotal};
    }

    public static void printDiversity(Long[] diversity) {
        long nrUniqueHtml = diversity[0];
        long nrUniqueNotHtml = diversity[1];
        long nrTotalHtml = diversity[2];
        long nrTotalNotHtml = diversity[3];
        long nrTotal = diversity[4];

        if (nrTotal != 0) {
            float uniqueness = (float) (nrUniqueHtml + nrUniqueNotHtml) / nrTotal;
            System.out.println("Global uniqueness: (" +
                    nrUniqueHtml + " + " + nrUniqueNotHtml + ") / " + nrTotal + " = " + uniqueness);
        } else {
            System.out.println("Did not find any resources.");
        }
        if (nrTotalHtml != 0) {
            System.out.println("HTML resources, unique / total: " +
                    nrUniqueHtml + " / " + nrTotalHtml + " = " + (float) nrUniqueHtml / nrTotalHtml);
        } else {
            System.out.println("Did not find any HTML resources.");
        }
        if (nrTotalNotHtml != 0) {
            System.out.println("Non HTML resources, unique / total: " +
                    nrUniqueNotHtml + " / " + nrTotalNotHtml + " = " + (float) nrUniqueNotHtml / nrTotalNotHtml);
        } else {
            System.out.println("Did not find any not HTML resources.");
        }
        System.out.println();
    }

    public static void removeCloseHashes(Set<URLInfo> uis, URLInfo ui, int dist) {
        Set<URLInfo> toRemove = new HashSet<>();
        for (URLInfo e : uis) {
            if (e.distance(ui) < dist) {
                toRemove.add(e);
            }
        }
        uis.removeAll(toRemove);
        toRemove.remove(ui);
        for (URLInfo e : toRemove) {
            removeCloseHashes(uis, e, dist);
        }
    }

    public static int nrUniqueSimhashes(Collection<URLInfo> uis) {
        int nr_unique = 0;

        Set<URLInfo> toProcess = new HashSet<>(uis);
        while (! toProcess.isEmpty()) {
            nr_unique++;
            URLInfo ui = toProcess.iterator().next();
            removeCloseHashes(toProcess, ui, 4);
        }
        return nr_unique;
    }

    public static long[] uniqueCounts(Collection<URLInfo> uis) {
        Map<String, Map<Integer, Integer>> dists = exactDuplicatesDistribution(uis);
        long nr_exact_unique_html = dists.get("html").values().stream().mapToInt(Integer::intValue).sum();
        long nr_unique_not_html = dists.get("not_html").values().stream().mapToInt(Integer::intValue).sum();
        long nr_unique_html = nrUniqueSimhashes(uis.stream().filter(u -> u.simhash != null).collect(Collectors.toSet()));
        return new long[]{nr_exact_unique_html, nr_unique_not_html, nr_unique_html};
    }

    public static Map<String, URLInfo> simhashFilter(Map<String, URLInfo> uis) {
        return uis.values().stream()
                .filter(v -> v.simhash != null
                        || (v.simhashBoilerplate != null
                            && v.simhashBoilerplate.simhash()[0] != 0))
                .collect(Collectors.toMap(
                        k -> k.url,
                        v -> v
                ));
    }
}
