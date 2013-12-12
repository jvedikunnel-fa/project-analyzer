package com.financeactive.insito.tools;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.index.Index;

/**
 *
 */
public class GraphDB {


    public static final String TYPE_KEY = "type";
    private static final String DOCUMENT_KEY = "document";
    private static final String SERVICE_KEY = "service";
    private static final String NAME_KEY = "name";

    private static GraphDB INSTANCE;

    private static final String DB_PATH = "/developpement/java/neo4j-community-2.0.0/data/graph.db";

    public GraphDatabaseService getGraphService() {
        return graphDb;
    }

    private GraphDatabaseService graphDb;

    private Index<Node> index;

    public synchronized static GraphDB get(){
        if (INSTANCE == null){
            INSTANCE = new GraphDB();
        }
        return INSTANCE;
    }

    private GraphDB() {
        graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(DB_PATH);
        index = graphDb.index().forNodes("nodes");
        registerShutdownHook();
    }


    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                graphDb.shutdown();
            }
        });
    }

    public Transaction beginTx() {
        return graphDb.beginTx();
    }

    public Node getOrCreateEntryPointsNode(){
        Node entryPoints = findSingle(TYPE_KEY, "entry-points");
        if (entryPoints == null){
            entryPoints = graphDb.createNode();
            entryPoints.setProperty(NAME_KEY, "entry-points");
            entryPoints.setProperty(TYPE_KEY, "entry-points");
            index(entryPoints, TYPE_KEY, "entry-points");
            getRootNode().createRelationshipTo(entryPoints, CanvasdocRelationship.ROOT);
        }
        return entryPoints;
    }

    public Node getOrCreateStrutsConfigNode() {
        Node strutsConfig = findSingle(TYPE_KEY, "struts-config");
        if (strutsConfig == null){
            strutsConfig = graphDb.createNode();
            strutsConfig.setProperty(NAME_KEY, "struts-config");
            strutsConfig.setProperty(TYPE_KEY, "struts-config");
            index(strutsConfig, TYPE_KEY, "struts-config");
            getOrCreateEntryPointsNode().createRelationshipTo(strutsConfig, CanvasdocRelationship.ROOT);
        }
        return strutsConfig;
    }

    public Node getOrCreateMenuNode(String name) {
        Node menuNode = findSingle(TYPE_KEY, name);
        if (menuNode == null){
            menuNode = graphDb.createNode();
            menuNode.setProperty(NAME_KEY, name);
            menuNode.setProperty(TYPE_KEY, "menu");
            index(menuNode, TYPE_KEY, name);
            getOrCreateEntryPointsNode().createRelationshipTo(menuNode, CanvasdocRelationship.ROOT);
        }
        return menuNode;
    }

    public Node getOrCreateOrphansNode() {
        Node orphansNode = findSingle(TYPE_KEY, "orphans");
        if (orphansNode == null){
            orphansNode = graphDb.createNode();
            orphansNode.setProperty(NAME_KEY, "orphans");
            orphansNode.setProperty(TYPE_KEY, "orphans");
            index(orphansNode, TYPE_KEY, "orphans");
            getRootNode().createRelationshipTo(orphansNode, CanvasdocRelationship.ROOT);
        }
        return orphansNode;
    }

    public Node addDocument(Node parent, String documentName, CanvasdocRelationship relationship) {
        documentName = documentName.replace(" ", "_");
        Node document = findSingle(DOCUMENT_KEY, documentName);
        if (document == null){
            document = graphDb.createNode(DynamicLabel.label("document"));
            document.setProperty(NAME_KEY, documentName);
            document.setProperty(DOCUMENT_KEY, documentName);
            document.setProperty(TYPE_KEY, "document");
            index(document, DOCUMENT_KEY, documentName);
            index(document, TYPE_KEY, "document");
            System.out.println("Created document " + documentName);
        }
        if (parent != null && !hasRelationShip(parent, document, relationship)){
            parent.createRelationshipTo(document, relationship);
        }
        return document;
    }


    public void addService(Node parent, String className, CanvasdocRelationship relationship) {
        Node service = findSingle(SERVICE_KEY, className);
        if (service == null){
            service = graphDb.createNode(DynamicLabel.label("service"));
            service.setProperty(NAME_KEY, className);
            service.setProperty("class", className);
            service.setProperty(TYPE_KEY, "service");
            index(service, SERVICE_KEY, className);
            index(service, TYPE_KEY, "service");
            System.out.println("Created service " + className);
        }
        if (parent != null && !hasRelationShip(parent, service, relationship)){
            parent.createRelationshipTo(service, relationship);
        }
    }

    private Node findSingle(String indexName, Object value) {
        return index.query(indexName, value).getSingle();
    }

    public void index(Node node, String indexName, Object value ) {
        index.add(node, indexName, value);
    }

    public boolean containsDocument(String page) {
        return index.get(DOCUMENT_KEY, page).size() > 0;
    }

    public Node getRootNode() {
        Node root = graphDb.getNodeById(0L);
        root.setProperty("name", "ROOT");
        return root;
    }



    private boolean hasRelationShip(Node source, Node target, CanvasdocRelationship relationship){
        for (Relationship rel : source.getRelationships(Direction.OUTGOING)) {
            if (rel.getEndNode().equals(target)){
                return true;
            }
        }
        return false;
    }

    public boolean containsService(String className) {
        return index.get(SERVICE_KEY, className).size() > 0;
    }
}

