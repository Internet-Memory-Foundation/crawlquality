package net.internetmemory.utils;

import java.util.Stack;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;

/**
 * Utility methods / classes for using NodeVisitor
 */
public class NodeVisitorUtils {

    /**
     * Implementation of SAX handler for traversing nodes in an HTML / XML document.
     * When node traverse is interrupted by a visitor, this handler throws
     * {@link NodeVisitorUtils.TraverseNodes.TraverseInterrupted}
     * exception.
     */
    public static class TraverseNodes extends DefaultHandler2{

        private NodeVisitor visitor = null;

        private Stack<String> path = new Stack<>();
        private StringBuilder characters = new StringBuilder();

        private String skipRoot = null;
        private int skipLevel = 0;

        public TraverseNodes() {
        }

        public TraverseNodes(NodeVisitor visitor) {
            this.visitor = visitor;
        }

        public NodeVisitor getVisitor() {
            return visitor;
        }

        public void setVisitor(NodeVisitor visitor) {
            this.visitor = visitor;
        }

        public void init(){

            path.clear();
            characters.setLength(0);

            skipRoot = null;
            skipLevel = 0;

            visitor.init();
        }

        @Override
        public void startDocument() throws SAXException {
            init();
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {

            if(skipRoot != null && skipRoot.equals(qName)) skipLevel++;

            if(skipLevel > 0) return;

            if(characters.length() > 0){
                String parentName = !path.empty() ? path.lastElement() : null;
                visitor.visitTextNode(characters.toString(), parentName);
            }

            characters.setLength(0);

            path.push(qName);
            boolean cont = visitor.visitElement(uri, localName, qName, attributes);
            if(!cont){
                skipRoot = qName;
                skipLevel = 1;
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {

            if(skipRoot != null && skipRoot.equals(qName)) skipLevel--;

            if(skipLevel > 0) return;
            else if (skipRoot != null) skipRoot = null;
            else if(characters.length() > 0){
                String parentName = !path.empty() ? path.lastElement() : null;
                visitor.visitTextNode(characters.toString(), parentName);
            }

            characters.setLength(0);

            boolean cont = visitor.leaveElement(uri, localName, qName);
            if(!path.empty()) path.pop();

            if(!cont) throw new TraverseInterrupted();
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {

            if(skipRoot == null){
                characters.append(ch, start, length);
            }else{
                characters.setLength(0);
            }
        }

        public static class TraverseInterrupted extends SAXException{
            public TraverseInterrupted() { super("Node traversal was interrupted by the visitor"); }
        }
    }

    /**
     * Traverses nodes in the DOM subtree rooted by the given node (inclusive)
     * @param visitor visitor visiting nodes in the subtree
     * @param node the root of the subtree to traverse
     * @return false if the visitor should stop traversing
     */
    public static boolean traverseNodes(NodeVisitor visitor, Node node){

        if(node.getNodeType() == Node.ELEMENT_NODE){

            boolean skipChildren = visitor.visitElement(
                    node.getNamespaceURI(),
                    node.getLocalName(),
                    node.getNodeName(),
                    new AttributeWrapper((Element) node)
            );

            if(skipChildren){
                for(int i = 0; i < node.getChildNodes().getLength(); i++){
                    boolean cont = traverseNodes(visitor, node.getChildNodes().item(i));
                    if(!cont) return false;
                }
            }

            boolean cont = visitor.leaveElement(
                    node.getNamespaceURI(),
                    node.getLocalName(),
                    node.getNodeName()
            );

            return cont;

        }else if(node.getNodeType() == Node.TEXT_NODE){

            Node parent = node.getParentNode();

            String parentName = parent != null && parent.getNodeType() == Node.ELEMENT_NODE ?
                    parent.getNodeName() : null;

            visitor.visitTextNode(node.getNodeValue(), parentName);
        }

        return true;
    }

    private static class AttributeWrapper implements Attributes{

        private final Element element;

        private AttributeWrapper(Element element) {
            this.element = element;
        }


        private Attr getAttr(int index){
            return (Attr) element.getAttributes().item(index);
        }

        @Override
        public int getLength() {
            return element.getAttributes().getLength();
        }

        @Override
        public String getURI(int index) {
            return getAttr(index).getNamespaceURI();
        }

        @Override
        public String getLocalName(int index) {
            return getAttr(index).getLocalName();
        }

        @Override
        public String getQName(int index) {
            return getAttr(index).getNodeName();
        }

        @Override
        public String getType(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getValue(int index) {
            return getAttr(index).getValue();
        }

        @Override
        public int getIndex(String uri, String localName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getIndex(String qName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getType(String uri, String localName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getType(String qName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getValue(String uri, String localName) {
            return element.getAttributeNS(uri, localName);
        }

        @Override
        public String getValue(String qName) {
            return element.getAttribute(qName);
        }
    }

    private NodeVisitorUtils(){}
}

