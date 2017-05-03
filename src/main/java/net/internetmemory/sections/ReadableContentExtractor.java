package net.internetmemory.sections;

import de.jetwick.snacktory.*;
import net.internetmemory.utils.HtmlUtils;
import org.apache.commons.lang.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLOutputFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Created by buchi on 22/04/14.
 */
public class ReadableContentExtractor {

    /**
     * Snacktory's output formatter class extracts both readable plain text and HTML.
     * This formatter resolves relative URLs in specific element attributes (href, src etc.)
     * <b>Important note: this formatter is not stateless nor thread-safe. New instance must
     * be created for each data extraction thread.</b>
     */
    public static class ReadableHtmlOutputFormatter extends OutputFormatter {

        private static final Logger log = LoggerFactory.getLogger(ReadableHtmlOutputFormatter.class);

        private static final Pattern unlikelyPattern = Pattern.compile("display\\:none|visibility\\:hidden");

        private static final Pattern stopWordsPattern = Pattern.compile("com(m?)ent|share|footer|add|breadcrumb|menu", Pattern.CASE_INSENSITIVE);

        private static final Map<String, Set<String>> attributesToBeKept = new HashMap<String, Set<String>>() {

            {
                put("img", new HashSet<String>(Arrays.asList("abs:src", "width", "height")));
                put("abbr", new HashSet<String>(Arrays.asList("title")));
                put("acronym", new HashSet<String>(Arrays.asList("title")));
                put("bdo", new HashSet<String>(Arrays.asList("dir")));
                put("a", new HashSet<String>(Arrays.asList("abs:href")));
                put("q", new HashSet<String>(Arrays.asList("abs:cite")));
            }
        };

        private static final Set<String> elementsInCleanedHtml = new HashSet<String>(
                Arrays.asList(
                        "ul", "ol", "li", "dl", "dt", "dd", "address", "article", "aside",
                        "blockquote", "canvas", "figcaption", "figure", "footer",
                        "h1", "h2", "h3", "h4", "h5", "h6", "header", "hgroup",
                        "p", "pre", "section", "b", "big", "i", "small", "tt",
                        "abbr", "acronym", "cite", "code", "dfn", "em", "kbd", "strong", "samp", "var",
                        "a", "bdo", "br", "img", "q", "sub", "sup", "label", "map", "span"
                )
        );

        private static final Set<String> emptyElementsInCleanedHtml = new HashSet<String>(
                Arrays.asList(
                        "br", "img"
                )
        );

        private static final Set<String> elementsMustBeKept = new HashSet<String>(
                Arrays.asList(
                        "h1", "h2", "h3", "h4", "h5", "h6"
                )
        );

        private static final Set<String> elementsToBeUnwrapped = new HashSet<String>(
                Arrays.asList("div", "tbody", "thead", "tfoot", "tr", "th", "td", "center")
        );

        private static final XMLOutputFactory xof = XMLOutputFactory.newFactory();

        private String readableHtml = null;

        public String getReadableHtml() {
            return readableHtml;
        }

        /**
         * Copy from de.jetwick.snacktory.OutputFormatter
         *
         * @param e
         * @return
         */
        boolean unlikely(Node e) {
            if (e.attr("class") != null && e.attr("class").toLowerCase().contains("caption"))
                return true;

            String style = e.attr("style");
            String clazz = e.attr("class");
            if (unlikelyPattern.matcher(style).find() || unlikelyPattern.matcher(clazz).find())
                return true;
            return false;
        }

        @Override
        public String getFormattedText(Element topNode) {

            // Remove gravityScore because sometimes a top element has gravityScore = 0.
            topNode.removeAttr("gravityScore");

            Element copied = new Element(Tag.valueOf("div"), topNode.baseUri());
            copyTree(topNode, copied, false);
            readableHtml = copied.html();

            // Last treatment on readable html
            readableHtml = readableHtml.replaceAll("(<br\\s*/?>(\\s|&nbsp;)*)+", "<br />");
            return super.getFormattedText(topNode);
        }

        /**
         * Recursively copies a DOM subtree rooted by the specified element to construct a readable HTML.
         *
         * @param element     DOM element rooting the DOM tree to be copied
         * @param currentRoot a DOM element to which a copied subtree will be appended
         * @param onlyImages  set true to copy only image elements
         */
        protected void copyTree(Element element, Element currentRoot, boolean onlyImages) {

            if (unlikely(element)) return;

            String tagName = element.tagName().toLowerCase();

            // Check whether gravityScore <= 0.
            String gs = element.attr("gravityScore");
            if (gs != null && gs.length() > 0 && !elementsMustBeKept.contains(tagName)) {
                try {
                    onlyImages |= Integer.parseInt(gs) < 0;
                } catch (NumberFormatException e) {
                }
            }

            // If gravityScore <= 0, copy only images in descendant elements
            if (onlyImages && !tagName.equals("img")) {
                for (Node child : element.childNodes())
                    if (child instanceof Element)
                        copyTree((Element) child, currentRoot, onlyImages);
                return;
            }

            // If img elements doesn't have valid image URL, skip it
            if ("img".equals(tagName)) {
                String src = element.attr("abs:src");
                if (src == null || src.length() == 0)
                    return;
            }

            // if element contains the following stopwords as class or id, skip it
            String id = element.id();
            String clazz = StringUtils.join(element.classNames(), " ");
            if (stopWordsPattern.matcher(id).find() || stopWordsPattern.matcher(clazz).find())
                return;

            boolean unwrapped = false;
            if (elementsInCleanedHtml.contains(tagName)) {
                // Copy the element
                currentRoot = currentRoot.appendElement(tagName);

                // Copy only desired attributes
                Set<String> toBeKept = attributesToBeKept.get(tagName);
                if (toBeKept != null) {
                    for (String name : toBeKept) {
                        String value = element.attr(name);
                        if (value == null || value.length() == 0 || value.toLowerCase().startsWith("javascript:"))
                            continue;
                        name = name.startsWith("abs:") ? name.substring(4) : name;
                        currentRoot.attr(name, value);
                    }
                }
            } else if (elementsToBeUnwrapped.contains(tagName)) {

                // Unwrap the element instead of copying the element

                // Insert <br/> as delimiter if the previous element is not <br/>
                int numChildren = currentRoot.childNodeSize();
                if (numChildren > 0) {
                    Node lastChild = currentRoot.childNode(numChildren - 1);
                    if (!(lastChild instanceof Element) || !(((Element) lastChild).tagName().equals("br") || ((Element) lastChild).tagName().equals("p")))
                        currentRoot.appendElement("br");
                }

                unwrapped = true;
            } else {
                // Skip
                return;
            }

            // Visit child nodes
            for (Node child : element.childNodes()) {
                if (child instanceof TextNode) {
                    String text = ((TextNode) child).text();
                    if (!text.replace("\u00a0", "").trim().isEmpty())
                        currentRoot.appendText(text);
                } else if (child instanceof Element) {
                    copyTree((Element) child, currentRoot, onlyImages);
                }
            }

            if (!unwrapped) {
                // If the copied element is <a> element with no child or its only child is an image, remove it.
                if (tagName.equals("a") && (currentRoot.childNodes().size() == 0 ||
                        (currentRoot.childNodes().size() == 1 && currentRoot.select("img").size() == 1))) {
                    currentRoot.remove();
                }
                // If the copied element is not <img> not <br>, and its text content is empty,
                // remove it.
                else if (!emptyElementsInCleanedHtml.contains(tagName)) {
                    // Hack : trim method does not remove '&nbsp;'
                    String text = currentRoot.text().replace("\u00a0", "").trim();
                    if (text.isEmpty() && (currentRoot.select("img").size() == 0))
                        currentRoot.remove();
                }
            }
        }
    }

    /**
     * Custom version of ArticleTextExtractor which
     * <ol>
     * <li>removes all images except ones marked as "important", and</li>
     * <li>compute scores also for long span elements and list elements</li>
     * </ol>
     */
    public static class ReadableHtmlExtractor extends ArticleTextExtractor {

        public ReadableHtmlExtractor() {
            super();
            setPositive("(?i)(^(body|content|h?entry|main\\w+|page|post|text|blog|story\\w+|haupt))"
                    + "|arti(cle|kel)|instapaper_body");
        }

        /*
         * Customized. Remove all img elements except images marked as "important"
         */
        @Override
        public Element determineImageSource(Element el, List<ImageResult> images) {

            Element maxNode = super.determineImageSource(el, images);

            Elements els = el.select("img");
            if (els.isEmpty()) els = el.parent().select("img");

            for (Element e : els) {
                // Deleting small images
                String heightStr = e.attr("height");
                String widthStr = e.attr("width");
                Integer width = null;
                Integer height = null;
                if (widthStr != null && !widthStr.isEmpty())
                    width = Integer.parseInt(widthStr);
                if (heightStr != null && !heightStr.isEmpty())
                    height = Integer.parseInt(heightStr);

                if ((width != null && width < 70) || (height != null && height < 70)) {
                    e.remove();
                    continue;
                }

                // Deleting images with inconsistent src
                boolean important = false;
                for (ImageResult ir : images) {
                    String source = e.attr("src");
                    if (source.equals(ir.src)) {
                        if (source.endsWith(".gif"))
                            ir.weight -= 30;

                        important = true;
                        break;
                    }
                }

                if (!important) e.remove();
            }
            return maxNode;
        }

        // add "span", "ul" and "ol"
        //private static final Pattern NODES = Pattern.compile("p|div|td|h1|h2|article|section|span|ul|ol");
        // stan extended wrt to nicola's specs
        private static final Pattern NODES = Pattern.compile("p|div|td|h1|h2|h3|h4|h5|h6|hr|blockquote|article|section|hgroup|span|ul|ol|dl|dt|dd|code|pre|center|address");

        /**
         * Copied from de.jetwick.snacktory.ArticleTextExtractor
         *
         * @param doc
         * @return
         */
        @Override
        public Collection<Element> getNodes(Document doc) {
            Map<Element, Object> nodes = new LinkedHashMap<Element, Object>(64);
            int score = 100;
            for (Element el : doc.select("body").select("*")) {
                if (NODES.matcher(el.tagName()).matches()) {

                    // Use a span element if the span element is wrongly used as a block element.
                    if (el.tagName().equals("span")) {
                        int numBlock = el.select("p").size();
                        numBlock += el.select("div").size();
                        if (numBlock < 2 || el.text().length() < 100) continue;
                    }

                    nodes.put(el, null);
                    setScore(el, score);
                    score = score / 2;
                }
            }
            return nodes.keySet();
        }

        @Override
        protected int weightChildNodes(Element rootEl) {
            int weight = 0;
            Element caption = null;
            List<Element> pEls = new ArrayList<Element>(5);
            for (Element child : rootEl.children()) {
                String ownText = child.ownText();
                int ownTextLength = ownText.length();

                if (ownTextLength > 200)
                    weight += Math.max(50, ownTextLength / 10);

                if (child.tagName().equals("h1") || child.tagName().equals("h2")) {
                    weight += 30;
                } else if (child.tagName().equals("div") || child.tagName().equals("p")) {
                    weight += calcWeightForChild(child, ownText);
                    if (child.tagName().equals("p") && ownTextLength > 50)
                        pEls.add(child);

                    // Consider one more nested level
                    if (rootEl.tagName().equals("div") && child.tagName().equals("div")) {
                        for (Element nestedChild : child.children()) {
                            if (!nestedChild.tagName().equals("p"))
                                continue;

                            String nestedChildText = nestedChild.ownText();
                            int nestedChildTextLength = nestedChildText.length();

                            if (nestedChildTextLength > 200)
                                weight += Math.max(35, ownTextLength / 15);
                        }
                    }

                    if (child.className().toLowerCase().equals("caption"))
                        caption = child;
                }
            }

            // use caption and image
            if (caption != null)
                weight += 30;

            if (pEls.size() >= 2) {
                for (Element subEl : rootEl.children()) {
                    if ("h1;h2;h3;h4;h5;h6".contains(subEl.tagName())) {
                        weight += 20;
                        // headerEls.add(subEl);
                    } else if ("table;li;td;th".contains(subEl.tagName())) {
                        addScore(subEl, -30);
                    }

                    if ("p".contains(subEl.tagName()))
                        addScore(subEl, 30);
                }
            }
            return weight;
        }

        private int calcWeightForChild(Element child, String ownText) {
            int c = SHelper.count(ownText, "&quot;");
            c += SHelper.count(ownText, "&lt;");
            c += SHelper.count(ownText, "&gt;");
            c += SHelper.count(ownText, "px");
            int val;
            if (c > 5)
                val = -30;
            else
                val = (int) Math.round(ownText.length() / 25.0);

            addScore(child, val);
            return val;
        }
    }

    // TODO check we can make it static
    private static final ArticleTextExtractor articleTextExtractor = new ReadableHtmlExtractor();

    public static String text(byte[] data, String url) throws Exception {
        String encoding = HtmlUtils.detectEncoding(data);
        Document doc = Jsoup.parse(new ByteArrayInputStream(data), encoding, url);
        ReadableHtmlOutputFormatter formatter = new ReadableHtmlOutputFormatter();
        JResult result = new JResult();
        articleTextExtractor.extractContent(result, doc, formatter);

        return result.getText().equals("") ? "" : result.getText() + result.getTitle();
    }

    /**
     * Extract readable contents from the specified Web page
     *
     * @param in       HTML page to be processed
     * @param encoding character encoding of the page
     * @param url      the URL of the page
     * @return extracted readable contents
     * @throws Exception
     */
    public ReadableContent extractReadableContent(InputStream in, String encoding, String url)
            throws Exception {

        Document doc = Jsoup.parse(in, encoding, url);

        ReadableHtmlOutputFormatter formatter = new ReadableHtmlOutputFormatter();
        JResult result = new JResult();

        articleTextExtractor.extractContent(result, doc, formatter);

        List<String> imageUrls = new ArrayList<>(result.getImages().size());
        for (ImageResult image : result.getImages()) {
            imageUrls.add(image.src);
        }

        return new ReadableContent(
                result.getTitle(),
                formatter.getReadableHtml(),
                result.getText(),
                result.getImageUrl(),
                imageUrls
        );
    }
}

