package net.internetmemory.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.steadystate.css.parser.CSSOMParser;
import net.internetmemory.utils.Html;
import net.internetmemory.utils.W3CDom;
import org.apache.xerces.parsers.DOMParser;
import org.cyberneko.html.HTMLConfiguration;
import org.htmlcleaner.BaseToken;
import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.CommentNode;
import org.htmlcleaner.ContentNode;
import org.htmlcleaner.HtmlNode;
import org.htmlcleaner.HtmlSerializer;
import org.htmlcleaner.SimpleHtmlSerializer;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.TagNodeVisitor;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document.OutputSettings;
import org.jsoup.nodes.Document.OutputSettings.Syntax;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;
import org.mozilla.intl.chardet.HtmlCharsetDetector;
import org.mozilla.intl.chardet.nsDetector;
import org.mozilla.intl.chardet.nsICharsetDetectionObserver;
import org.mozilla.intl.chardet.nsPSMDetector;
import org.mozilla.universalchardet.UniversalDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.css.sac.CSSException;
import org.w3c.css.sac.CSSParseException;
import org.w3c.css.sac.ErrorHandler;
import org.w3c.dom.Document;
import org.w3c.dom.css.CSSImportRule;
import org.w3c.dom.css.CSSRule;
import org.w3c.dom.css.CSSRuleList;
import org.w3c.dom.css.CSSStyleSheet;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * A utility class providing common functionality for parsing / cleaning and comparing HTML
 * documents for Web scraping.
 * <b>Important note: do not use this class for other purposes.</b> Algorithm implemented
 * in this class is specialized only for data extraction done by WebScrapingUtils library.
 * To keep consistency of annotations and wrappers, <b>do not modify this class to reuse
 * this class for other purpose</b>
 */
public class HtmlUtils {

    private static final Logger log = LoggerFactory.getLogger(HtmlUtils.class);

    private HtmlUtils() {
    }

    private static final int NUM_BYTES_TO_DETECT_ENCODING = 1024 * 128;
    private static final String DEFAULT_ENCODING = "UTF-8";

    private static final Map<String, String> ENCODING_NAME_FROM_MOZILLA_TO_JDK =
            Collections.unmodifiableMap(new HashMap<String, String>() {{
                put("ISO-2022-CN", "ISO2022CN");
                put("BIG5", "Big5");
                put("EUC-TW", "EUC_TW");
                put("GB18030", "GB18030");
                put("ISO-8859-5", "ISO8859_5");
                put("KOI8-R", "KOI8_R");
                put("WINDOWS-1251", "Cp1251");
                put("MACCYRILLIC", "MacCyrillic");
                put("IBM866", "Cp866");
                put("IBM855", "Cp855");
                put("ISO-8859-7", "ISO8859_7");
                put("WINDOWS-1253", "Cp1253");
                put("ISO-8859-8", "ISO8859_8");
                put("WINDOWS-1255", "Cp1255");
                put("ISO-2022-JP", "ISO2022JP");
                put("SHIFT_JIS", "SJIS");
                put("EUC-JP", "EUC_JP");
                put("ISO-2022-KR", "ISO2022KR");
                put("EUC-KR", "EUC_KR");
                put("UTF-8", "UTF-8");
                put("UTF-16BE", "UnicodeBigUnmarked");
                put("UTF-16LE", "UnicodeLittleUnmarked");
                put("UTF-32BE", "UTF_32BE");
                put("UTF-32LE", "UTF_32LE");
                put("WINDOWS-1252", "Cp1252");
            }});

    /**
     * Detects character encoding of the specified HTML document
     *
     * @param html byte array representation of the HTML document to analyze
     * @return the name (canonical for java.lang) of a detected character encoding of the HTML document
     */
    public static String detectEncoding(byte[] html) {

        final int numBytesToUse = html.length > NUM_BYTES_TO_DETECT_ENCODING ?
                NUM_BYTES_TO_DETECT_ENCODING : html.length;

        UniversalDetector detector = new UniversalDetector(null);
        detector.handleData(html, 0, numBytesToUse);
        detector.dataEnd();

        String detected = detector.getDetectedCharset();
        if (detected != null)
            detected = ENCODING_NAME_FROM_MOZILLA_TO_JDK.get(detected);

        return detected != null ? detected : DEFAULT_ENCODING;
    }


    /**
     * Alternative detect encoding, should be better than the other
     * @param html
     * @return
     */
    public static String detectEncodingAlt(byte[] html) {
        // Initalize the nsDetector() ;
        int lang = nsPSMDetector.ALL ;
        nsDetector det = new nsDetector(lang) ;

        // Set an observer...
        // The Notify() will be called when a matching charset is found.
        det.Init(new nsICharsetDetectionObserver() {
            public void Notify(String charset) {
//                HtmlCharsetDetector.found = true ;
//                System.out.println("CHARSET = " + charset);
            }
        });

        ByteArrayInputStream imp = new ByteArrayInputStream(html);
//        BufferedInputStream imp = new BufferedInputStream(url.openStream());

        byte[] buf = new byte[1024] ;
        int len;
        boolean done = false ;
        boolean isAscii = true ;
        int counter = 0;
        while( (len=imp.read(buf,0,buf.length)) != -1) {

            // Check if the stream is only ascii.
            if (isAscii)
                isAscii = det.isAscii(buf,len);

            // DoIt if non-ascii and not done yet.
            if (!isAscii && !done) {
//                log.info("iteration: "+counter++);
                //done =
                det.DoIt(buf, len, false);
            }
        }
        det.DataEnd();

        String[] probableCharsets = det.getProbableCharsets();

//        System.out.println(Arrays.asList(probableCharsets));
//        if (isAscii) {
//            System.out.println("CHARSET = ASCII");
//            found = true ;
//        }
        String charset = probableCharsets[0];
        if (!charset.equals("nomatch")) {
            return probableCharsets[0];
        }
        return DEFAULT_ENCODING;
    }

    private static final List<String> HTML_EVENT_ATTRIBUTES = Collections.unmodifiableList(
            Arrays.asList(
                    "onafterprint", "onbeforeprint", "onbeforeunload", "onerror",
                    "onhaschange", "onload", "onmessage", "onoffline",
                    "ononline", "onpagehide", "onpageshow", "onpopstate",
                    "onredo", "onresize", "onstorage", "onundo",
                    "onunload", "onblur", "onchange", "oncontextmenu",
                    "onfocus", "onformchange", "onforminput", "oninput",
                    "oninvalid", "onreset", "onselect", "onsubmit",
                    "onkeydown", "onkeypress", "onkeyup", "onclick",
                    "ondblclick", "ondrag", "ondragend", "ondragenter",
                    "ondragleave", "ondragover", "ondragstart", "ondrop",
                    "onmousedown", "onmousemove", "onmouseout", "onmouseover",
                    "onmouseup", "onmousewheel", "onscroll", "onabort",
                    "oncanplay", "oncanplaythrough", "ondurationchange", "onemptied",
                    "onended", "onerror", "onloadeddata", "onloadedmetadata",
                    "onloadstart", "onpause", "onplay", "onplaying",
                    "onprogress", "onratechange", "onreadystatechange", "onseeked",
                    "onseeking", "onstalled", "onsuspend", "ontimeupdate",
                    "onvolumechange", "onwaiting",
                    "http-equiv" // <- not event attribute, but causes page redirect with <META>
            )
    );

    private static final Map<String, List<String>> HTML_URL_ATTRIBUTES = Collections.unmodifiableMap(
            new HashMap<String, List<String>>() {{
                put("a", Arrays.asList("href"));
                put("applet", Arrays.asList("archive", "codebase"));
                put("area", Arrays.asList("href"));
                put("audio", Arrays.asList("src"));
                put("base", Arrays.asList("href"));
                put("blockquote", Arrays.asList("cite"));
                put("button", Arrays.asList("formaction"));
                put("command", Arrays.asList("icon"));
                put("del", Arrays.asList("cite"));
                put("embed", Arrays.asList("src"));
                put("form", Arrays.asList("action"));
                put("frame", Arrays.asList("longdesc", "src"));
                put("head", Arrays.asList("profile"));
                put("html", Arrays.asList("manifest"));
                put("iframe", Arrays.asList("longdesc", "src"));
                put("img", Arrays.asList("longdesc", "src", "usemap"));
                put("input", Arrays.asList("formaction", "src", "usemap"));
                put("ins", Arrays.asList("cite"));
                put("link", Arrays.asList("href"));
                put("object", Arrays.asList("archive", "classid", "codebase", "data", "usemap"));
                put("q", Arrays.asList("cite"));
                put("script", Arrays.asList("src"));
                put("source", Arrays.asList("src"));
                put("video", Arrays.asList("poster", "src"));
                put("meta", Arrays.asList("http-equiv"));
            }}
    );

    private static final List<String> HTML_FRAME_ELEMENTS = Collections.unmodifiableList(Arrays.asList(
            "frame", "iframe"
    ));

    private static final Pattern WHITE_SPACE = Pattern.compile("\\s");

    /**
     * Appends the given string to the specified string buffer with white space normalization.
     * @param builder the StringBuilder object
     * @param text the text to append
     * @param afterWhiteSpace true if the text is after white space
     * @return true if the appended text is terminated by a white space character
     */
    public static boolean normalizeWhiteSpace(StringBuilder builder, String text, boolean afterWhiteSpace){

        int length = text.length();
        for(int i = 0; i < length; i++){
            char c = text.charAt(i);
            if(Character.isWhitespace(c) || c == 160){
                afterWhiteSpace = true;
                continue;
            }

            if(afterWhiteSpace && builder.length() > 0) builder.append(' ');
            afterWhiteSpace = false;
            builder.append(c);
        }

        return afterWhiteSpace;
    }

    /**
     * Normalizes white spaces in the given string.
     * @param text the text to normalize
     * @return resulting text
     */
    public static String normalizeWhiteSpace(String text){
        StringBuilder builder = new StringBuilder(text.length());
        normalizeWhiteSpace(builder, text, false);
        return builder.toString();
    }

    /**
     * Utility methods for cleaning HTML documents by using HtmlCleaner.
     * @deprecated Use methods provided by {@link net.internetmemory.util.scraping.HtmlUtils.Jsoup} instead
     */
    @Deprecated
    public static class HtmlCleaner{

        private final static CleanerProperties props = new CleanerProperties();

        static {
            props.setRecognizeUnicodeChars(false);
            props.setAdvancedXmlEscape(true);
            props.setAllowMultiWordAttributes(true);
            props.setAllowHtmlInsideAttributes(true);
            props.setIgnoreQuestAndExclam(false);
            props.setOmitUnknownTags(false);
            props.setOmitComments(false);
            props.setNamespacesAware(false);
            props.setTranslateSpecialEntities(false);
            props.setTransSpecialEntitiesToNCR(true);
            props.setTreatUnknownTagsAsContent(false);
            props.setUseEmptyElementTags(true);
            props.setUseCdataForScriptAndStyle(false);
        }

        private static final HtmlSerializer defaultSerializer = new SimpleHtmlSerializer(props);
        private static final org.htmlcleaner.HtmlCleaner cleaner = new org.htmlcleaner.HtmlCleaner(props);

        private HtmlCleaner(){}

        /**
         * Cleans HTML document
         *
         * @param html     byte array representation of the HTML document to be cleaned
         * @param pageUrl the URL of the page to resolve relative URLs in the page (optional).
         *                If null, relative URLs in the page are not resolved.
         * @param encoding text encoding of the HTML document
         * @param forPreviewing if true, (1) resolve URLs is CSS (2) remove attributes may contain JavaScript code
         * @return cleaned HTML document
         * @throws IOException
         * @throws SAXException
         *
         * @deprecated Use an equivalent method provided by {@link net.internetmemory.util.scraping.HtmlUtils.Jsoup} instead
         */
        @Deprecated
        public static byte[] clean(byte[] html, String pageUrl, String encoding, boolean forPreviewing)
                throws IOException, SAXException {

            if (encoding == null) throw new NullPointerException("encoding cannot be null");

            TagNode root = parseToTagNode(html, encoding);

            removeScripts(root);
            if(forPreviewing)
                disableFrames(root);

            root.traverse(new ResolveLinks(pageUrl, forPreviewing));

            return toByteArray(root, encoding);
        }

        /**
         * Cleans HTML document
         *
         * @param html     byte array representation of the HTML document to be cleaned
         * @param pageUrl the URL of the page to resolve relative URLs in the page (optional).
         *                If null, relative URLs in the page are not resolved.
         * @param encoding text encoding of the HTML document
         * @return cleaned HTML document
         * @throws IOException
         * @throws SAXException
         *
         * @deprecated Use an equivalent method provided by {@link net.internetmemory.util.scraping.HtmlUtils.Jsoup} instead
         */
        @Deprecated
        public static byte[] clean(byte[] html, String pageUrl, String encoding)
                throws IOException, SAXException {

            return clean(html, pageUrl, encoding, true);
        }

        /**
         * Parses the specified HTML document by using HtmlCleaner library.
         * @param html byte array representation of the HTML document to be persed
         * @param encoding text encoding of the HTML document
         * @return The root node of the parsed document tree
         * @throws IOException
         *
         * @deprecated Use an equivalent method provided by {@link net.internetmemory.util.scraping.HtmlUtils.Jsoup} instead
         */
        @Deprecated
        public static TagNode parseToTagNode(byte[] html, String encoding) throws IOException {
            return cleaner.clean(new ByteArrayInputStream(html), encoding);
        }

        /**
         * Removes JavaScripts from the DOM tree rooted by the specified node.
         * @param root the root of DOM tree to remove scripts
         *
         * @deprecated Use an equivalent method provided by {@link net.internetmemory.util.scraping.HtmlUtils.Jsoup} instead
         */
        @Deprecated
        public static void removeScripts(TagNode root){
            root.traverse(new RemoveScripts());
        }

        /**
         * Rewrites relative links in the DOM tree rooted by the specified node into absolute links
         * @param root the root of DOM tree to rewrite links
         * @param pageUrl the URL of the page
         * @param rewriteLinksInCss if true then links in CSS styles also will be resolved.
         *
         * @deprecated Use an equivalent method provided by {@link net.internetmemory.util.scraping.HtmlUtils.Jsoup} instead
         */
        @Deprecated
        public static void resolveLinks(TagNode root, String pageUrl, boolean rewriteLinksInCss){
            root.traverse(new ResolveLinks(pageUrl, rewriteLinksInCss));
        }

        /**
         * Rewrites declaration of charset in the DOM tree rooted by the specified node
         * @param root the root of DOM tree to rewrite charset declaration
         * @param charsetName the name of the charset to set
         *
         * @deprecated Use an equivalent method provided by {@link net.internetmemory.util.scraping.HtmlUtils.Jsoup} instead
         */
        @Deprecated
        public static void rewriteCharset(TagNode root, String charsetName){
            root.traverse(new RewriteCharset(charsetName));
        }

        /**
         * Disables content loading in FRAME and IFRAME elements in the given DOM.
         * @param root the root of DOM tree
         *
         * @deprecated Use an equivalent method provided by {@link net.internetmemory.util.scraping.HtmlUtils.Jsoup} instead
         */
        @Deprecated
        public static void disableFrames(TagNode root){
            root.traverse(new DisableFrames());
        }

        /**
         * Serialize the specified HTML DOM tree parsed by using HtmlCleaner library.
         * @param root the root of HTML document tree to be serialized
         * @param encoding text encoding of the HTML document
         * @return byte array representation of the HTML document
         * @throws IOException
         *
         * @deprecated Use an equivalent method provided by {@link net.internetmemory.util.scraping.HtmlUtils.Jsoup} instead
         */
        @Deprecated
        public static byte[] toByteArray(TagNode root, String encoding) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            defaultSerializer.writeToStream(root, out, encoding);

            return out.toByteArray();
        }

        /**
         * Parses HTML document
         *
         * @param html     byte array representation of the HTML document to be parsed
         * @param encoding text encoding of the HTML document
         * @return parsed HTML document
         * @throws SAXException
         * @throws IOException
         *
         * @deprecated Use an equivalent method provided by {@link net.internetmemory.util.scraping.HtmlUtils.Jsoup} instead
         */
        @Deprecated
        public static Document parse(byte[] html, String encoding)
                throws SAXException, IOException {
            return parse(new ByteArrayInputStream(html), encoding);
        }

        /**
         * Parses HTML document
         *
         * @param html     input stream representation of the HTML document to be parsed
         * @param encoding text encoding of the HTML document
         * @return parsed HTML document
         * @throws SAXException
         * @throws IOException
         *
         * @deprecated Use an equivalent method provided by {@link net.internetmemory.util.scraping.HtmlUtils.Jsoup} instead
         */
        @Deprecated
        public static Document parse(InputStream html, String encoding)
                throws SAXException, IOException {

            if (encoding == null)
                throw new NullPointerException("encoding cannot be null");

            DOMParser parser = new DOMParser(new HTMLConfiguration());

            parser.setFeature(
                    "http://cyberneko.org/html/features/augmentations",
                    true);
            parser.setProperty(
                    "http://cyberneko.org/html/properties/default-encoding",
                    encoding);
            parser.setFeature(
                    "http://xml.org/sax/features/namespaces",
                    false);
            parser.setFeature(
                    "http://cyberneko.org/html/features/scanner/ignore-specified-charset",
                    true);
            parser.setFeature(
                    "http://cyberneko.org/html/features/balance-tags/ignore-outside-content",
                    false);
            parser.setFeature(
                    "http://cyberneko.org/html/features/balance-tags/document-fragment",
                    true);
            parser.setProperty(
                    "http://cyberneko.org/html/properties/names/elems", "lower");
            parser.setProperty(
                    "http://cyberneko.org/html/properties/names/attrs", "no-change");

            parser.setFeature("http://cyberneko.org/html/features/balance-tags", false);

            parser.parse(new InputSource(html));
            return parser.getDocument();
        }

        private static class ResolveLinks implements TagNodeVisitor {

            private String baseUri;
            private CSSOMParser cssParser = null;
            private final boolean resolveLinksInCss;
            private final StringBuilder buf = new StringBuilder();

            private ResolveLinks(String pageUri, boolean resolveLinksInCss) {
                this.baseUri = pageUri;
                this.resolveLinksInCss = resolveLinksInCss;
            }

            @Override
            public boolean visit(TagNode parentNode, HtmlNode currentNode) {

                if (!(currentNode instanceof TagNode)) return true;

                TagNode element = (TagNode) currentNode;
                String elementName = element.getName().toLowerCase();

                if(elementName.equals("base")){
                    // Handle <base> element which implicitly specify base URL
                    String href = element.getAttributeByName("href");
                    if(href != null && baseUri != null){
                        String resolved = Html.optAbsoluteUrl(baseUri, href);
                        if(resolved != null){
                            element.addAttribute("href", resolved);
                            baseUri = resolved;
                        }else {
                            element.removeAttribute("href");
                        }
                    }
                    return true;
                }

                if(baseUri == null) return true;

                // Rewrite legacy background attribute
                String background = element.getAttributeByName("background");
                if(background != null){
                    element.addAttribute("background", Html.optAbsoluteUrl(baseUri, background));
                }

                List<String> urlAttrList = HTML_URL_ATTRIBUTES.get(elementName);
                if(urlAttrList != null){
                    // Rewrite HyperLinks
                    for (String urlAttr : urlAttrList) {

                        String urlExp = element.getAttributeByName(urlAttr);
                        if(urlExp == null) continue;

                        // Rewrite relative URLs into absolute URLs
                        try {
                            String resolved = Html.optAbsoluteUrl(baseUri, urlExp.trim());
                            if(resolved != null && resolved.length() > 0)
                                element.addAttribute(urlAttr, resolved);
                            else
                                element.removeAttribute(urlAttr);
                        } catch (Exception e) {
                            // Do nothing
                        }
                    }
                }

                if(!resolveLinksInCss) return true;

                // Rewrite relative URLs in @import rule in in-page CSS
                if (elementName.equals("style")) {

                    buf.setLength(0);
                    for(BaseToken child: element.getAllChildren()){
                        if(child instanceof ContentNode)
                            buf.append(((ContentNode)child).getContent());
                        else if(child instanceof CommentNode)
                            buf.append(((CommentNode)child).getContent());
                        else if(child instanceof TagNode)
                            buf.append(((TagNode) child).getText());
                    }

                    String css = buf.toString();
                    if (css.length() > 15) {

                        if (cssParser == null) {
                            cssParser = new CSSOMParser();
                            cssParser.setErrorHandler(slf4jCssErrorHandler);
                        }

                        try {
                            StringReader reader = new StringReader(css);
                            org.w3c.css.sac.InputSource in = new org.w3c.css.sac.InputSource(reader);
                            CSSStyleSheet styleSheet = cssParser.parseStyleSheet(in, null, null);
                            CSSRuleList ruleList = styleSheet.getCssRules();
                            for (int i = 0; i < ruleList.getLength(); i++) {
                                CSSRule rule = ruleList.item(i);
                                if (rule instanceof CSSImportRule) {
                                    String href = ((CSSImportRule) rule).getHref();
                                    rule.setCssText("@import url(\"" + Html.optAbsoluteUrl(baseUri, href) + "\");");
                                }
                            }

                            element.removeAllChildren();
                            element.addChild(new ContentNode(styleSheet.toString()));
                        } catch (IOException e) {
                        }
                    }
                }

                return true;
            }
        }

        private static class RemoveScripts implements TagNodeVisitor {

            @Override
            public boolean visit(TagNode parentNode, HtmlNode currentNode) {

                if (!(currentNode instanceof TagNode)) return true;

                TagNode element = (TagNode) currentNode;

                for (TagNode child : element.getChildTags()) {
                    String tagName = child.getName().toLowerCase();
                    if (tagName.equals("script")){
                        // remove <script> elements
                        child.removeFromTree();
                    }
                }

                for(String eventAttr: HTML_EVENT_ATTRIBUTES){
                    element.removeAttribute(eventAttr);
                }

                String elementName = element.getName().toLowerCase();
                List<String> urlAttrList = HTML_URL_ATTRIBUTES.get(elementName);
                if(urlAttrList == null) return true;

                // Rewrite HyperLinks
                for (String urlAttr : urlAttrList) {
                    String urlExp = element.getAttributeByName(urlAttr);
                    if (urlExp != null) {
                        urlExp = urlExp.trim();

                        // Remove script links
                        if(urlExp.startsWith("javascript:")){
                            element.addAttribute(urlAttr, "javascript:void(0)");
                            continue;
                        }
                    }
                }

                return true;
            }
        }

        private static class RewriteCharset implements TagNodeVisitor {

            private String charsetName;

            public RewriteCharset(String charsetName) {
                this.charsetName = charsetName;
            }

            @Override
            public boolean visit(TagNode parentNode, HtmlNode currentNode) {

                if (!(currentNode instanceof TagNode)) return true;

                TagNode element = (TagNode) currentNode;
                String elementName = element.getName().toLowerCase();

                if(!"meta".equals(elementName)) return true;

                String metaCharset = element.getAttributeByName("charset");
                if(metaCharset != null) element.addAttribute("charset", charsetName);

                String metaHttpEquiv = element.getAttributeByName("http-equiv");
                String metaContent = element.getAttributeByName("content");

                if(metaHttpEquiv != null && metaContent != null &&
                        metaHttpEquiv.toLowerCase().equals("content-type")){

                    String[] tokens = metaContent.split(";", 2);
                    if(tokens.length == 2){
                        element.addAttribute("content", tokens[0] + "; charset=" + charsetName);
                    }
                }

                return true;
            }
        }

        private static class DisableFrames implements TagNodeVisitor{

            @Override
            public boolean visit(TagNode parentNode, HtmlNode currentNode) {

                if(!(currentNode instanceof TagNode)) return true;

                TagNode element = (TagNode) currentNode;

                if(HTML_FRAME_ELEMENTS.contains(element.getName())){
                    element.removeAttribute("src");
                }

                return true;
            }
        }
    }

    /**
     * Utility methods for cleaning HTML documents by using Jsoup.
     */
    public static class Jsoup{

        private Jsoup(){}

        /**
         * Parses HTML document into Jsoup Document object.
         * @param html HTML document
         * @param url the base URL of the document
         * @param encoding Character encoding of the document
         * @return Jsoup Document object
         * @throws IOException
         */
        public static org.jsoup.nodes.Document parseToJsoupDoc(InputStream html, String url, String encoding) throws IOException {
            return  org.jsoup.Jsoup.parse(html, encoding, url);
        }

        /**
         * Parses HTML document into Jsoup Document object.
         * @param html HTML document
         * @param url the base URL of the document
         * @param encoding Character encoding of the document
         * @return Jsoup Document object
         * @throws IOException
         */
        public static org.jsoup.nodes.Document parseToJsoupDoc(byte[] html, String url, String encoding) throws IOException {
            return  parseToJsoupDoc(new ByteArrayInputStream(html), url, encoding);
        }


        /**
         * Repeatedly parses HTML document into Jsoup Document object until it reaches a stable
         * representation
         * @param body HTML document
         * @param url the base URL of the document
         * @param encoding Character encoding of the document
         * @param forPreviewing if true, remove script elements and iframe elements
         * @return Jsoup Document object
         * @throws IOException
         */
        public static org.jsoup.nodes.Document cleanAndParseStableToJsoupDoc(
                byte[] body,
                String url,
                String encoding,
                boolean forPreviewing) throws IOException {

            int maxAttempts = 10;

            byte[] lastBody = body;
            org.jsoup.nodes.Document firstDoc = cleanAndParseToJsoupDoc(
                    lastBody, url, encoding, forPreviewing);
            org.jsoup.nodes.Document doc = firstDoc;
            for (int i = 0; i < maxAttempts; i++) {
                if (i > 0) {
                    doc = cleanAndParseToJsoupDoc(lastBody, url, encoding, forPreviewing);
                }
                doc.outputSettings(new OutputSettings().syntax(Syntax.xml));
                byte[] newBody = toByteArray(doc, encoding);
                if (Arrays.equals(lastBody, newBody)) {
                    return doc;
                }
                lastBody = newBody;
            }
            return doc;
        }

        /**
         * Parses HTML document
         * @param html HTML document
         * @param url the base URL of the document
         * @param encoding Character encoding of the document
         * @return Document object
         * @throws IOException
         */
        public static Document parse(InputStream html, String url, String encoding) throws IOException {
            return toW3CDocument(parseToJsoupDoc(html, url, encoding));
        }

        /**
         * Parses HTML document
         * @param html HTML document
         * @param url the base URL of the document
         * @param encoding Character encoding of the document
         * @return Document object
         * @throws IOException
         */
        public static Document parse(byte[] html, String url, String encoding) throws IOException {
            return toW3CDocument(parseToJsoupDoc(html, url, encoding));
        }

        /**
         * Cleans the given HTML document then parse it to Jsoup Document object
         * @param html HTML document
         * @param url the base URL of the document
         * @param encoding Character encoding of the document
         * @param forPreviewing if true, remove script elements and iframe elements
         * @return Jsoup Document object
         * @throws IOException
         */
        public static org.jsoup.nodes.Document cleanAndParseToJsoupDoc(byte[] html, String url, String encoding, boolean forPreviewing) throws IOException {
            org.jsoup.nodes.Document doc = parseToJsoupDoc(html, url, encoding);

            if(forPreviewing){
                removeScripts(doc);
                disableFrames(doc);
            }

            resolveLinks(doc, url, forPreviewing);

            return doc;
        }

        /**
         * Cleans the given HTML document then parse it
         * @param html HTML document
         * @param url the base URL of the document
         * @param encoding Character encoding of the document
         * @param forPreviewing if true, remove script elements and iframe elements
         * @return Document object
         * @throws IOException
         */
        public static Document cleanAndParse(byte[] html, String url, String encoding, boolean forPreviewing) throws IOException {

            return toW3CDocument(cleanAndParseToJsoupDoc(html, url, encoding, forPreviewing));
        }

        /**
         * Cleans the given HTML document then parse it
         * @param html HTML document
         * @param url the base URL of the document
         * @param encoding Character encoding of the document
         * @return Document object
         * @throws IOException
         */
        public static Document cleanAndParse(byte[] html, String url, String encoding) throws IOException {

            return toW3CDocument(cleanAndParseToJsoupDoc(html, url, encoding, true));
        }

        /**
         * Attempts to perform a "stable" cleanAndParse - i.e. it parses and serializes
         * the document until it stops changing. If that never happens it use the original
         * document.
         *
         * @param body HTML document
         * @param url the base URL of the document
         * @param encoding Character encoding of the document
         * @param forPreviewing if true then the method produces cleaned content for previewing.
         * @return Document object
         * @throws IOException
         */
        public static Document cleanAndParseStable(
                byte[] body,
                String url,
                String encoding,
                boolean forPreviewing) throws IOException {

            int maxAttempts = 10;

            byte[] lastBody = body;
            org.jsoup.nodes.Document firstDoc = cleanAndParseToJsoupDoc(
                    lastBody, url, encoding, forPreviewing);
            org.jsoup.nodes.Document doc = firstDoc;
            for (int i = 0; i < maxAttempts; i++) {
                if (i > 0) {
                    doc = cleanAndParseToJsoupDoc(lastBody, url, encoding, forPreviewing);
                }
                doc.outputSettings(new OutputSettings().syntax(Syntax.xml));
                byte[] newBody = toByteArray(doc, encoding);
                if (Arrays.equals(lastBody, newBody)) {
                    return toW3CDocument(doc);
                }
                lastBody = newBody;
            }
            return toW3CDocument(doc);
        }

        /**
         * Attempts to perform a "stable" cleanAndParse - i.e. it parses and serializes
         * the document until it stops changing. If that never happens it use the original
         * document.
         *
         * @param body HTML document
         * @param url the base URL of the document
         * @param encoding Character encoding of the document
         * @return Document object
         * @throws IOException
         */
        public static Document cleanAndParseStable(
                byte[] body,
                String url,
                String encoding) throws IOException {
            return cleanAndParseStable(body, url, encoding, true);
        }

        /**
         * Resolves relative links in the given document
         * @param doc HTML document
         * @param url the base URL of the document
         * @param resolveLinksInCss if true then relative URLs in in-line CSS are also resolved.
         */
        public static void resolveLinks(org.jsoup.nodes.Document doc, String url, boolean resolveLinksInCss){
            new NodeTraversor(new ResolveLinks(url, resolveLinksInCss)).traverse(doc);
        }

        /**
         * Remove JavaScript from the given document
         * @param doc HTML document
         */
        public static void removeScripts(org.jsoup.nodes.Document doc){
            new NodeTraversor(new RemoveScripts()).traverse(doc);
        }

        /**
         * Rewrites character set declaration in the given document.
         * @param doc HTML document
         * @param encoding character encoding
         */
        public static void rewriteCharset(org.jsoup.nodes.Document doc, String encoding){
            new NodeTraversor(new RewriteCharset(encoding)).traverse(doc);
        }

        /**
         * Disables content loading in FRAME and IFRAME elements in the given DOM.
         * @param doc HTML document
         */
        public static void disableFrames(org.jsoup.nodes.Document doc){
            new NodeTraversor(new DisableFrames()).traverse(doc);
        }

        /**
         * Serializes content of Jsoup's Document object into a bite array
         * @param doc HTML document
         * @param encoding character encoding
         * @return serialization result
         */
        public static byte[] toByteArray(org.jsoup.nodes.Document doc, String encoding){
            return doc.html().getBytes(Charset.forName(encoding));
        }

        /**
         * Converts Jsoup's Document object into org.w3c.dom.Document object.
         * @param doc Jsoup's Document object
         * @return converted result
         */
        public static Document toW3CDocument(org.jsoup.nodes.Document doc){
            return new W3CDom().fromJsoup(doc);
        }

        /**
         * Cleans the given HTML content. This method does (1) resolve relative URLs in the HTML (if
         * value of forPreviewing is true, URLs in inline CSS are also resolved), and
         * (2) remove JavaScript codes from the content (only if value of forPreviewing is true).
         * @param html HTML content
         * @param url the base URL of the content
         * @param encoding character encoding of the content
         * @param forPreviewing if true then the method produces cleaned content for previewing.
         * @return cleaned content
         * @throws IOException
         */
        public static byte[] clean(byte[] html, String url, String encoding, boolean forPreviewing) throws IOException {

            org.jsoup.nodes.Document doc = cleanAndParseToJsoupDoc(html, url, encoding, forPreviewing);

            doc.outputSettings(new OutputSettings().syntax(Syntax.xml));
            return toByteArray(doc, encoding);
        }

        /**
         * Cleans the given HTML content. This method does (1) resolve relative URLs in the HTML
         * and its in-line CSS, and (2) remove JavaScript codes from the content.
         * @param html HTML content
         * @param url the base URL of the content
         * @param encoding character encoding of the content
         * @return cleaned content
         * @throws IOException
         */
        public static byte[] clean(byte[] html, String url, String encoding) throws IOException {
            return clean(html, url, encoding, true);
        }

        private static class ResolveLinks implements NodeVisitor{

            private String baseUri;
            private CSSOMParser cssParser = null;
            private final boolean resolveLinksInCss;
            private final StringBuilder buf = new StringBuilder();

            private ResolveLinks(String pageUri, boolean resolveLinksInCss) {
                this.baseUri = pageUri;
                this.resolveLinksInCss = resolveLinksInCss;
            }

            @Override
            public void head(Node node, int depth) {

                if (!(node instanceof Element)) return;

                Element element = (Element) node;
                String elementName = element.nodeName();

                if(elementName.equals("base")){
                    // Handle <base> element which implicitly specify base URL
                    String href = element.attr("href");
                    if(href != null && baseUri != null){
                        String resolved = Html.optAbsoluteUrl(baseUri, href);
                        if(resolved != null){
                            element.attr("href", resolved);
                            baseUri = resolved;
                        }else {
                            element.removeAttr("href");
                        }
                    }
                    return;
                }

                if(baseUri == null) return;

                // Rewrite legacy background attribute
                String background = element.attr("background");
                if(background != null && background.length() > 0){
                    element.attr("background", Html.optAbsoluteUrl(baseUri, background));
                }

                List<String> urlAttrList = HTML_URL_ATTRIBUTES.get(elementName);
                if(urlAttrList != null){
                    // Rewrite HyperLinks
                    for (String urlAttr : urlAttrList) {

                        String urlExp = element.attr(urlAttr);
                        if(urlExp == null) continue;

                        // Rewrite relative URLs into absolute URLs
                        try {
                            String resolved = Html.optAbsoluteUrl(baseUri, urlExp.trim());
                            if(resolved != null && resolved.length() > 0)
                                element.attr(urlAttr, resolved);
                            else
                                element.removeAttr(urlAttr);
                        } catch (Exception e) {
                            // Do nothing
                        }
                    }
                }

                if(!resolveLinksInCss) return;

                // Rewrite relative URLs in @import rule in in-page CSS
                if (elementName.equals("style")) {

                    buf.setLength(0);
                    for(Node child: element.childNodes()){
                        if(child instanceof TextNode)
                            buf.append(((TextNode)child).text());
                        else if(child instanceof Comment)
                            buf.append(((Comment)child).getData());
                        else if(child instanceof DataNode)
                            buf.append(((DataNode) child).getWholeData());
                    }

                    String css = buf.toString();
                    if (css.length() > 15) {
                        if (cssParser == null) {
                            cssParser = new CSSOMParser();
                            cssParser.setErrorHandler(slf4jCssErrorHandler);
                        }

                        try {
                            StringReader reader = new StringReader(css);
                            org.w3c.css.sac.InputSource in = new org.w3c.css.sac.InputSource(reader);
                            CSSStyleSheet styleSheet = cssParser.parseStyleSheet(in, null, null);
                            CSSRuleList ruleList = styleSheet.getCssRules();
                            for (int i = 0; i < ruleList.getLength(); i++) {
                                CSSRule rule = ruleList.item(i);
                                if (rule instanceof CSSImportRule) {
                                    String href = ((CSSImportRule) rule).getHref();
                                    rule.setCssText("@import url(\"" + Html.optAbsoluteUrl(baseUri, href) + "\");");
                                }
                            }

                            element.empty();
                            element.appendChild(new DataNode(styleSheet.toString(), baseUri));

                        } catch (IOException e) {
                        }
                    }
                }

                return;
            }

            @Override
            public void tail(Node node, int depth) {

            }
        }

        private static class RemoveScripts implements NodeVisitor {

            @Override
            public void head(Node node, int depth) {

                if (!(node instanceof Element)) return;

                Element element = (Element) node;

                for (Element child : element.children()) {
                    String tagName = child.nodeName();
                    if (tagName.equals("script")){
                        // remove <script> elements
                        child.remove();
                    }
                }

                for(String eventAttr: HTML_EVENT_ATTRIBUTES){
                    element.removeAttr(eventAttr);
                }

                String elementName = element.nodeName();
                List<String> urlAttrList = HTML_URL_ATTRIBUTES.get(elementName);
                if(urlAttrList == null) return;

                // Rewrite HyperLinks
                for (String urlAttr : urlAttrList) {
                    String urlExp = element.attr(urlAttr);
                    if (urlExp != null) {
                        urlExp = urlExp.trim();

                        // Remove script links
                        if(urlExp.startsWith("javascript:")){
                            element.attr(urlAttr, "javascript:void(0)");
                            continue;
                        }
                    }
                }

                return;
            }

            @Override
            public void tail(Node node, int depth) {
            }
        }

        private static class RewriteCharset implements NodeVisitor {

            private String charsetName;

            public RewriteCharset(String charsetName) {
                this.charsetName = charsetName;
            }

            @Override
            public void head(Node node, int depth) {

                if (!(node instanceof Element)) return;

                Element element = (Element) node;
                String elementName = element.nodeName();

                if(!"meta".equals(elementName)) return;

                String metaCharset = element.attr("charset");
                if(metaCharset != null) element.attr("charset", charsetName);

                String metaHttpEquiv = element.attr("http-equiv");
                String metaContent = element.attr("content");

                if(metaHttpEquiv != null && metaContent != null &&
                        metaHttpEquiv.toLowerCase().equals("content-type")){

                    String[] tokens = metaContent.split(";", 2);
                    if(tokens.length == 2){
                        element.attr("content", tokens[0] + "; charset=" + charsetName);
                    }
                }

                return;
            }

            @Override
            public void tail(Node node, int depth) {
            }
        }

        private static class DisableFrames implements NodeVisitor{
            @Override
            public void head(Node node, int depth) {

                if(!(node instanceof Element)) return;

                Element element = (Element) node;

                if(HTML_FRAME_ELEMENTS.contains(element.nodeName())){
                    element.removeAttr("src");
                }
            }

            @Override
            public void tail(Node node, int depth) {

            }
        }
    }

    private static final ErrorHandler slf4jCssErrorHandler = new ErrorHandler() {

        private void log(String prefix, CSSParseException exception) {

            final StringBuilder sb = new StringBuilder();
            sb.append(prefix)
                    .append(" [")
                    .append(exception.getLineNumber())
                    .append(":")
                    .append(exception.getColumnNumber())
                    .append("] ")
                    .append(exception.getMessage());

            log.debug(sb.toString());
        }

        @Override
        public void warning(final CSSParseException exception) throws CSSException {

            log("CSS WARN", exception);
        }

        @Override
        public void error(final CSSParseException exception) throws CSSException {

            log("CSS ERROR", exception);
        }

        @Override
        public void fatalError(final CSSParseException exception) throws CSSException {

            log("CSS FATAL", exception);
        }
    };
}
