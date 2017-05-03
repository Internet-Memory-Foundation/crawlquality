package net.internetmemory;

import net.internetmemory.crawlquality.SimHashGenerator;
import net.internetmemory.crawlquality.URLInfo;
import net.internetmemory.sections.Sections;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Created by zunzun on 05/04/17.
 */
public class CrawlQualityMain {
    public static Map<String, Map<String, URLInfo>> sectionsUrlPrefix(
            String fn, Map<String, URLInfo> uis, String domain) throws IOException {
        Graph g = Sections.webGraph(fn, uis, domain, true);
        Map<Vertex, Long> indegrees = Sections.indegrees(g, "text/html");
        return Sections.verticesToUrlinfos(
                uis, Sections.graphSectionsFromHeads(
                        g, Sections.filterHeadsUrlPrefix(g, Sections.graphSectionHeads(uis, g, indegrees, domain))));
    }

    public static Map<String, Map<String, URLInfo>> sectionsBasic(
            String fn, Map<String, URLInfo> uis, String domain) throws IOException {
        Graph g = Sections.webGraph(fn, uis, domain, true);
        Map<Vertex, Long> indegrees = Sections.indegrees(g, "text/html");
        return Sections.verticesToUrlinfos(
                uis, Sections.graphSectionsFromHeads(
                        g, Sections.graphSectionHeads(uis, g, indegrees, domain)));
    }

    public static Map<String, Map<String, URLInfo>> sectionsBasicPart(
            String fn, Map<String, URLInfo> uis, String domain) throws IOException {
        Graph g = Sections.webGraph(fn, uis, domain, true);
        Map<Vertex, Long> indegrees = Sections.indegrees(g, "text/html");
        return Sections.verticesToUrlinfos(
                uis, Sections.findSectionsNodes(
                        g, Sections.filterHeadsUrlPrefix(g, Sections.graphSectionHeads(uis, g, indegrees, domain))));
    }

    public static void main(String[] args) {
        try {
            if (args[0].equals("-hash")) {
                SimHashGenerator.hashAndPrint(args[1]);
            } else if ((args[0].equals("-dists") || args[0].equals("-distances")) && args.length == 3) {
                Map<String, URLInfo> hashes1 = SimHashGenerator.loadHashes(args[1], false, false);
                Map<String, URLInfo> hashes2 = SimHashGenerator.loadHashes(args[2], false, false);
                hashes1 = SimHashGenerator.simhashFilter(hashes1);
                hashes2 = SimHashGenerator.simhashFilter(hashes2);

                Map<String, Integer> dists = SimHashGenerator.getDistancesSameKey(hashes1, hashes2);
                List<Map.Entry<String, Integer>> listSort = SimHashGenerator.sortRankAscByValue(dists);
                SimHashGenerator.printDistances(listSort);
            } else if ((args[0].equals("-redun") || args[0].equals("-redundancy")) && args.length == 2) {
                SimHashGenerator.printDistributions(SimHashGenerator.exactDuplicatesDistribution(
                        SimHashGenerator.loadHashes(args[1], false, false).values()));
            } else if ((args[0].equals("-diver") || args[0].equals("-diversity")) && args.length == 2) {
                Map<String, URLInfo> hashes =
                        SimHashGenerator.loadHashes(args[1], false, false);
                SimHashGenerator.printDiversity(SimHashGenerator.diversity(hashes.values()));
            } else if (args[0].equals("-size") && args.length == 3) {
                Map<String, URLInfo> ma = SimHashGenerator.loadHashes(args[1], false, false);
                long[] res = SimHashGenerator.uniqueCounts(ma.values());
                long unique_non_html_a = res[1];
                long unique_html_a = res[2];

                Map<String, URLInfo> mb = SimHashGenerator.loadHashes(args[2], false, false);
                res = SimHashGenerator.uniqueCounts(mb.values());
                long unique_non_html_b = res[1];
                long unique_html_b = res[2];

                Set<URLInfo> mboth = new HashSet<>(ma.values());
                mboth.addAll(mb.values());
                res = SimHashGenerator.uniqueCounts(mboth);
                long unique_non_html_both = res[1];
                long unique_html_both = res[2];

                System.out.println("size a (all MIME types): " + ma.values().size());
                System.out.println("size b (all MIME types): " + mb.values().size());
                System.out.println("size both (all MIME types): " + mboth.size());
                System.out.println("unique a html: " + unique_html_a);
                System.out.println("unique a not html: " + unique_non_html_a);
                System.out.println("unique b html: " + unique_html_b);
                System.out.println("unique b not html: " + unique_non_html_b);
                System.out.println("unique both html: " + unique_html_both);
                System.out.println("unique both not html: " + unique_non_html_both);

                System.out.println("A: " + (float) (unique_html_a + unique_non_html_a) / (unique_html_both + unique_non_html_both));
                System.out.println("B: " + (float) (unique_html_b + unique_non_html_b) / (unique_html_both + unique_non_html_both));
            } else if ((args[0].equals("-indegree")) && (args.length == 3 || args.length == 2)) {
                try {
                    Map<String, URLInfo> uis = SimHashGenerator.loadHashes(args[1], true, true);
                    Graph g = Sections.webGraph(args[1], uis, args.length >= 3 ? args[2] : null, true);
                    Map<Vertex, Long> indegrees = Sections.indegrees(g, "text/html");
                    for (Map.Entry<Vertex, Long> e : indegrees.entrySet()) {
                        System.out.println(e.getValue() + " " + e.getKey().property("url").value());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if ((args[0].equals("-graphSections") || args[0].equals("-graphSectionsPart")
                        || args[0].equals("-graphSectionsUrl") || args[0].equals("-sectionsBc"))
                       && args.length == 3) {
                Map<String, URLInfo> uis = SimHashGenerator.loadHashes(args[1], true, true);
                Map<String, Map<String, URLInfo>> sections;
                if (args[0].equals("-graphSections")) {
                    sections = sectionsBasic(args[1], uis, args[2]);
                } else if (args[0].equals("-graphSectionsPart")) {
                    sections = sectionsBasicPart(args[1], uis, args[2]);
                } else if (args[0].equals("-graphSectionsUrl")) {
                    sections = sectionsUrlPrefix(args[1], uis, args[2]);
                } else {
                    sections = Sections.jsoupSections(uis, args[2]);
                }
                for (Map.Entry<String, Map<String, URLInfo>> m : sections.entrySet()) {
                    System.out.println("section: " + m.getKey() + "\t" + "Number of nodes: " + m.getValue().size());
                    for (Map.Entry<String, URLInfo> m2 : m.getValue().entrySet()) {
                        System.out.println("- " + m2.getValue().url);
                    }
                }
            } else if ((args[0].equals("-secDiver") || args[0].equals("-sectionDiversity")
                        || args[0].equals("-secDiverBc") || args[0].equals("-sectionDiversityBreadcrumb"))
                       && args.length == 3) {
                Map<String, Map<String, URLInfo>> sections;
                Map<String, URLInfo> uis = SimHashGenerator.loadHashes(args[1], true, true);
                if (args[0].equals("-secDiver") || args[0].equals("-sectionDiversity")) {
                    sections = sectionsUrlPrefix(args[1], uis, args[2]);
                } else {
                    sections = Sections.jsoupSections(uis, args[2]);
                }

                for (Map.Entry<String, Map<String, URLInfo>> m : sections.entrySet()) {
                    System.out.println("section: " + m.getKey() + "\t" + "Number of nodes: " + m.getValue().size());
                    Set<URLInfo> sectionUis = m.getValue().entrySet().stream()
                            .map(v -> v.getValue())
                            .collect(Collectors.toSet());
                    SimHashGenerator.printDiversity(SimHashGenerator.diversity(sectionUis));
                }
            } else if ((args[0].equals("-secDists") || args[0].equals("-sectionsDistances")
                        || args[0].equals("-secDistsBc") || args[0].equals("-sectionsDistancesBc"))
                       && args.length == 4) {
                Map<String, Map<String, URLInfo>> sectionsA;
                Map<String, Map<String, URLInfo>> sectionsB;
                Map<String, URLInfo> uis1 = SimHashGenerator.loadHashes(args[1], true, true);
                Map<String, URLInfo> uis2 = SimHashGenerator.loadHashes(args[2], true, true);
                if (args[0].equals("-secDists") || args[0].equals("-sectionsDistances")) {
                    sectionsA = sectionsUrlPrefix(args[1], uis1, args[3]);
                    sectionsB = sectionsUrlPrefix(args[2], uis2, args[3]);
                } else {
                    sectionsA = Sections.jsoupSections(uis1, args[3]);
                    sectionsB = Sections.jsoupSections(uis2, args[3]);
                }
                int threshold = 8;

                for (Map.Entry<String, Map<String, URLInfo>> s : sectionsA.entrySet()) {
                    if (sectionsB.containsKey(s.getKey())) {
                        System.out.println("Section " + s.getKey());
                        Map<String, Integer> dists = SimHashGenerator.getDistancesSameKey(
                                s.getValue(), sectionsB.get(s.getKey()));
                        List<Map.Entry<String, Integer>> sortedDists = SimHashGenerator.sortRankAscByValue(dists);
                        long changed = sortedDists.stream().filter(r -> r.getValue() > threshold).count();
                        int count = sortedDists.size();
                        System.out.println("Changed pages proportion among URLs in both captures with threshold "
                                + threshold + ": " +
                                + changed + " / " + count + " = " + (float) changed / count);
                        SimHashGenerator.printDistances(sortedDists);
                        System.out.println();
                    }
                }
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
