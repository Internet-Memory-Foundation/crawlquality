package net.internetmemory.utils;

import net.internetmemory.constants.Html;
import org.xml.sax.Attributes;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Implementation of node visitor to extract plain texts from HTML
 */
public class TextExtractionVisitor implements NodeVisitor {
    private boolean separateLinesByBlocks = true;

    private Set<String> skip = new HashSet<String>(Arrays.asList(
            "script", "style", "applet", "noframe", "noscript", "audio", "video", "canvas"
    ));

    private TextAppender title = new TextAppender();
    private TextAppender body = new TextAppender();

    private boolean afterBlockNode = false;
    private boolean afterBodyStart = false;

    /**
     * If true then the extractor inserts new line characters after texts extracted from block nodes
     * @return If true then the extractor inserts new line characters after texts extracted from block nodes
     */
    public boolean getSeparateLinesByBlocks() {
        return separateLinesByBlocks;
    }

    /**
     * Set true if the extractor should insert new line characters after texts extracted from block nodes
     * @param separateLinesByBlocks true if the extractor should insert new line characters after texts extracted from block nodes
     */
    public void setSeparateLinesByBlocks(boolean separateLinesByBlocks) {
        this.separateLinesByBlocks = separateLinesByBlocks;
    }

    /**
     * Returns names of HTML elements skipped by the extractor
     * @return names of HTML elements skipped by the extractor
     */
    public Set<String> getSkip() {
        return skip;
    }

    /**
     * Sets names of HTML elements that should be skipped by the extractor
     * @param skip names of HTML elements that should be skipped by the extractor
     */
    public void setSkip(Set<String> skip) {
        this.skip = skip;
    }

    /**
     * Returns extracted page title
     * @return extracted page title
     */
    public String getTitle() {
        return title.toString();
    }

    /**
     * Returns extracted body text
     * @return extracted body text
     */
    public String getBody() {
        return body.toString();
    }

    @Override
    public void init() {
        title.reset();
        body.reset();
        afterBlockNode = false;
        afterBodyStart = false;
    }

    @Override
    public boolean visitElement(String namespace, String localName, String qName, Attributes attributes) {
        if (!afterBodyStart) {
            afterBodyStart = "body".equals(qName);
            return true;
        } else {
            return !skip.contains(qName);
        }
    }

    @Override
    public boolean leaveElement(String namespace, String localName, String qName) {
        if (afterBodyStart) {
            afterBlockNode |= Html.BLOCK_ELEMENTS.contains(qName);
        }
        return true;
    }

    @Override
    public void visitTextNode(String text, String nameOfParentElement) {
        if (!afterBodyStart) {
            if ("title".equals(nameOfParentElement))
                title.append(text);
        } else {
            if (afterBlockNode)
                body.append(separateLinesByBlocks ? "\n" : " ");
            body.append(text);
        }
        afterBlockNode = false;
    }

    private class TextAppender {
        private final StringBuilder buf = new StringBuilder();

        private boolean lastIsSpace = true;
        private boolean containNewLine = false;

        public void reset() {
            lastIsSpace = false;
            containNewLine = false;
            buf.setLength(0);
        }

        @Override
        public String toString() {
            return buf.toString();
        }

        public void append(CharSequence seq) {
            int length = seq.length();

            for (int i = 0; i < length; i++) {
                char c = seq.charAt(i);
                if (Character.isWhitespace(c) || c == 160){
                    lastIsSpace = true;
                    if (c == '\n') containNewLine = true;
                    continue;
                }

                if (lastIsSpace && buf.length() > 0) {
                    buf.append(containNewLine && separateLinesByBlocks ? '\n' : ' ');
                }

                lastIsSpace = false;
                containNewLine = false;

                int j;
                for (j = i + 1; j < length; j++) {
                    if (Character.isWhitespace(seq.charAt(j)) || c == 160) {
                        lastIsSpace = true;
                        if (c == '\n') containNewLine = true;
                        break;
                    }
                }

                buf.append(seq, i, j);
                i = j;
            }
        }
    }
}
