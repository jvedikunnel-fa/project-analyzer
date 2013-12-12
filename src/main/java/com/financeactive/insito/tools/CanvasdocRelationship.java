package com.financeactive.insito.tools;

import org.neo4j.graphdb.RelationshipType;

/**
 *
 */
public enum CanvasdocRelationship implements RelationshipType {
    ROOT, FORWARD, INCLUDE, LINK, MENU, ORPHAN;
}
