package io.taucoin.core;

import org.ethereum.config.SystemProperties;
import io.taucoin.core.Block;
import io.taucoin.core.BlockHeader;
import io.taucoin.core.Blockchain;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Bloom;
import org.ethereum.core.Chain;
import org.ethereum.core.ImportResult;
import org.ethereum.core.PendingState;
import org.ethereum.core.PendingTransaction;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.core.Wallet;
import org.ethereum.crypto.HashUtil;
import io.taucoin.db.BlockStore;
import org.ethereum.listener.EthereumListener;
import org.ethereum.manager.AdminInfo;
import org.ethereum.trie.Trie;
import org.ethereum.trie.TrieImpl;
import org.ethereum.util.AdvancedDeviceUtils;
import org.ethereum.util.RLP;
import io.taucoin.validator.DependentBlockHeaderRule;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.inject.Singleton;

import static java.lang.Runtime.getRuntime;
import static java.math.BigInteger.ZERO;
import static org.ethereum.config.Constants.UNCLE_GENERATION_LIMIT;
import static org.ethereum.config.Constants.UNCLE_LIST_LIMIT;
import static org.ethereum.config.SystemProperties.CONFIG;
import static org.ethereum.core.Denomination.SZABO;
import static org.ethereum.core.ImportResult.EXIST;
import static org.ethereum.core.ImportResult.IMPORTED_BEST;
import static org.ethereum.core.ImportResult.IMPORTED_NOT_BEST;
import static org.ethereum.core.ImportResult.NO_PARENT;
import static org.ethereum.util.BIUtil.isMoreThan;

/**
 * The Ethereum blockchain is in many ways similar to the Bitcoin blockchain,
 * although it does have some differences.
 * <p>
 * The main difference between Ethereum and Bitcoin with regard to the blockchain architecture
 * is that, unlike Bitcoin, Ethereum blocks contain a copy of both the transaction list
 * and the most recent state. Aside from that, two other values, the block number and
 * the difficulty, are also stored in the block.
 * </p>
 * The block validation algorithm in Ethereum is as follows:
 * <ol>
 * <li>Check if the previous block referenced exists and is valid.</li>
 * <li>Check that the timestamp of the block is greater than that of the referenced previous block and less than 15 minutes into the future</li>
 * <li>Check that the block number, difficulty, transaction root, uncle root and gas limit (various low-level Ethereum-specific concepts) are valid.</li>
 * <li>Check that the proof of work on the block is valid.</li>
 * <li>Let S[0] be the STATE_ROOT of the previous block.</li>
 * <li>Let TX be the block's transaction list, with n transactions.
 * For all in in 0...n-1, set S[i+1] = APPLY(S[i],TX[i]).
 * If any applications returns an error, or if the total gas consumed in the block
 * up until this point exceeds the GASLIMIT, return an error.</li>
 * <li>Let S_FINAL be S[n], but adding the block reward paid to the miner.</li>
 * <li>Check if S_FINAL is the same as the STATE_ROOT. If it is, the block is valid; otherwise, it is not valid.</li>
 * </ol>
 * See <a href="https://github.com/ethereum/wiki/wiki/White-Paper#blockchain-and-mining">Ethereum Whitepaper</a>
 *
 * @author Roman Mandeleil
 * @author Nick Savers
 * @since 20.05.2014
 */
@Singleton
public class BlockchainImpl implements Blockchain, org.ethereum.facade.Blockchain {


    private static final Logger logger = LoggerFactory.getLogger("blockchain");
    private static final Logger stateLogger = LoggerFactory.getLogger("state");

    // to avoid using minGasPrice=0 from Genesis for the wallet
    private static final long INITIAL_MIN_GAS_PRICE = 10 * SZABO.longValue();

    @Resource
    private final Set<PendingTransaction> pendingTransactions = new HashSet<>();

    private Repository repository;
    private Repository track;

    private BlockStore blockStore;

    private Block bestBlock;
    private BigInteger totalDifficulty = ZERO;

    Wallet wallet;

    private EthereumListener listener;

    ProgramInvokeFactory programInvokeFactory;

    private AdminInfo adminInfo;

    private DependentBlockHeaderRule parentHeaderValidator;

    private PendingState pendingState;

    private List<Chain> altChains = new ArrayList<>();
    private List<Block> garbage = new ArrayList<>();

    long exitOn = Long.MAX_VALUE;

    public boolean byTest = false;
    private boolean fork = false;

    public BlockchainImpl() {
    }


    //todo: autowire over constructor
    @Inject
    public BlockchainImpl(BlockStore blockStore, Repository repository,
                          Wallet wallet, AdminInfo adminInfo, DependentBlockHeaderRule parentHeaderValidator,
                          PendingState pendingState, EthereumListener listener) {
        this.blockStore = blockStore;
        this.repository = repository;
        this.wallet = wallet;
        this.adminInfo = adminInfo;
        this.parentHeaderValidator = parentHeaderValidator;
        this.pendingState = pendingState;
        this.listener = listener;
        this.programInvokeFactory = new ProgramInvokeFactoryImpl();
        this.programInvokeFactory.setBlockchain(this);
    }

    @Override
    public byte[] getBestBlockHash() {
        return getBestBlock().getHash();
    }

    @Override
    public long getSize() {
        return bestBlock.getNumber() + 1;
    }

    @Override
    public Block getBlockByNumber(long blockNr) {
        return blockStore.getChainBlockByNumber(blockNr);
    }

    @Override
    public TransactionReceipt getTransactionReceiptByHash(byte[] hash) {
        throw new UnsupportedOperationException("TODO: will be implemented soon "); // FIXME: go and fix me
    }

    @Override
    public Block getBlockByHash(byte[] hash) {
        return blockStore.getBlockByHash(hash);
    }

    @Override
    public List<byte[]> getListOfHashesStartFrom(byte[] hash, int qty) {
        return blockStore.getListHashesEndWith(hash, qty);
    }

    @Override
    public List<byte[]> getListOfHashesStartFromBlock(long blockNumber, int qty) {
        long bestNumber = bestBlock.getNumber();

        if (blockNumber > bestNumber) {
            return Collections.emptyList();
        }

        if (blockNumber + qty - 1 > bestNumber) {
            qty = (int) (bestNumber - blockNumber + 1);
        }

        long endNumber = blockNumber + qty - 1;

        Block block = getBlockByNumber(endNumber);

        List<byte[]> hashes = blockStore.getListHashesEndWith(block.getHash(), qty);

        // asc order of hashes is required in the response
        Collections.reverse(hashes);

        return hashes;
    }

    private byte[] calcTxTrie(List<Transaction> transactions) {

        Trie txsState = new TrieImpl(null);

        if (transactions == null || transactions.isEmpty())
            return HashUtil.EMPTY_TRIE_HASH;

        for (int i = 0; i < transactions.size(); i++) {
            txsState.update(RLP.encodeInt(i), transactions.get(i).getEncoded());
        }
        return txsState.getRootHash();
    }

    public ImportResult tryConnectAndFork(Block block) {

        //TODO::try to roll back
        return IMPORTED_NOT_BEST;

//        Repository savedRepo = this.repository;
//        Block savedBest = this.bestBlock;
//        BigInteger savedTD = this.totalDifficulty;
//
//        this.bestBlock = blockStore.getBlockByHash(block.getPreviousHeaderHash());
//        totalDifficulty = blockStore.getTotalDifficultyForHash(block.getPreviousHeaderHash());
//        this.repository = this.repository.getSnapshotTo(this.bestBlock.getStateRoot());
//        this.fork = true;
//
//        try {
//
//            // FIXME: adding block with no option for flush
//            add(block);
//        } catch (Throwable th) {
//            logger.error("Unexpected error: ", th);
//        } finally {this.fork = false;}
//
//        if (isMoreThan(this.totalDifficulty, savedTD)) {
//
//            logger.info("Rebranching: {} ~> {}", savedBest.getShortHash(), block.getShortHash());
//
//            // main branch become this branch
//            // cause we proved that total difficulty
//            // is greateer
//            blockStore.reBranch(block);
//
//            // The main repository rebranch
//            this.repository = savedRepo;
//            this.repository.syncToRoot(block.getStateRoot());
//
//            // flushing
//            if (!byTest){
//                repository.flush();
//                blockStore.flush();
//                System.gc();
//            }
//
//            return IMPORTED_BEST;
//        } else {
//
//            // Stay on previous branch
//            this.repository = savedRepo;
//            this.bestBlock = savedBest;
//            this.totalDifficulty = savedTD;
//
//            return IMPORTED_NOT_BEST;
//        }
    }


    public ImportResult tryToConnect(Block block) {

        //wrap the block
        Block preBlock = blockStore.getBlockByHash(block.getPreviousHeaderHash());
        if (preBlock == null)
            return NO_PARENT;
        if (block.isMsg()) {
            block.setNumber(preBlock.getNumber() + 1);
            BigInteger baseTarget = ProofOfTransaction.calculateRequiredBaseTarget(block, blockStore);
            block.setBaseTarget(baseTarget);
            BigInteger lastCumulativeDifficulty = preBlock.getCumulativeDifficulty();
            BigInteger cumulativeDifficulty = ProofOfTransaction.
                    calculateCumulativeDifficulty(lastCumulativeDifficulty, baseTarget);
            block.setCumulativeDifficulty(cumulativeDifficulty);
        }

        if (logger.isInfoEnabled())
            logger.info("Try connect block hash: {}, number: {}",
                    Hex.toHexString(block.getHash()).substring(0, 6),
                    block.getNumber());

        if (blockStore.getMaxNumber() >= block.getNumber() &&
                blockStore.isBlockExist(block.getHash())) {

            if (logger.isDebugEnabled())
                logger.debug("Block already exist hash: {}, number: {}",
                        Hex.toHexString(block.getHash()).substring(0, 6),
                        block.getNumber());

            // retry of well known block
            return EXIST;
        }

        // The simple case got the block
        // to connect to the main chain
        if (bestBlock.isParentOf(block)) {
            recordBlock(block);
            add(block);

            pendingState.processBest(block);

            return IMPORTED_BEST;
        } else {

            if (blockStore.isBlockExist(block.getPreviousHeaderHash())) {
                recordBlock(block);
                ImportResult result = tryConnectAndFork(block);

                if (result == IMPORTED_BEST) pendingState.processBest(block);

                return result;
            }

        }

        return NO_PARENT;
    }


    @Override
    public void add(Block block) {

        if (exitOn < block.getNumber()) {
            System.out.print("Exiting after block.number: " + getBestBlock().getNumber());
            System.exit(-1);
        }


        if (!isValid(block)) {
            logger.warn("Invalid block with number: {}", block.getNumber());
            return;
        }

        track = repository.startTracking();
        if (block == null)
            return;

        // keep chain continuity
        if (!Arrays.equals(getBestBlock().getHash(),
                block.getPreviousHeaderHash())) return;

        if (block.getNumber() >= CONFIG.traceStartBlock() && CONFIG.traceStartBlock() != -1) {
            AdvancedDeviceUtils.adjustDetailedTracing(block.getNumber());
        }

        List<TransactionReceipt> receipts = processBlock(block);

        track.commit();
        storeBlock(block, receipts);


        if (!byTest && needFlush(block)) {
            repository.flush();
            blockStore.flush();
            System.gc();
        }

        // Remove all wallet transactions as they already approved by the net
        wallet.removeTransactions(block.getTransactionsList());

        listener.trace(String.format("Block chain size: [ %d ]", this.getSize()));
        listener.onBlock(block, receipts);
    }

    private boolean needFlush(Block block) {
        if (CONFIG.cacheFlushMemory() > 0) {
            return needFlushByMemory(CONFIG.cacheFlushMemory());
        } else if (CONFIG.cacheFlushBlocks() > 0) {
            return block.getNumber() % CONFIG.cacheFlushBlocks() == 0;
        } else {
            return needFlushByMemory(.7);
        }
    }

    private boolean needFlushByMemory(double maxMemoryPercents) {
        return getRuntime().freeMemory() < (getRuntime().totalMemory() * (1 - maxMemoryPercents));
    }

    private byte[] calcReceiptsTrie(List<TransactionReceipt> receipts) {
        //TODO Fix Trie hash for receipts - doesnt match cpp
        Trie receiptsTrie = new TrieImpl(null);

        if (receipts == null || receipts.isEmpty())
            return HashUtil.EMPTY_TRIE_HASH;

        for (int i = 0; i < receipts.size(); i++) {
            receiptsTrie.update(RLP.encodeInt(i), receipts.get(i).getEncoded());
        }
        return receiptsTrie.getRootHash();
    }

    private byte[] calcLogBloom(List<TransactionReceipt> receipts) {

        Bloom retBloomFilter = new Bloom();

        if (receipts == null || receipts.isEmpty())
            return retBloomFilter.getData();

        for (int i = 0; i < receipts.size(); i++) {
            retBloomFilter.or(receipts.get(i).getBloomFilter());
        }

        return retBloomFilter.getData();
    }

    public Block getParent(BlockHeader header) {

        return blockStore.getBlockByHash(header.getPreviousHeaderHash());
    }


    public boolean isValid(BlockHeader header) {

        Block parentBlock = getParent(header);

        if (!parentHeaderValidator.validate(header, parentBlock.getHeader())) {

            if (logger.isErrorEnabled())
                parentHeaderValidator.logErrors(logger);

            return false;
        }

        return true;
    }

    /**
     * This mechanism enforces a homeostasis in terms of the time between blocks;
     * a smaller period between the last two blocks results in an increase in the
     * difficulty level and thus additional computation required, lengthening the
     * likely next period. Conversely, if the period is too large, the difficulty,
     * and expected time to the next block, is reduced.
     */
    private boolean isValid(Block block) {

        boolean isValid = true;

        if (!block.isGenesis()) {
            isValid = isValid(block.getHeader());

            // Sanity checks
        }

        return isValid;
    }

    private List<TransactionReceipt> processBlock(Block block) {

        List<TransactionReceipt> receipts = new ArrayList<>();
        if (!block.isGenesis()) {
            if (!CONFIG.blockChainOnly()) {
                wallet.addTransactions(block.getTransactionsList());
                receipts = applyBlock(block);
                wallet.processBlock(block);
            }
        }

        return receipts;
    }

    private List<TransactionReceipt> applyBlock(Block block) {

        logger.info("applyBlock: block: [{}] tx.list: [{}]", block.getNumber(), block.getTransactionsList().size());
        long saveTime = System.nanoTime();
        int i = 1;
        long totalGasUsed = 0;
        List<TransactionReceipt> receipts = new ArrayList<>();

        for (Transaction tx : block.getTransactionsList()) {
            stateLogger.info("apply block: [{}] tx: [{}] ", block.getNumber(), i);

            TransactionExecutor executor = new TransactionExecutor(tx, HashUtil.ripemd160(block.getGeneratorPublicKey()),
                    track, blockStore,
                    programInvokeFactory, block, listener, totalGasUsed);

            executor.init();
            executor.execute();

            totalGasUsed += executor.getGasUsed();

            track.commit();
        }

        updateTotalDifficulty(block);

        track.commit();

        stateLogger.info("applied reward for block: [{}]  \n  state: [{}]",
                block.getNumber(),
                Hex.toHexString(repository.getRoot()));


        if (block.getNumber() >= CONFIG.traceStartBlock())
            repository.dumpState(block, totalGasUsed, 0, null);

        long totalTime = System.nanoTime() - saveTime;
        adminInfo.addBlockExecTime(totalTime);
        logger.info("block: num: [{}] hash: [{}], executed after: [{}]nano", block.getNumber(), block.getShortHash(), totalTime);

        return receipts;
    }

    @Override
    public void storeBlock(Block block, List<TransactionReceipt> receipts) {

        /* Debug check to see if the state is still as expected */
        /*
        String blockStateRootHash = Hex.toHexString(block.getStateRoot());
        String worldStateRootHash = Hex.toHexString(repository.getRoot());

        if (!SystemProperties.CONFIG.blockChainOnly())
            if (!blockStateRootHash.equals(worldStateRootHash)) {

                stateLogger.error("BLOCK: STATE CONFLICT! block: {} worldstate {} mismatch", block.getNumber(), worldStateRootHash);
//                stateLogger.error("DO ROLLBACK !!!");
                adminInfo.lostConsensus();

                System.out.println("CONFLICT: BLOCK #" + block.getNumber());
                System.exit(1);
                // in case of rollback hard move the root
//                Block parentBlock = blockStore.getBlockByHash(block.getParentHash());
//                repository.syncToRoot(parentBlock.getStateRoot());
//                return false;
            }
        */
        if (fork)
            blockStore.saveBlock(block, totalDifficulty, false);
        else
            blockStore.saveBlock(block, totalDifficulty, true);

        logger.info("Block saved: number: {}, hash: {}, TD: {}",
                block.getNumber(), block.getShortHash(), totalDifficulty);

        setBestBlock(block);

        if (logger.isDebugEnabled())
            logger.debug("block added to the blockChain: index: [{}]", block.getNumber());
        if (block.getNumber() % 100 == 0)
            logger.info("*** Last block added [ #{} ]", block.getNumber());

    }


    public boolean hasParentOnTheChain(Block block) {
        return getParent(block.getHeader()) != null;
    }

    @Override
    public List<Chain> getAltChains() {
        return altChains;
    }

    @Override
    public List<Block> getGarbage() {
        return garbage;
    }

    @Override
    public void setBestBlock(Block block) {
        bestBlock = block;
    }

    @Override
    public Block getBestBlock() {
        return bestBlock;
    }

    @Override
    public void close() {
    }

    @Override
    public BigInteger getTotalDifficulty() {
        return totalDifficulty;
    }

    @Override
    public void updateTotalDifficulty(Block block) {
//        totalDifficulty = totalDifficulty.add(block.getDifficultyBI());
        totalDifficulty = block.getCumulativeDifficulty();
        logger.info("TD: updated to {}" , totalDifficulty);
    }

    @Override
    public void setTotalDifficulty(BigInteger totalDifficulty) {
        this.totalDifficulty = totalDifficulty;
    }

    private void recordBlock(Block block) {

        if (!CONFIG.recordBlocks()) return;

        String dumpDir = CONFIG.databaseDir() + "/" + CONFIG.dumpDir();

        File dumpFile = new File(dumpDir + "/blocks-rec.dmp");
        FileWriter fw = null;
        BufferedWriter bw = null;

        try {

            dumpFile.getParentFile().mkdirs();
            if (!dumpFile.exists()) dumpFile.createNewFile();

            fw = new FileWriter(dumpFile.getAbsoluteFile(), true);
            bw = new BufferedWriter(fw);

            if (bestBlock.isGenesis()) {
                bw.write(Hex.toHexString(bestBlock.getEncoded()));
                bw.write("\n");
            }

            bw.write(Hex.toHexString(block.getEncoded()));
            bw.write("\n");

        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        } finally {
            try {
                if (bw != null) bw.close();
                if (fw != null) fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public void setProgramInvokeFactory(ProgramInvokeFactory factory) {
        this.programInvokeFactory = factory;
    }

    public void startTracking() {
        track = repository.startTracking();
    }

    public void commitTracking() {
        track.commit();
    }

    public void setExitOn(long exitOn) {
        this.exitOn = exitOn;
    }

    public boolean isBlockExist(byte[] hash) {
        return blockStore.isBlockExist(hash);
    }

    public void setParentHeaderValidator(DependentBlockHeaderRule parentHeaderValidator) {
        this.parentHeaderValidator = parentHeaderValidator;
    }

    public void setPendingState(PendingState pendingState) {
        this.pendingState = pendingState;
    }

    public PendingState getPendingState() {
        return pendingState;
    }

    @Override
    public List<BlockHeader> getListOfHeadersStartFrom(BlockIdentifier identifier, int skip, int limit, boolean reverse) {
        long blockNumber = identifier.getNumber();

        if (identifier.getHash() != null) {
            Block block = getBlockByHash(identifier.getHash());

            if (block == null) {
                return Collections.emptyList();
            }

            blockNumber = block.getNumber();
        }

        long bestNumber = bestBlock.getNumber();

        int qty = getQty(blockNumber, bestNumber, limit);

        byte[] startHash = getStartHash(blockNumber, skip, qty, reverse);

        if (startHash == null) {
            return Collections.emptyList();
        }

        List<BlockHeader> headers = blockStore.getListHeadersEndWith(startHash, qty);

        // blocks come with falling numbers
        if (!reverse) {
            Collections.reverse(headers);
        }

        return headers;
    }

    private int getQty(long blockNumber, long bestNumber, int limit) {

        if (blockNumber + limit - 1 > bestNumber) {
            return (int) (bestNumber - blockNumber + 1);
        } else {
            return limit;
        }
    }

    private byte[] getStartHash(long blockNumber, int skip, int qty, boolean reverse) {

        long startNumber;

        if (reverse) {
            startNumber = blockNumber - skip;
        } else {
            startNumber = blockNumber + skip + qty - 1;
        }

        Block block = getBlockByNumber(startNumber);

        if (block == null) {
            return null;
        }

        return block.getHash();
    }

    @Override
    public List<byte[]> getListOfBodiesByHashes(List<byte[]> hashes) {
        List<byte[]> bodies = new ArrayList<>(hashes.size());

        for (byte[] hash : hashes) {
            Block block = blockStore.getBlockByHash(hash);
            if (block == null) break;
            bodies.add(block.getEncodedBody());
        }

        return bodies;
    }
}
