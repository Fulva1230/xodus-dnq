package com.jetbrains.teamsys.dnq.database;


import jetbrains.exodus.core.dataStructures.NanoSet;
import jetbrains.exodus.entitystore.Entity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class IncomingLinkViolation {

    private static final int MAXIMUM_BAD_LINKED_ENTITIES_TO_SHOW = 10;

    private String linkName;
    private List<Entity> entitiesCausedViolation;
    private boolean hasMoreEntitiesCausedViolations;

    public IncomingLinkViolation(String linkName) {
        this.linkName = linkName;
        this.entitiesCausedViolation = new ArrayList<Entity> (MAXIMUM_BAD_LINKED_ENTITIES_TO_SHOW);
        this.hasMoreEntitiesCausedViolations = false ;
    }

    public String getLinkName() {
        return linkName;
    }

    public boolean tryAddCause(Entity cause) {
        if (entitiesCausedViolation.size() < MAXIMUM_BAD_LINKED_ENTITIES_TO_SHOW) {
            entitiesCausedViolation.add(cause);
            return true;
        }
        hasMoreEntitiesCausedViolations = true;
        return false;
    }

    public Collection<String> getDescription() {
        // default implementation
        return createPerTypeDefaultErrorMessage(linkName + " for ");
    }

    private Collection<String> createPerTypeDefaultErrorMessage(String linkDescrition) {
        StringBuilder entitiesDescritpionBuilder = new StringBuilder();
        entitiesDescritpionBuilder.append(linkDescrition);
        entitiesDescritpionBuilder.append("{");
        Iterator<Entity> iterator = entitiesCausedViolation.iterator();
        while(iterator.hasNext()) {
            entitiesDescritpionBuilder.append(iterator.next().toString());
            if (iterator.hasNext()) entitiesDescritpionBuilder.append(", ");
        }
        if (hasMoreEntitiesCausedViolations) {
            entitiesDescritpionBuilder.append(" and more...}");
        } else {
            entitiesDescritpionBuilder.append("}");
        }
        return new NanoSet<String>(entitiesDescritpionBuilder.toString());
    }

    final protected Collection<String> createPerInstanceErrorMessage(MessageBuilder messageBuilder) {
        List<String> res = new ArrayList<String>();
        for (Entity entity : entitiesCausedViolation) {
            res.add(messageBuilder.build(null, entity, hasMoreEntitiesCausedViolations));
        }
        if (hasMoreEntitiesCausedViolations) {
            res.add("and more...");
        }
        return res;
    }

    final protected Collection<String> createPerTypeErrorMessage(MessageBuilder messageBuilder) {
        String descritpion = messageBuilder.build(new Iterable<Entity>(){
            public Iterator<Entity> iterator() {
                return entitiesCausedViolation.iterator();
            }
        }, null, hasMoreEntitiesCausedViolations);
        return new NanoSet<String>(descritpion);
    }
}