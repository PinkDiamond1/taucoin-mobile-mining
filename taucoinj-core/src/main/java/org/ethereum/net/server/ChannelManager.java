package org.ethereum.net.server;

import org.ethereum.core.Transaction;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListener;

import org.ethereum.sync.SyncManager;
import org.ethereum.net.rlpx.discover.NodeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import static org.ethereum.net.message.ReasonCode.DUPLICATE_PEER;

/**
 * @author Roman Mandeleil
 * @since 11.11.2014
 */
@Singleton
public class ChannelManager {

    private static final Logger logger = LoggerFactory.getLogger("net");

    private List<Channel> newPeers = new CopyOnWriteArrayList<>();
    private final Map<ByteArrayWrapper, Channel> activePeers = Collections.synchronizedMap(new HashMap<ByteArrayWrapper, Channel>());

    private ScheduledExecutorService mainWorker = Executors.newSingleThreadScheduledExecutor();

    EthereumListener listener;

    SyncManager syncManager;

    NodeManager nodeManager;

    Ethereum ethereum;

    @Inject
    public ChannelManager(EthereumListener listener, SyncManager syncManager, NodeManager nodeManager) {
        this.listener = listener;
        this.syncManager = syncManager;
        this.syncManager.setChannelManager(this);
        this.nodeManager = nodeManager;
        this.init();
    }

    public void setEthereum(Ethereum ethereum) {
        this.ethereum = ethereum;
    }

    public void init() {
        mainWorker.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                processNewPeers();
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void processNewPeers() {
        List<Channel> processed = new ArrayList<>();
        for(Channel peer : newPeers) {

            if(peer.isProtocolsInitialized()) {

                if (!activePeers.containsKey(peer.getNodeIdWrapper())) {
                    process(peer);
                } else {
                    peer.disconnect(DUPLICATE_PEER);
                }

                processed.add(peer);
            }

        }

        newPeers.removeAll(processed);
    }

    private void process(Channel peer) {
        if(peer.hasEthStatusSucceeded()) {
            if (syncManager.isSyncDone()) {
                peer.onSyncDone();
            }
            syncManager.addPeer(peer);
            activePeers.put(peer.getNodeIdWrapper(), peer);
        }
    }

    public void sendTransaction(Transaction tx) {

        synchronized (activePeers) {
            for (Channel channel : activePeers.values())
                channel.sendTransaction(tx);
        }
    }

    public void add(Channel peer) {
        newPeers.add(peer);
    }

    public void notifyDisconnect(Channel channel) {
        logger.debug("Peer {}: notifies about disconnect", channel.getPeerIdShort());
        channel.onDisconnect();
        syncManager.onDisconnect(channel);
        activePeers.values().remove(channel);
        newPeers.remove(channel);
    }

    public void onSyncDone() {

        synchronized (activePeers) {
            for (Channel channel : activePeers.values())
                channel.onSyncDone();
        }
    }
}