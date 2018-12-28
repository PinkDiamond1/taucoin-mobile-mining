package org.ethereum.manager;

import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListener;
import org.ethereum.net.client.PeerClient;
import org.ethereum.sync.SyncManager;
import org.ethereum.net.peerdiscovery.PeerDiscovery;
import org.ethereum.net.rlpx.discover.NodeManager;
import org.ethereum.net.server.ChannelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import static org.ethereum.config.SystemProperties.CONFIG;
import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;

/**
 * WorldManager is a singleton containing references to different parts of the system.
 *
 * @author Roman Mandeleil
 * @since 01.06.2014
 */
@Singleton
public class WorldManager {

    private static final Logger logger = LoggerFactory.getLogger("worldManager");

    private EthereumListener listener;

    private Blockchain blockchain;

    private Repository repository;

    private Wallet wallet;

    private PeerClient activePeer;

    private PeerDiscovery peerDiscovery;

    private BlockStore blockStore;

    private ChannelManager channelManager;

    private AdminInfo adminInfo;

    private NodeManager nodeManager;

    private SyncManager syncManager;

    private PendingState pendingState;

    @Inject
	public WorldManager(EthereumListener listener, Blockchain blockchain, Repository repository, Wallet wallet, PeerDiscovery peerDiscovery
                        , BlockStore blockStore, ChannelManager channelManager, AdminInfo adminInfo, NodeManager nodeManager, SyncManager syncManager
                        , PendingState pendingState) {
        logger.info("World manager instantiated");
        this.listener = listener;
        this.blockchain = blockchain;
        this.repository = repository;
        this.wallet = wallet;
        this.peerDiscovery = peerDiscovery;
        this.blockStore = blockStore;
        this.channelManager = channelManager;
		this.adminInfo = adminInfo;
        this.nodeManager = nodeManager;
        this.syncManager = syncManager;
        this.pendingState = pendingState;

    }

    public void init() {

        loadBlockchain();


    }

    public void initSync() {

        // must be initialized after blockchain is loaded
        syncManager.init();
        pendingState.init();
    }

    public void addListener(EthereumListener listener) {
        logger.info("Ethereum listener added");
        ((CompositeEthereumListener) this.listener).addListener(listener);
    }

    public void startPeerDiscovery() {
        if (!peerDiscovery.isStarted())
            peerDiscovery.start();
    }

    public void stopPeerDiscovery() {
        if (peerDiscovery.isStarted())
            peerDiscovery.stop();
    }

    public ChannelManager getChannelManager() {
        return channelManager;
    }

    public PeerDiscovery getPeerDiscovery() {
        return peerDiscovery;
    }

    public EthereumListener getListener() {
        return listener;
    }

    public void setWallet(Wallet wallet) {
        this.wallet = wallet;
    }

    public org.ethereum.facade.Repository getRepository() {
        return (org.ethereum.facade.Repository)repository;
    }

    public Blockchain getBlockchain() {
        return blockchain;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public void setActivePeer(PeerClient peer) {
        this.activePeer = peer;
    }

    public PeerClient getActivePeer() {
        return activePeer;
    }

    public BlockStore getBlockStore() {
        return blockStore;
    }

    public PendingState getPendingState() {
        return pendingState;
    }

    public void loadBlockchain() {

        if (!CONFIG.databaseReset())
            blockStore.load();

        Block bestBlock = blockStore.getBestBlock();
        if (bestBlock == null) {
            logger.info("DB is empty - adding Genesis");
            listener.trace("Importing Genesis");
            Genesis genesis = (Genesis)Genesis.getInstance();
            Set<ByteArrayWrapper> keys = genesis.getPremine().keySet();
            int size = keys.size();
            int index = 0;
            long startTime0 = System.nanoTime();
            for (ByteArrayWrapper key : keys) {
                index++;
                if (index % 500 == 0 || index == size) {
                    listener.trace("Importing genesis accounts: " + index + "/" + size);
                }
                repository.createAccount(key.getData());
                repository.addBalance(key.getData(), genesis.getPremine().get(key).getBalance());
            }
            long endTime0 = System.nanoTime();
            System.out.println("Import accounts time: " + ((endTime0 - startTime0) / 1000000));

            blockStore.saveBlock(Genesis.getInstance(), Genesis.getInstance().getCumulativeDifficulty(), true);

            blockchain.setBestBlock(Genesis.getInstance());
            blockchain.setTotalDifficulty(Genesis.getInstance().getCumulativeDifficulty());
            blockStore.flush();
            listener.onBlock(Genesis.getInstance(), new ArrayList<TransactionReceipt>());
            repository.dumpState(Genesis.getInstance(), 0, 0, null);
            repository.flush();

            logger.info("Genesis block imported");
        } else {

            blockchain.setBestBlock(bestBlock);

            BigInteger totalDifficulty = blockStore.getTotalDifficulty();
            blockchain.setTotalDifficulty(totalDifficulty);

            logger.info("*** Loaded up to block [{}] totalDifficulty [{}] with stateRoot [{}]",
                    blockchain.getBestBlock().getNumber(),
                    blockchain.getTotalDifficulty().toString(),
                    Hex.toHexString(blockchain.getBestBlock().getStateRoot()));
        }

        if (CONFIG.rootHashStart() != null) {

            // update world state by dummy hash
            byte[] rootHash = Hex.decode(CONFIG.rootHashStart());
            logger.info("Loading root hash from property file: [{}]", CONFIG.rootHashStart());
            this.repository.syncToRoot(rootHash);

        } else {

            // Update world state to latest loaded block from db
            // if state is not generated from empty premine list
            // todo this is just a workaround, move EMPTY_TRIE_HASH logic to Trie implementation
            if (!Arrays.equals(blockchain.getBestBlock().getStateRoot(), EMPTY_TRIE_HASH)) {
                this.repository.syncToRoot(blockchain.getBestBlock().getStateRoot());
            }
        }

/* todo: return it when there is no state conflicts on the chain
        boolean dbValid = this.repository.getWorldState().validate() || bestBlock.isGenesis();
        if (!dbValid){
            logger.error("The DB is not valid for that blockchain");
            System.exit(-1); //  todo: reset the repository and blockchain
        }
*/
    }


    @PreDestroy
    public void close() {
        stopPeerDiscovery();
        repository.close();
        blockchain.close();
    }

}
