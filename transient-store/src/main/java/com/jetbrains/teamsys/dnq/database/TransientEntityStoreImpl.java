package com.jetbrains.teamsys.dnq.database;

import jetbrains.exodus.core.dataStructures.StablePriorityQueue;
import jetbrains.exodus.core.dataStructures.hash.HashSet;
import jetbrains.exodus.core.execution.locks.Latch;
import jetbrains.exodus.database.TransientEntity;
import jetbrains.exodus.database.TransientEntityStore;
import jetbrains.exodus.database.TransientStoreSession;
import jetbrains.exodus.database.TransientStoreSessionListener;
import jetbrains.exodus.entitystore.*;
import jetbrains.exodus.env.EnvironmentConfig;
import jetbrains.exodus.query.QueryEngine;
import jetbrains.exodus.query.metadata.ModelMetaData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Vadim.Gurov
 */
public class TransientEntityStoreImpl implements TransientEntityStore {

    private static final Logger logger = LoggerFactory.getLogger(TransientEntityStoreImpl.class);

    private EntityStore persistentStore;
    private QueryEngine queryEngine;
    private ModelMetaData modelMetaData;
    private IEventsMultiplexer eventsMultiplexer;
    private final Set<TransientStoreSession> sessions =
            Collections.newSetFromMap(new ConcurrentHashMap<TransientStoreSession, Boolean>(200));
    private final ThreadLocal<TransientStoreSession> currentSession = new ThreadLocal<TransientStoreSession>();
    private final StablePriorityQueue<Integer, TransientStoreSessionListener> listeners = new StablePriorityQueue<Integer, TransientStoreSessionListener>();

    private volatile boolean open = true;
    private boolean closed = false;
    private final Latch enumContainersLock = Latch.create();
    private final Set<EnumContainer> initedContainers = new HashSet<EnumContainer>(10);
    private final Map<String, Entity> enumCache = new ConcurrentHashMap<String, Entity>();
    private final Map<String, BasePersistentClassImpl> persistentClassInstanceCache = new ConcurrentHashMap<String, BasePersistentClassImpl>();
    private final Map<Class, BasePersistentClassImpl> persistentClassInstances = new ConcurrentHashMap<Class, BasePersistentClassImpl>();

    final ReentrantLock flushLock = new ReentrantLock(true); // fair flushLock

    public TransientEntityStoreImpl() {
        if (logger.isTraceEnabled()) {
            logger.trace("TransientEntityStoreImpl constructor called.");
        }
    }

    public EntityStore getPersistentStore() {
        return persistentStore;
    }

    public QueryEngine getQueryEngine() {
        return queryEngine;
    }

    public IEventsMultiplexer getEventsMultiplexer() {
        return eventsMultiplexer;
    }

    public void setEventsMultiplexer(IEventsMultiplexer eventsMultiplexer) {
        this.eventsMultiplexer = eventsMultiplexer;
    }

    /**
     * Must be injected.
     *
     * @param persistentStore persistent entity store.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void setPersistentStore(EntityStore persistentStore) {
        final EnvironmentConfig ec = ((PersistentEntityStore) persistentStore).getEnvironment().getEnvironmentConfig();
        if (ec.getEnvTxnDowngradeAfterFlush() == EnvironmentConfig.DEFAULT.getEnvTxnDowngradeAfterFlush()) {
            ec.setEnvTxnDowngradeAfterFlush(false);
        }
        ec.setEnvTxnReplayMaxCount(Integer.MAX_VALUE);
        ec.setEnvTxnReplayTimeout(Long.MAX_VALUE);
        ec.setGcUseExclusiveTransaction(true);
        this.persistentStore = persistentStore;
    }

    /**
     * Must be injected.
     *
     * @param queryEngine query engine.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void setQueryEngine(QueryEngine queryEngine) {
        this.queryEngine = queryEngine;
    }

    @NotNull
    public String getName() {
        return "transient store";
    }

    @NotNull
    public String getLocation() {
        throw new UnsupportedOperationException("Not supported by transient store.");
    }

    @NotNull
    @Override
    public StoreTransaction beginTransaction() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public StoreTransaction beginExclusiveTransaction() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public TransientStoreSession beginReadonlyTransaction() {
        return registerStoreSession(new TransientSessionImpl(this, true));
    }

    @Nullable
    @Override
    public StoreTransaction getCurrentTransaction() {
        throw new UnsupportedOperationException();
    }

    public TransientStoreSession beginSession() {
        assertOpen();

        if (logger.isDebugEnabled()) {
            logger.debug("Begin new session");
        }

        TransientStoreSession currentSession = this.currentSession.get();
        if (currentSession != null) {
            logger.debug("Return session already associated with the current thread " + currentSession);
            return currentSession;
        }

        return registerStoreSession(new TransientSessionImpl(this, false));
    }

    public void resumeSession(TransientStoreSession session) {
        if (session != null) {
            assertOpen();

            TransientStoreSession current = currentSession.get();
            if (current != null) {
                if (current != session) {
                    throw new IllegalStateException("Another open transient session already associated with current thread.");
                }
            }

            currentSession.set(session);
        }
    }

    public void setModelMetaData(final ModelMetaData modelMetaData) {
        this.modelMetaData = modelMetaData;
    }

    @Nullable
    public ModelMetaData getModelMetaData() {
        return modelMetaData;
    }

    /**
     * It's guaranteed that current thread session is Open, if exists
     *
     * @return current thread session
     */
    @Nullable
    public TransientStoreSession getThreadSession() {
        return currentSession.get();
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;

        eventsMultiplexer.onClose(this);

        logger.info("Close transient store.");
        closed = true;

        int sessionsSize = sessions.size();
        if (sessionsSize > 0) {
            logger.warn("There're " + sessionsSize + " open transient sessions. Print.");
            if (logger.isDebugEnabled()) {
                for (TransientStoreSession session : sessions) {
                    TransientSessionImpl impl = session instanceof TransientSessionImpl ? (TransientSessionImpl) session : null;
                    if (impl != null) {
                        logger.warn("Not closed session stack trace: ", impl.getStack());
                    }
                }
            }
        }
    }

    public boolean entityTypeExists(@NotNull final String entityTypeName) {
        try {
            return ((PersistentEntityStore) persistentStore).getEntityTypeId(entityTypeName) >= 0;
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    public void renameEntityTypeRefactoring(@NotNull final String oldEntityTypeName, @NotNull final String newEntityTypeName) {
        final TransientSessionImpl s = (TransientSessionImpl) getThreadSession();

        if (s == null) {
            throw new IllegalStateException("No current thread session.");
        }

        s.addChangeAndRun(new TransientSessionImpl.MyRunnable() {
            @Override
            public boolean run() {
                ((PersistentEntityStore) s.getPersistentTransaction().getStore()).renameEntityType(oldEntityTypeName, newEntityTypeName);
                return true;
            }
        });
    }

    public void deleteEntityTypeRefactoring(@NotNull final String entityTypeName) {
        final TransientSessionImpl s = (TransientSessionImpl) getThreadSession();

        if (s == null) {
            throw new IllegalStateException("No current thread session.");
        }

        s.addChangeAndRun(new TransientSessionImpl.MyRunnable() {
            @Override
            public boolean run() {
                ((PersistentEntityStoreImpl) s.getPersistentTransaction().getStore()).deleteEntityType(entityTypeName);
                return true;
            }
        });
    }

    public void deleteEntityRefactoring(@NotNull Entity entity) {
        final TransientSessionImpl s = (TransientSessionImpl) getThreadSession();

        if (s == null) {
            throw new IllegalStateException("No current thread session.");
        }

        final Entity persistentEntity = (entity instanceof TransientEntity) ? ((TransientEntity) entity).getPersistentEntity() : entity;
        if (entity instanceof TransientEntity) {
            s.deleteEntity((TransientEntity) entity);
        } else {
            s.addChangeAndRun(new TransientSessionImpl.MyRunnable() {
                public boolean run() {
                    persistentEntity.delete();
                    return true;
                }
            });
        }
    }

    public void deleteLinksRefactoring(@NotNull final Entity entity, @NotNull final String linkName) {
        final TransientSessionImpl s = (TransientSessionImpl) getThreadSession();

        if (s == null) {
            throw new IllegalStateException("No current thread session.");
        }

        final Entity persistentEntity = (entity instanceof TransientEntity) ? ((TransientEntity) entity).getPersistentEntity() : entity;
        s.addChangeAndRun(new TransientSessionImpl.MyRunnable() {
            public boolean run() {
                persistentEntity.deleteLinks(linkName);
                return true;
            }
        });
    }

    public void deleteLinkRefactoring(@NotNull final Entity entity, @NotNull final String linkName, @NotNull final Entity link) {
        final TransientSessionImpl s = (TransientSessionImpl) getThreadSession();

        if (s == null) {
            throw new IllegalStateException("No current thread session.");
        }

        final Entity persistentEntity = (entity instanceof TransientEntity) ? ((TransientEntity) entity).getPersistentEntity() : entity;
        final Entity persistentLink = (link instanceof TransientEntity) ? ((TransientEntity) link).getPersistentEntity() : link;

        s.addChangeAndRun(new TransientSessionImpl.MyRunnable() {
            public boolean run() {
                persistentEntity.deleteLink(linkName, persistentLink);
                return true;
            }
        });
    }

    private TransientStoreSession registerStoreSession(TransientStoreSession s) {
        if (!sessions.add(s)) {
            throw new IllegalArgumentException("Session is already registered.");
        }

        currentSession.set(s);

        return s;
    }

    void unregisterStoreSession(TransientStoreSession s) {
        if (!sessions.remove(s)) {
            throw new IllegalArgumentException("Transient session wasn't previously registered.");
        }

        currentSession.remove();
    }

    @Nullable
    public TransientStoreSession suspendThreadSession() {
        assertOpen();

        final TransientStoreSession current = getThreadSession();
        if (current != null) {
            currentSession.remove();
        }

        return current;
    }

    public void addListener(@NotNull TransientStoreSessionListener listener) {
        listeners.push(Integer.valueOf(0), listener);
    }

    @Override
    public void addListener(TransientStoreSessionListener listener, final int priority) {
        listeners.push(Integer.valueOf(priority), listener);
    }

    public void removeListener(@NotNull TransientStoreSessionListener listener) {
        listeners.remove(listener);
    }

    void forAllListeners(@NotNull ListenerVisitor v) {
        for (final TransientStoreSessionListener listener : listeners) {
            v.visit(listener);
        }
    }

    public int sessionsCount() {
        return sessions.size();
    }

    public void dumpSessions(StringBuilder sb) {
        for (TransientStoreSession s : sessions) {
            sb.append("\n").append(s.toString());
        }
    }

    public boolean isEnumContainerInited(EnumContainer container) {
        return initedContainers.contains(container);
    }

    public void enumContainerInited(EnumContainer container) {
        initedContainers.add(container);
    }

    public void enumContainerLock() throws InterruptedException {
        enumContainersLock.acquire();
    }

    public void enumContainerUnLock() {
        enumContainersLock.release();
    }

    public Entity getCachedEnumValue(@NotNull final String className, @NotNull final String propName) {
        return enumCache.get(getEnumKey(className, propName));
    }

    public void setCachedEnumValue(@NotNull final String className,
                                   @NotNull final String propName, @NotNull final Entity entity) {
        enumCache.put(getEnumKey(className, propName), entity);
    }

    public BasePersistentClassImpl getCachedPersistentClassInstance(@NotNull final String entityType) {
        return persistentClassInstanceCache.get(entityType);
    }

    public BasePersistentClassImpl getCachedPersistentClassInstance(@NotNull final Class<? extends BasePersistentClassImpl> entityType) {
        return persistentClassInstances.get(entityType);
    }

    public void setCachedPersistentClassInstance(@NotNull final String entityType, @NotNull final BasePersistentClassImpl instance) {
        persistentClassInstanceCache.put(entityType, instance);
        Class<? extends BasePersistentClassImpl> clazz = instance.getClass();
        if (persistentClassInstances.get(clazz) != null) {
            if (logger.isWarnEnabled()) {
                logger.warn("Persistent class instance already registered for: " + clazz.getSimpleName());
            }
        }
        persistentClassInstances.put(clazz, instance);
    }

    private void assertOpen() {
        // this flag isn't even volatile, but this is legacy behavior
        if (closed) throw new IllegalStateException("Transient store is closed.");
    }

    public static String getEnumKey(@NotNull final String className, @NotNull final String propName) {
        final StringBuilder builder = new StringBuilder(24);
        builder.append(propName);
        builder.append('@');
        builder.append(className);
        return builder.toString();
    }

    interface ListenerVisitor {
        void visit(TransientStoreSessionListener listener);
    }

}