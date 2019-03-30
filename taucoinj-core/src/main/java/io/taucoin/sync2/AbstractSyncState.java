package io.taucoin.sync2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mikhail Kalinin
 * @since 13.08.2015
 */
public abstract class AbstractSyncState implements SyncState {

    private static final Logger logger = LoggerFactory.getLogger("sync");

    protected SyncManager syncManager;

    protected SyncStateEnum name;

    protected AbstractSyncState(SyncStateEnum name) {
        this.name = name;
    }

    @Override
    public boolean is(SyncStateEnum name) {
        return this.name == name;
    }

    @Override
    public String toString() {
        return name.toString();
    }

    @Override
    public void doOnTransition() {
        logger.trace("Transit to {} state", name);
    }

    @Override
    public void doMaintain() {
        logger.trace("Maintain {} state", name);
    }

    public void setSyncManager(SyncManager syncManager) {
        this.syncManager = syncManager;
    }
}
