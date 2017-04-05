package net.internetmemory.utils;

import org.xml.sax.Attributes;

public interface NodeVisitor {
    /**
     * This method is invoked before traversing a tree
     */
    public void init();

    /**
     * This method is called when the visitor visits a element
     * @param namespace the namespace URL of the element name (may be null)
     * @param localName the local name of the element (may be null)
     * @param qName the qualified name of the element
     * @param attributes attributes of the element
     * @return false if the visitor skip children of the node
     */
    public boolean visitElement(String namespace, String localName, String qName, Attributes attributes);

    /**
     * This method is called when the visitor leaves a element
     * @param namespace the namespace URL of the element name (may be null)
     * @param localName the local name of the element (may be null)
     * @param qName the qualified name of the element
     * @return false if the visitor terminate tree traverse
     */
    public boolean leaveElement(String namespace, String localName, String qName);

    /**
     * This method is called when visitor visits a text node
     * @param text content of the text node
     * @param nameOfParentElement name of the parent element (may be null due to unbalanced HTML
     *                            tag markups)
     */
    public void visitTextNode(String text, String nameOfParentElement);
}
