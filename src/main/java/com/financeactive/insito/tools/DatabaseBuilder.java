package com.financeactive.insito.tools;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static com.financeactive.insito.tools.CanvasdocRelationship.*;

/**
 *
 */
public class DatabaseBuilder {

    private static DocumentBuilder builder;
    private static XPath xpath;


    public static final String STRUTS_CONFIG_FILE = "/developpement/projets/insito/web/src/main/webapp/WEB-INF/struts-config.xml";
    public static final String DOCS_ROOT_DIR = "/developpement/projets/insito/web/src/main/webapp/WEB-INF/application/canvasdoc/page/";
    public static final String TML_MENUS_ROOT_DIR = "/developpement/projets/insito/web/src/main/resources/com/financeactive/insito/tapestry/components/menu/";


    private static GraphDB graphDb = GraphDB.get();

    private static Set<String> alreadyProcessedDocuments = new HashSet<String>();


    public static void main(String[] args) throws Exception {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        builder = factory.newDocumentBuilder();
        XPathFactory xPathfactory = XPathFactory.newInstance();
        xpath = xPathfactory.newXPath();


        Transaction tx = graphDb.beginTx();
        try {
            analyzeStrutsConfigFile();
            analyseTopMenu();
            analyseLeftMenu();
            analyseOrphans();
            tx.success();
        }
        finally {
            tx.finish();
        }

    }

    private static void analyseOrphans() throws Exception {
        Node orphans = graphDb.getOrCreateOrphansNode();
        Collection<File> pages = FileUtils.listFiles(new File(DOCS_ROOT_DIR), TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
        System.out.println("Total page count: " + pages.size());
        for (File pageFile : pages) {
            String page = pageFile.getAbsolutePath().substring(DOCS_ROOT_DIR.length());
            if (!graphDb.containsDocument(page)){
                addDocumentNode(orphans, page, CanvasdocRelationship.ORPHAN);
            }
        }
        //Clean orphans that have live relationships
        for (Relationship relationship : orphans.getRelationships(Direction.OUTGOING)) {
            Node orphan = relationship.getEndNode();
            if (orphan.hasRelationship(Direction.INCOMING, INCLUDE, LINK, MENU, FORWARD)){
                System.out.println("Deleting orphan relationship for " + orphan.getProperty("name"));
                relationship.delete();
            }
        }
    }

    private static void analyseLeftMenu() throws Exception {
        Node root = graphDb.getOrCreateMenuNode("left-menu");
        Collection<File> tmls = FileUtils.listFiles(new File(TML_MENUS_ROOT_DIR + "gauche"), TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
        for (File tml : tmls) {
            processTml(root, tml.getAbsolutePath());
        }
    }

    private static void analyseTopMenu() throws Exception {
        Node root = graphDb.getOrCreateMenuNode("top-menu");
        processTml(root, TML_MENUS_ROOT_DIR + "MenuHaut.tml");

    }


    private static void analyzeStrutsConfigFile() throws Exception {
        Node root = graphDb.getOrCreateStrutsConfigNode();

        Document doc = builder.parse(STRUTS_CONFIG_FILE);

        XPathExpression expr = xpath.compile("//forward[starts-with(@path , '/display.do?page=')]");
        NodeList nodeList = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
        for (int i = 0; i < nodeList.getLength(); i++) {
            String path = nodeList.item(i).getAttributes().getNamedItem("path").getNodeValue();
            String page = extractPageFrom(path);
            addDocumentNode(root, page, FORWARD);

        }
    }

    private static void addDocumentNode(Node parent, String page, CanvasdocRelationship relationship) throws Exception {
        if (!new File(DOCS_ROOT_DIR + page).exists()) {
            System.err.println("Dead link to " + page + " found in " + parent.getProperty("name"));
            return;
        }
        Node document = graphDb.addDocument(parent, page, relationship);
        processDocument(page, document);
    }


    private static void processTml(Node parent, String path) throws Exception {
        Document doc = builder.parse(path);
        xpath.setNamespaceContext(new UniversalNamespaceResolver(doc));
        XPathExpression expr = xpath.compile("//t:canvasdocpagelink");
        NodeList nodeList = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
        for (int i = 0; i < nodeList.getLength(); i++) {
            String page = nodeList.item(i).getAttributes().getNamedItem("page").getNodeValue();
            addDocumentNode(parent, page, MENU);
        }
    }

    private static void processDocument(String page, Node document) throws Exception {
        if (alreadyProcessedDocuments.contains(page)){
            return;
        }
        alreadyProcessedDocuments.add(page);
        if (page.endsWith(".xml")) {
            processXmlPage(page, document);
        } else if (page.endsWith(".xhtml")) {
            processXhtmlPage(page, document);
        }

    }

    private static void processXhtmlPage(String page, Node document) throws Exception {
        InputStream stream = readAndReplaceEntities(page);
        Document doc = builder.parse(stream);

        String includeXPath = "//include/@page";
        NodeList nodeList = (NodeList) xpath.compile(includeXPath).evaluate(doc, XPathConstants.NODESET);
        for (int i = 0; i < nodeList.getLength(); i++) {
            String include = nodeList.item(i).getNodeValue();
            addDocumentNode(document, include, INCLUDE);
        }

        String linkXPath = "//a[starts-with(@href , '/display.do?page=')]/@href";
        nodeList = (NodeList) xpath.compile(linkXPath).evaluate(doc, XPathConstants.NODESET);
        for (int i = 0; i < nodeList.getLength(); i++) {
            String target = nodeList.item(i).getNodeValue();
            target = extractPageFrom(target);
            addDocumentNode(document, target, LINK);
        }
    }

    private static InputStream readAndReplaceEntities(String page) throws Exception {
        String path = DOCS_ROOT_DIR + page;
        String fileContent = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        fileContent = fileContent.replaceAll("&nbsp;", "&#160;");
        return new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8));
    }

    private static void processXmlPage(String page, Node document) throws Exception {
        Document doc = builder.parse(DOCS_ROOT_DIR + page);

        //Static includes
        String includeXPath = "//include[@type='static']";
        NodeList nodeList = (NodeList) xpath.compile(includeXPath).evaluate(doc, XPathConstants.NODESET);
        for (int i = 0; i < nodeList.getLength(); i++) {
            String include = nodeList.item(i).getAttributes().getNamedItem("name").getNodeValue();
            addDocumentNode(document, include, INCLUDE);
        }

        //Dynamic includes
        String dynIncludeXPath = "//include[@type='dynamic' and @classe-type='classe-stateless' ]";
        nodeList = (NodeList) xpath.compile(dynIncludeXPath).evaluate(doc, XPathConstants.NODESET);
        for (int i = 0; i < nodeList.getLength(); i++) {
            org.w3c.dom.Node nameAttr = nodeList.item(i).getAttributes().getNamedItem("name");
            String include = nameAttr.getNodeValue();
            addServiceNode(document, include, INCLUDE);

        }

        //Forms
        String formXPath = "//form-name";
        nodeList = (NodeList) xpath.compile(formXPath).evaluate(doc, XPathConstants.NODESET);
        for (int i = 0; i < nodeList.getLength(); i++) {
            String include = nodeList.item(i).getTextContent();
            addDocumentNode(document, include, INCLUDE);
        }


        //Links
        String linkXPath = "//link";
        nodeList = (NodeList) xpath.compile(linkXPath).evaluate(doc, XPathConstants.NODESET);
        for (int i = 0; i < nodeList.getLength(); i++) {
            String target = nodeList.item(i).getTextContent();
            //Remove query string
            if (target.contains("?")) {
                target = target.substring(0, target.indexOf("?"));
            }
            if (isDocument(target)) {
                addDocumentNode(document, target, LINK);
            }
        }

        //Other links
        String hrefXPath = "//href[starts-with(@prefixe , 'display.do?page=')]/@prefixe";
        nodeList = (NodeList) xpath.compile(hrefXPath).evaluate(doc, XPathConstants.NODESET);
        for (int i = 0; i < nodeList.getLength(); i++) {
            String path = nodeList.item(i).getNodeValue();
            addDocumentNode(document, extractPageFrom(path), LINK);

        }
    }

    private static void addServiceNode(Node parent, String className, CanvasdocRelationship relationship) throws Exception {
        graphDb.addService(parent, className, relationship);
    }
    private static boolean isDocument(String link) {

        return link.endsWith(".xml") || link.endsWith(".xhtml");
    }

    private static String extractPageFrom(String path) {
        int start = "/display.do?page=".length();
        String page = path.substring(start);
        if (page.contains("&")) {
            page = page.substring(0, page.indexOf('&'));
        }
        return page;
    }

}
