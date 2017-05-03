package net.internetmemory.sections;

import net.internetmemory.crawlquality.SimHashGenerator;
import net.internetmemory.crawlquality.URLInfo;
import net.internetmemory.utils.Html;
import net.internetmemory.utils.HtmlUtils;

import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.apache.tinkerpop.gremlin.util.iterator.IteratorUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by zunzun on 04/04/17.
 */
public class Sections {
    public static long vertices = 0;
    public static long arcs = 0;

    public static String stripOptWww(String domain) {
        return domain.startsWith("www") ? domain.split("\\.", 2)[1] : domain;
    }

    public static Vertex maybeAddVertex(String url, Graph g, Map<String, Vertex> vmap, Map<String, URLInfo> uis) {
        if (vmap.containsKey(url))
            return vmap.get(url);
        vertices++;
        if (vertices % 1000 == 0) System.err.println(vertices + " vertices");
        Vertex v = g.addVertex("url", url, "section", "");
        vmap.put(url, v);
        if (uis.containsKey(url)) {
            URLInfo ui = uis.get(url);
            v.property("MIME type", ui.mimeType);
            v.property("redir location", ui.redirLocation);
            v.property("status", ui.status);
        }
        return v;
    }

    public static boolean urlInDomain(String url, String domain) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return false;
        }
        try {
            URI uri = new URI(url);
            String dom = uri.getHost();
            if (dom != null) {
                return dom.endsWith("." + domain) || dom.equals(domain);
            } else {
                return false;
            }
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public static Graph webGraph(String fn, Map<String, URLInfo> uis, String optDomain, boolean onlyKnownVertices)
            throws IOException {
        Graph g = TinkerGraph.open();
        Map<String, Vertex> vmap = new HashMap<>();
        optDomain = optDomain == null ? null : stripOptWww(optDomain);
        try (BufferedReader br = new BufferedReader(new FileReader(fn))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] words = line.split(" ");
                if (words.length > 2 && words[1].equals("->")) {
                    if ((optDomain == null || urlInDomain(words[0], optDomain) && urlInDomain(words[2], optDomain)) &&
                            (!onlyKnownVertices || uis.containsKey(words[0]) && uis.containsKey(words[2]))) {
                        Vertex v1 = maybeAddVertex(words[0], g, vmap, uis);
                        Vertex v2 = maybeAddVertex(words[2], g, vmap, uis);
                        String type = words.length > 3 ? words[3] : "-";
                        v1.addEdge("outlink", v2, "type", type);
                        arcs++;
                        if (arcs % 1000 == 0) System.err.println(arcs + " arcs");
                    }
                }
            }
        }
        System.err.println("Loaded all " + vertices + " vertices");
        System.err.println("Loaded all " + arcs + " edges");
        return g;
    }

    // returns the indegree of nodes with a non-error status code (< 400), only taking into account non-inferred links
    public static Map<Vertex, Long> indegrees(Graph g, String optMimeType) {
        Map<Vertex, Long> res = new HashMap<>();
        Iterator<Vertex> iteratorVertex = g.vertices();
        while (iteratorVertex.hasNext()) {
            Vertex vertex = iteratorVertex.next();
            if ((!vertex.keys().contains("status") ||
                    (int) vertex.property("status").value() >= 400) ||
                    (optMimeType != null &&
                            !((String) vertex.property("MIME type").value()).startsWith(optMimeType))) {
                continue;
            }
            Traversal inEgdes = g.traversal().V(vertex).inE("outlink")
                    .filter(x -> !x.get().property("type").value().equals("I"));
            res.put(vertex, IteratorUtils.count(inEgdes));
        }
        return res;
    }

    // indegree distribution per MIME type
    public static Map<String, Map<Long, Integer>> indegreeDistribution(
            Map<Vertex, Long> inDegreeMap, Map<String, URLInfo> uis) {
        Map<Long, Integer> distributionAll = new HashMap<>();
        Map<Long, Integer> distributionHTML = new HashMap<>();
        Map<Long, Integer> distributionNotHTML = new HashMap<>();
        for (Map.Entry<Vertex, Long> node : inDegreeMap.entrySet()) {
            String n = (String) node.getKey().property("url").value();
            incrCounts(node.getValue(), distributionAll);
            if (uis.containsKey(n) && uis.get(n).mimeType.startsWith("text/html")) {
                incrCounts(node.getValue(), distributionHTML);
            } else {
                incrCounts(node.getValue(), distributionNotHTML);
            }
        }

        Map<String, Map<Long, Integer>> res = new HashMap<>();
        res.put("all", distributionAll);
        res.put("html", distributionHTML);
        res.put("not_html", distributionNotHTML);
        return res;
    }

    /**
     * Increments the count of hashCode in counts.
     */
    public static void incrCounts(Long inDegree, Map<Long, Integer> distribution) {
        if (distribution.containsKey(inDegree)) {
            distribution.put(inDegree, distribution.get(inDegree) + 1);
        } else {
            distribution.put(inDegree, 1);
        }
    }

    public static void printDistributions(Map<String, Map<Long, Integer>> distributions) {
        for (Map.Entry<String, Map<Long, Integer>> m : distributions.entrySet()) {
            System.out.println(m.getKey());
            for (Map.Entry<Long, Integer> record : sortRankAscByKey(m.getValue())) {
                System.out.println(record.getKey() + " in-degree:" + "\t" + record.getValue() + " nodes");
            }
        }
    }

    public static long sectionHeadThreshold(Map<Long, Integer> distribution) {
        List<Map.Entry<Long, Integer>> ds = sortRankAscByKey(distribution);
        return ds.get((int) ((double) ds.size() / 2.0)).getKey();
    }

    /**
     * This function sorts a Map object to a LinkedHashMap by key ascending order.
     */
    public static <T> List<Map.Entry<Long, T>> sortRankAscByKey(Map<Long, T> rank) {
        // map to list
        List<Map.Entry<Long, T>> listSort = new ArrayList<>(rank.entrySet());
        Collections.sort(listSort, Comparator.comparingLong(Map.Entry::getKey));
        return listSort;
    }

    // Puts all reachable nodes from the section head into nodesInSectionSet.
    public static void getSectionNodes(
            GraphTraversalSource gts, Vertex v, Set<Vertex> sections, Set<Vertex> nodesInSectionSet,
            boolean keepUrlSuffixes) {
        String sectionHeadUrl = v.property("url").value().toString();
        nodesInSectionSet.add(v);

        // get edges.
        Set<Edge> edges = gts.V(v).outE("outlink")
                .filter(x -> ((String) x.get().inVertex().property("MIME type").value()).startsWith("text/html"))
                .toSet();
        if (!edges.isEmpty()) {
            for (Edge edge : edges) {
                if (!gts.E(edge).inV().next().equals(gts.E(edge).outV().next()) //not a link to itself
                        && !nodesInSectionSet.contains(gts.E(edge).inV().next())
                        && !sections.contains(gts.E(edge).inV().next())
                        && (!keepUrlSuffixes
                            || edge.inVertex().property("url").value().toString().startsWith(sectionHeadUrl))) {
                    getSectionNodes(gts, gts.E(edge).inV().next(), sections, nodesInSectionSet, keepUrlSuffixes);
                }
            }
        }
    }

    /**
     * Puts each node in its closest section.
     * Traverses the graph from all sections, one hop at a time: we define a frontier for all sections, and in one
     * iteration, we go one hop from all the nodes in all frontiers.
     */
    public static Map<Vertex, Set<Vertex>> findSectionsNodes(Graph g, Set<Vertex> sectionHeads) {
        Map<Vertex, Set<Vertex>> sections = new HashMap<>();
        Map<Vertex, Set<Vertex>> frontiers = new HashMap<>();
        for (Vertex v : sectionHeads) {
            sections.put(v, new HashSet<>(Arrays.asList(new Vertex[]{v})));
            frontiers.put(v, new HashSet<>(Arrays.asList(new Vertex[]{v})));
            v.property("section", v.property("url").value().toString());
        }

        while (frontiers.values().stream().map(Set::isEmpty).anyMatch(b -> ! b)) {
            for (Vertex sectionHead : sectionHeads) {
                Set<Vertex> newFrontier = new HashSet<>();
                for (Vertex v : frontiers.get(sectionHead)) {
                    Set<Edge> edges = g.traversal().V(v).outE("outlink")
                            .filter(e -> (e.get().inVertex().property("MIME type").value().toString())
                                    .startsWith("text/html"))
                            .filter(e -> e.get().inVertex().property("section").value().equals(""))
                            .filter(e -> ! e.get().inVertex().equals(e.get().outVertex()))
                            .toSet();
                    for (Edge edge : edges) {
                        sections.get(sectionHead).add(edge.inVertex());
                        newFrontier.add(edge.inVertex());
                        edge.inVertex().property("section", sectionHead.property("url").value().toString());
                    }
                }
                frontiers.put(sectionHead, newFrontier);
            }
        }

        return sections;
    }

    public static <E> List<Map.Entry<E, Long>> sortRankDescByValue(Map<E, Long> rank) {
        List<Map.Entry<E, Long>> listSort = new ArrayList<>(rank.entrySet());
        Collections.sort(
                listSort,
                (i1, i2) -> {
                    long d = i2.getValue() - i1.getValue();
                    return d > 0 ? 1 : (d == 0 ? 0 : -1);
                });
        return listSort;
    }

    // For x.com, tries x.com and www.com, with http and https,
    // for www*.x.com, tries x.com and the original form, with http and https.
    public static Vertex selectRoot(String domain, Graph g) {
        Set<String> candidates = new HashSet<>(Arrays.asList(
                new String[]{"http://" + domain + "/", "https://" + domain + "/"}));
        if (domain.startsWith("www")) {
            String d = stripOptWww(domain);
            candidates.addAll(Arrays.asList(
                    new String[]{"http://" + d + "/", "https://" + d + "/"}));
        } else {
            candidates.addAll(Arrays.asList(
                    new String[]{"http://www." + domain + "/", "https://www." + domain + "/"}));
        }
        for (String c : candidates) {
            System.err.println("candidate " + c);
            try {
                Vertex v = g.traversal().V().has("url", c).next();
                return v;
            } catch (NoSuchElementException e) {
            }
        }
        return null;
    }

    // rejects URLs with a subpath that is also a section head candidate
    public static boolean isATopSectionHead(Vertex v, Set<String> sectionHeadsCandidates) {
        String url = (String) v.property("url").value();
        try {
            URI u = new URI(url);
            String[] segs = u.getPath().split("/");
            for (int i = 2; i < segs.length; i++) {
                String c = u.getScheme() + "://" + u.getAuthority() +
                        Arrays.stream(Arrays.copyOf(segs, i)).collect(Collectors.joining("/"));
                if (sectionHeadsCandidates.contains(c)) {
                    return false;
                }
            }
            return true;
        } catch (URISyntaxException e) {
            System.err.println("Could not parse " + url + " " + e);
            return true;
        }
    }

    // Returns the sections heads based on a high indegree and linked to from the root.
    public static Set<Vertex> graphSectionHeads(
            Map<String, URLInfo> uis, Graph g, Map<Vertex, Long> indegrees, String domain) {
        Map<String, Map<Long, Integer>> distributions = Sections.indegreeDistribution(indegrees, uis);
        Sections.printDistributions(distributions);
        long threshold = Sections.sectionHeadThreshold(distributions.get("html"));
        System.out.println("section head threshold: " + threshold);
        Vertex root = Sections.selectRoot(domain, g);
        if (root == null) {
            System.err.println("Could not find the root");
            System.exit(1);
        }
        // find all outlinks of the root page with an indegree above the threshold
        return g.traversal()
                .V(root)
                .outE("outlink")
                .filter(e -> !e.get().property("type").value().equals("I"))
                .inV()
                .filter(v -> ((String) v.get().property("MIME type").value()).startsWith("text/html"))
                .filter(v -> indegrees.get(v.get()) >= threshold)
                .toSet();
    }

    // remove sub-sections judging by their URLs
    public static Set<Vertex> filterHeadsUrlPrefix(Graph g, Set<Vertex> sectionHeads) {
        Set<String> sectionHeadsUrls =
                sectionHeads.stream().map(
                        v -> (String) v.property("url").value()).collect(Collectors.toSet());
        return sectionHeads.stream().filter(v -> isATopSectionHead(v, sectionHeadsUrls))
                .collect(Collectors.toSet());
    }

    public static Map<Vertex, Set<Vertex>> graphSectionsFromHeads(Graph g, Set<Vertex> sectionHeads) {
        Map<Vertex, Set<Vertex>> sections = new HashMap<>();
        for (Vertex v : sectionHeads) {
            Set<Vertex> nodesInSectionSet = new HashSet<>();
            getSectionNodes(g.traversal(), v, sectionHeads, nodesInSectionSet, true);
            sections.put(v, nodesInSectionSet);
        }
        return sections;
    }

    public static Map<String, Map<String, URLInfo>> verticesToUrlinfos(
            Map<String, URLInfo> uis, Map<Vertex, Set<Vertex>> sections)
            throws IOException {
        return sections.entrySet().stream().collect(
                Collectors.toMap(
                        e -> e.getKey().property("url").value().toString(),
                        e -> e.getValue().stream().collect(Collectors.toMap(
                                v -> v.property("url").value().toString(),
                                v -> uis.get(v.property("url").value().toString())))));
    }

    public static String extractSectionHeadUrl(byte[] data, String url) throws IOException, URISyntaxException {
        String encoding = HtmlUtils.detectEncoding(data);
        Document doc = Jsoup.parse(new ByteArrayInputStream(data), encoding, url);

        String re = ".*(bread.*crumb|ariane|fil).*";
        Elements candidates = doc.select("[class~=" + re + "]");
        candidates.addAll(doc.select("[id~=" + re + "]"));
        for (Element elt : candidates) {
            Elements anchors = elt.select("a[href]");
            if (anchors.size() == 1) {
                return Html.absoluteUrl(url, anchors.get(0).attr("href"));
            } else if (anchors.size() >= 1
                    && ! new URI(Html.absoluteUrl(url, anchors.get(0).attr("href"))).getPath().equals("/")) {
                // TODO add check on the domain too, this could be a link to the root of another site
                return Html.absoluteUrl(url, anchors.get(0).attr("href"));
            } else if (anchors.size() >= 2) {
                return Html.absoluteUrl(url, anchors.get(1).attr("href"));
            }
        }
        return null;
    }

    public static Map<String, Map<String, URLInfo>> jsoupSections(Map<String, URLInfo> uis, String domain)
            throws IOException {
        // get all records with jsoup section.
        Set<URLInfo> jsoupUis = uis.entrySet()
                .stream()
                .filter(v -> v.getValue().section != null)
                .map(v -> v.getValue()).collect(Collectors.toSet());
        // get all section headers.
        Set<String> sectionHeads = jsoupUis.stream().map(v -> v.section).collect(Collectors.toSet());

        Map<String, Map<String, URLInfo>> sectionsMap = sectionHeads.stream().collect(
                Collectors.toMap(
                        k -> k,
                        v -> jsoupUis.stream().filter(e -> e.section.equals(v)).collect(Collectors.toMap(
                                k -> k.url,
                                v1 -> v1))));
        return sectionsMap;
    }
}
