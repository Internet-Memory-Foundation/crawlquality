package net.internetmemory.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.HtmlSerializer;
import org.htmlcleaner.SimpleHtmlSerializer;
import org.htmlcleaner.TagNode;
import org.xml.sax.SAXException;

/**
 * Utility methods for using HTMLCleaner
 */
public class HtmlCleaner {

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
        props.setTreatUnknownTagsAsContent(false);
        props.setUseEmptyElementTags(true);
        props.setUseCdataForScriptAndStyle(false);
    }

    private static final org.htmlcleaner.HtmlCleaner cleaner = new org.htmlcleaner.HtmlCleaner(props);
    private static final HtmlSerializer defaultSerializer = new SimpleHtmlSerializer(props);

    /**
     * Cleans HTML document     *
     * @param in     InputStream of the HTML document to be cleaned
     * @param pageUrl the URL of the page to resolve relative URLs in the page (optional).
     *                If null, relative URLs in the page are not resolved.
     * @param encoding text encoding of the HTML document
     * @return cleaned HTML document
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public static byte[] cleanHtmlDocument(InputStream in, String pageUrl, String encoding)
            throws IOException, SAXException {

        if (encoding == null) throw new NullPointerException("encoding cannot be null");

        TagNode root = parseHtmlByHtmlCleaner(in, encoding);
        return serializeHtmlDocument(root, encoding);
    }

    /**
     * Parses the specified HTML document by using HtmlCleaner library.
     * @param in     InputStream of the HTML document to be cleaned
     * @param encoding text encoding of the HTML document
     * @return The root node of the parsed document tree
     * @throws IOException
     */
    public static TagNode parseHtmlByHtmlCleaner(InputStream in, String encoding) throws IOException {
        return cleaner.clean(in, encoding);
    }

    /**
     * Serialize the specified HTML DOM tree parsed by using HtmlCleaner library.
     * @param root the root of HTML document tree to be serialized
     * @param encoding text encoding of the HTML document
     * @return byte array representation of the HTML document
     * @throws IOException
     */
    public static byte[] serializeHtmlDocument(TagNode root, String encoding) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        defaultSerializer.writeToStream(root, out, encoding);

        return out.toByteArray();
    }

    private HtmlCleaner(){}
}
