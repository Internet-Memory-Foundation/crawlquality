package net.internetmemory.utils;

import static java.util.Collections.*;

import java.net.URL;
import java.net.MalformedURLException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.EmptyStackException;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class Html {

    /**
     * List of HTML block elements
     */
    public static final Set<String> BLOCK_ELEMENTS;
    static {

        Set<String> set = new HashSet<>(Arrays.asList(
                "address", "article", "aside", "audio", "blockquote",
                "canvas", "dd", "div", "dl", "fieldset", "figcaption",
                "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5",
                "h6", "header", "hgroup", "hr", "ol", "li",
                "p", "pre", "section", "table", "td", "tr", "th",
                "tfoot", "ul", "span"));

        BLOCK_ELEMENTS = unmodifiableSet(set);
    }

    /**
     * List of attributes of HTML elements that may contain JavaScript code
     */
    public static final Set<String> HTML_EVENT_ATTRIBUTES;
    static {

        Set<String> set = new HashSet<>(Arrays.asList(
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
        ));

        HTML_EVENT_ATTRIBUTES = unmodifiableSet(set);
    }

    /**
     * List of HTML elements and their attributes that may have URLs
     */
    public static final Map<String, Set<String>> HTML_URL_ATTRIBUTES;
    static {

        Map<String, Set<String>> map = new HashMap<>();

        map.put("a", unmodifiableSet(new HashSet<String>(Arrays.asList("href"))));
        map.put("applet", unmodifiableSet(new HashSet<String>(Arrays.asList("archive", "codebase"))));
        map.put("area", unmodifiableSet(new HashSet<String>(Arrays.asList("href"))));
        map.put("audio", unmodifiableSet(new HashSet<String>(Arrays.asList("src"))));
        map.put("base", unmodifiableSet(new HashSet<String>(Arrays.asList("href"))));
        map.put("blockquote", unmodifiableSet(new HashSet<String>(Arrays.asList("cite"))));
        map.put("body", unmodifiableSet(new HashSet<String>(Arrays.asList("background"))));
        map.put("button", unmodifiableSet(new HashSet<String>(Arrays.asList("formaction"))));
        map.put("command", unmodifiableSet(new HashSet<String>(Arrays.asList("icon"))));
        map.put("del", unmodifiableSet(new HashSet<String>(Arrays.asList("cite"))));
        map.put("embed", unmodifiableSet(new HashSet<String>(Arrays.asList("src"))));
        map.put("form", unmodifiableSet(new HashSet<String>(Arrays.asList("action"))));
        map.put("frame", unmodifiableSet(new HashSet<String>(Arrays.asList("longdesc", "src"))));
        map.put("head", unmodifiableSet(new HashSet<String>(Arrays.asList("profile"))));
        map.put("html", unmodifiableSet(new HashSet<String>(Arrays.asList("manifest"))));
        map.put("iframe", unmodifiableSet(new HashSet<String>(Arrays.asList("longdesc", "src"))));
        map.put("img", unmodifiableSet(new HashSet<String>(Arrays.asList("longdesc", "src", "usemap"))));
        map.put("input", unmodifiableSet(new HashSet<String>(Arrays.asList("formaction", "src", "usemap"))));
        map.put("ins", unmodifiableSet(new HashSet<String>(Arrays.asList("cite"))));
        map.put("link", unmodifiableSet(new HashSet<String>(Arrays.asList("href"))));
        map.put("object", unmodifiableSet(new HashSet<String>(Arrays.asList("archive", "classid", "codebase", "data", "usemap"))));
        map.put("q", unmodifiableSet(new HashSet<String>(Arrays.asList("cite"))));
        map.put("script", unmodifiableSet(new HashSet<String>(Arrays.asList("src"))));
        map.put("source", unmodifiableSet(new HashSet<String>(Arrays.asList("src"))));
        map.put("video", unmodifiableSet(new HashSet<String>(Arrays.asList("poster", "src"))));
        map.put("meta", unmodifiableSet(new HashSet<String>(Arrays.asList("http-equiv"))));

        HTML_URL_ATTRIBUTES = unmodifiableMap(map);
    }

    public static final Set<String> NO_TEXT_CONTENT;

    /**
     * Set of HTML element that usually do not contain any readable text content
     */
    static {
        NO_TEXT_CONTENT = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
                "script", "style", "applet", "noframe", "noscript", "audio", "video", "canvas"
        )));
    }

    /**
     * List of fully qualified URI and commonly used namespece prefix of popular vocabularies
     */
    public static final Map<String, String> NAMESPACE_PREFIX;
    static {
        Map<String, String> map = new HashMap<>();

        // OpenGraph
        map.put("hhttp://opengraphprotocol.org/schema/", "og");
        map.put("http://ogp.me/ns#", "og");

        // Dublin core
        map.put("http://purl.org/dc/elements/1.1/", "dc");
        map.put("http://purl.org/dc/terms/", "dcterms");

        // Twitter cards
        map.put("http://api.twitter.com", "twitter"); // but not documented

        // Facebook
        map.put("http://www.facebook.com/2008/fbml", "fb");

        NAMESPACE_PREFIX = Collections.unmodifiableMap(map);
    }

    public static String absoluteUrl(String base, String link)
            throws MalformedURLException {
        if (link.startsWith("http://") || link.startsWith("https://"))
            return link;
        URL u = new URL(base);
        Stack<String> bpSegments = new Stack<>();
        ArrayList<String> segs = new ArrayList<String>(
                Arrays.asList(u.getPath().split("/")));
        bpSegments.addAll(segs);
        if (!base.endsWith("/"))
            bpSegments.pop();
        if (link.startsWith("/"))
            bpSegments.removeAllElements();
        for (String segment : link.split("/")) {
            if (segment.equals("."))
                ;
            else if (segment.equals(".."))
                try {
                    bpSegments.pop();
                } catch (EmptyStackException e) {
                }
            else
                bpSegments.push(segment);
        }
        String p = bpSegments.stream().collect(Collectors.joining("/"));
        if (!p.startsWith("/")) p = "/" + p; // TODO ugly
        return u.getProtocol() + "://" + u.getAuthority() + p;
    }

    public static String optAbsoluteUrl(String base, String link) {
        try {
            return absoluteUrl(base, link);
        } catch (MalformedURLException e) {
            return null;
        }
    }

    private Html(){}
}
