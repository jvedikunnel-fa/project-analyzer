package com.financeactive.insito.tools;

import org.neo4j.graphdb.*;

/**
 *
 */
public class Analyzer {

    public static void main(String[] args) {
        GraphDB db = GraphDB.get();
        Transaction tx = db.beginTx();
        try {
            ResourceIterable<Node> services = db.getGraphService().findNodesByLabelAndProperty(DynamicLabel.label("service"), "type", "service");
            int i = 0;
            for (Node service : services) {
                System.out.println("" + (++i) + " " + service.getProperty("name"));
            }
        }
        finally {
            tx.success();
        }

    }

    private static void displayGraph(Node node, int depth){
        if (depth > 2) return;
        String prefix = "";
        for (int i = 0; i < depth; i++){
            prefix += "--";
        }
        System.out.println(prefix + " " + node.getProperty("name"));
        for (Relationship relationship : node.getRelationships(Direction.OUTGOING)) {
            displayGraph(relationship.getEndNode(), depth+1);
        }
    }

}
