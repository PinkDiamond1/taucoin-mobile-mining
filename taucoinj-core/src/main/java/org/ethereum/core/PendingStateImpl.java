package org.ethereum.core;

import org.ethereum.db.BlockStore;
import org.ethereum.listener.EthereumListener;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.inject.Singleton;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.math.BigInteger.ZERO;
import static org.ethereum.config.SystemProperties.CONFIG;
import static org.ethereum.util.BIUtil.toBI;

/**
 * Keeps logic providing pending state management
 *
 * @author Mikhail Kalinin
 * @since 28.09.2015
 */
@Singleton
public class PendingStateImpl implements PendingState {

    private static final Logger logger = LoggerFactory.getLogger("state");

    private EthereumListener listener;

    private Repository repository;

    private Blockchain blockchain;

    private BlockStore blockStore;

    private ProgramInvokeFactory programInvokeFactory;

    @Resource
    private final Set<PendingTransaction> wireTransactions = new HashSet<>();

    @Resource
    private final List<Transaction> pendingStateTransactions = new ArrayList<>();

    private Repository pendingState;

    public PendingStateImpl() {
    }

    @Inject
    public PendingStateImpl(EthereumListener listener, Repository repository,
                            BlockStore blockStore, ProgramInvokeFactory programInvokeFactory) {
        this.listener = listener;
        this.repository = repository;
        this.blockStore = blockStore;
        this.programInvokeFactory = programInvokeFactory;
    }

    @Override
    public void init() {
        this.pendingState = repository.startTracking();
    }

    @Override
    public Repository getRepository() {
        return pendingState;
    }

    @Override
    public Set<Transaction> getWireTransactions() {

        Set<Transaction> txs = new HashSet<>();

        for (PendingTransaction tx : wireTransactions) {
            txs.add(tx.getTransaction());
        }

        return txs;
    }

    @Override
    public void addWireTransactions(Set<Transaction> transactions) {

        if (transactions.isEmpty()) return;

        logger.info("Wire transaction list added: size: [{}]", transactions.size());

        listener.onPendingTransactionsReceived(transactions);

        long number = blockchain.getBestBlock().getNumber();
        for (Transaction tx : transactions) {
            if (isValid(tx)) wireTransactions.add(new PendingTransaction(tx, number));
        }
    }

    private boolean isValid(Transaction tx) {

        BigInteger txNonce = toBI(tx.getNonce());

        if (repository.isExist(tx.getSender())) {
            BigInteger currNonce = repository.getAccountState(tx.getSender()).getNonce();
            return currNonce.equals(txNonce);
        } else {
            return txNonce.equals(ZERO);
        }
    }

    @Override
    public void addPendingTransaction(Transaction tx) {
        pendingStateTransactions.add(tx);
        executeTx(tx);
    }

    @Override
    public List<Transaction> getPendingTransactions() {
        return pendingStateTransactions;
    }

    @Override
    public void processBest(Block block) {

        clearWire(block.getTransactionsList());

        clearOutdated(block.getNumber());

        clearPendingState(block.getTransactionsList());

        updateState();
    }

    private void clearOutdated(final long blockNumber) {
        List<PendingTransaction> outdated = new ArrayList<>();

        synchronized (wireTransactions) {
            for (PendingTransaction tx : wireTransactions)
                if (blockNumber - tx.getBlockNumber() > CONFIG.txOutdatedThreshold())
                    outdated.add(tx);
        }

        if (outdated.isEmpty()) return;

        if (logger.isInfoEnabled())
            for (PendingTransaction tx : outdated)
                logger.info(
                        "Clear outdated wire transaction, block.number: [{}] hash: [{}]",
                        tx.getBlockNumber(),
                        Hex.toHexString(tx.getHash())
                );

        wireTransactions.removeAll(outdated);
    }

    private void clearWire(List<Transaction> txs) {
        for (Transaction tx : txs) {
            PendingTransaction pend = new PendingTransaction(tx);

            if (logger.isInfoEnabled() && wireTransactions.contains(pend))
                logger.info("Clear wire transaction, hash: [{}]", Hex.toHexString(tx.getHash()));

            wireTransactions.remove(pend);
        }
    }

    private void clearPendingState(List<Transaction> txs) {
        if (logger.isInfoEnabled()) {
            for (Transaction tx : txs)
                if (pendingStateTransactions.contains(tx))
                    logger.info("Clear pending state transaction, hash: [{}]", Hex.toHexString(tx.getHash()));
        }

        pendingStateTransactions.removeAll(txs);
    }

    private void updateState() {

        pendingState = repository.startTracking();

        synchronized (pendingStateTransactions) {
            for (Transaction tx : pendingStateTransactions) executeTx(tx);
        }
    }

    private void executeTx(Transaction tx) {

        logger.info("Apply pending state tx: {}", Hex.toHexString(tx.getHash()));

        Block best = blockchain.getBestBlock();

        TransactionExecutor executor = new TransactionExecutor(
                tx, best.getCoinbase(), pendingState,
                blockStore, programInvokeFactory, best
        );

        executor.init();
        executor.execute();
        executor.go();
        executor.finalization();
    }

    public void setBlockchain(Blockchain blockchain) {
        this.blockchain = blockchain;
    }
}
