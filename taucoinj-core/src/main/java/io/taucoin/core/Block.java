package io.taucoin.core;

import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.SHA3Helper;
import org.ethereum.trie.Trie;
import org.ethereum.trie.TrieImpl;
import org.ethereum.core.*;
import org.ethereum.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.Arrays;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.ethereum.config.SystemProperties.CONFIG;

/**
 * The block in taucoin is the collection of relevant pieces of information
 * (known as the blockheader), H, together with information corresponding to
 * the comprised transactions, T.
 */
public class Block {

    private static final Logger logger = LoggerFactory.getLogger("block");

    private BlockHeader header;

    /*ensure the integrity of the block 512 bits*/
    private byte[] blockSignature;
    /*this is left for future use 8 bits*/
    private byte option;

    /* Transactions */
    private List<Transaction> transactionsList = new CopyOnWriteArrayList<>();

    protected byte[] rlpEncoded;
    private boolean parsed = false;

    private Trie txsState;


    /* Constructors */

    private Block() {
    }

    public Block(byte[] rawData) {
        logger.debug("new from [" + Hex.toHexString(rawData) + "]");
        this.rlpEncoded = rawData;
    }

    public Block(BlockHeader header, byte[] blockSignature,byte option,List<Transaction> transactionsList) {

        this(header.getVersion(),
                header.getTimeStamp(),
                header.getPreviousHeaderHash(),
                header.getGeneratorPublicKey(),
                blockSignature,
                option,
                transactionsList);
    }

    public Block(byte version, byte[] timestamp, byte[] previousHeaderHash, byte[] generatorPublickey,
                 byte[] blockSignature,byte option,
                 List<Transaction> transactionsList) {
        /*
        * TODO: calculate GenerationSignature
        *
         */
        this.header = new BlockHeader(version, timestamp, previousHeaderHash, generatorPublickey);

        this.transactionsList = transactionsList;
        if (this.transactionsList == null) {
            this.transactionsList = new CopyOnWriteArrayList<>();
        }

        this.parsed = true;
    }


    private void parseRLP() {

        RLPList params = RLP.decode2(rlpEncoded);
        RLPList block = (RLPList) params.get(0);

        // Parse Header
        RLPList header = (RLPList) block.get(0);
        this.header = new BlockHeader(header);
        // Parse blockSignature
        this.blockSignature =  block.get(1).getRLPData();
        // Parse option
        this.option = block.get(2).getRLPData()[0];
        // Parse Transactions
        RLPList txTransactions = (RLPList) block.get(3);
        //this.parseTxs(this.header.getTxTrieRoot(), txTransactions);

        this.parsed = true;
    }

    public BlockHeader getHeader() {
        if (!parsed) parseRLP();
        return this.header;
    }

    public byte[] getHash() {
        if (!parsed) parseRLP();
        //todo: need implementation
        return null;
    }

    public byte[] getPreviousHeaderHash() {
        if (!parsed) parseRLP();
        return this.header.getPreviousHeaderHash();
    }

    public byte[] getTimestamp() {
        if (!parsed) parseRLP();
        return this.header.getTimeStamp();
    }

    public byte getVersion() {
        if (!parsed) parseRLP();
        return this.header.getVersion();
    }

    public byte[] getblockSignature(){
        if (!parsed) parseRLP();
        return this.blockSignature;
    }

    public byte getOption() {
        if (!parsed) parseRLP();
        return this.option;
    }

    public List<Transaction> getTransactionsList() {
        if (!parsed) parseRLP();
        return transactionsList;
    }


    private StringBuffer toStringBuff = new StringBuffer();
    // [parent_hash, uncles_hash, coinbase, state_root, tx_trie_root,
    // difficulty, number, minGasPrice, gasLimit, gasUsed, timestamp,
    // extradata, nonce]

    @Override
    public String toString() {

        if (!parsed) parseRLP();

        toStringBuff.setLength(0);
        toStringBuff.append(Hex.toHexString(this.getEncoded())).append("\n");
        toStringBuff.append("BlockData [ ");
        toStringBuff.append("hash=" + ByteUtil.toHexString(this.getHash())).append("\n");
        toStringBuff.append(header.toString());
        toStringBuff.append("blocksig=" + ByteUtil.toHexString(this.blockSignature)).append("\n");
        //toStringBuff.append("option=" + ByteUtil.toHexString(this.option)).append("\n");
        toStringBuff.append("\nTransactions [\n");
        for (Transaction tx : getTransactionsList()) {
            toStringBuff.append("\n");
            toStringBuff.append(tx.toString());
        }
        toStringBuff.append("]");
        toStringBuff.append("\n]");

        return toStringBuff.toString();
    }

    public String toFlatString() {
        if (!parsed) parseRLP();

        toStringBuff.setLength(0);
        toStringBuff.append("BlockData [");
        toStringBuff.append("hash=").append(ByteUtil.toHexString(this.getHash()));
        toStringBuff.append(header.toFlatString());
        toStringBuff.append("blocksig=" + ByteUtil.toHexString(this.blockSignature));
        //toStringBuff.append("option=" + ByteUtil.toHexString(this.option));

        for (Transaction tx : getTransactionsList()) {
            toStringBuff.append("\n");
            toStringBuff.append(tx.toString());
        }

        toStringBuff.append("]");
        return toStringBuff.toString();
    }

    private void parseTxs(RLPList txTransactions) {

        this.txsState = new TrieImpl(null);
        for (int i = 0; i < txTransactions.size(); i++) {
            RLPElement transactionRaw = txTransactions.get(i);
            this.transactionsList.add(new Transaction(transactionRaw.getRLPData()));
            this.txsState.update(RLP.encodeInt(i), transactionRaw.getRLPData());
        }
    }

//    private boolean parseTxs(byte[] expectedRoot, RLPList txTransactions) {
//
//        parseTxs(txTransactions);
//        String calculatedRoot = Hex.toHexString(txsState.getRootHash());
//        if (!calculatedRoot.equals(Hex.toHexString(expectedRoot))) {
//            logger.error("Transactions trie root validation failed for block #{}", this.header.getNumber());
//            return false;
//        }
//
//        return true;
//    }

    /**
     * check if param block is son of this block
     *
     * @param block - possible a son of this
     * @return - true if this block is parent of param block
     */
//    public boolean isParentOf(Block block) {
//        return Arrays.areEqual(this.getHash(), block.getParentHash());
//    }

    public boolean isGenesis() {
        return this.header.isGenesis();
    }

//    public boolean isEqual(Block block) {
//        return Arrays.areEqual(this.getHash(), block.getHash());
//    }
    private byte[] getSigAndOptionEncoded() {
        byte[] blockSig = RLP.encodeElement(this.blockSignature);
        byte[] option = RLP.encodeByte(this.option);
        return RLP.encodeList(blockSig,option);
    }
    private byte[] getTransactionsEncoded() {

        byte[][] transactionsEncoded = new byte[transactionsList.size()][];
        int i = 0;
        for (Transaction tx : transactionsList) {
            transactionsEncoded[i] = tx.getEncoded();
            ++i;
        }
        return RLP.encodeList(transactionsEncoded);
    }

    public byte[] getEncoded() {
        if (rlpEncoded == null) {
            byte[] header = this.header.getEncoded();

            List<byte[]> block = getBodyElements();
            block.add(0, header);
            byte[][] elements = block.toArray(new byte[block.size()][]);

            this.rlpEncoded = RLP.encodeList(elements);
        }
        return rlpEncoded;
    }


    public byte[] getEncodedBody() {
        List<byte[]> body = getBodyElements();
        byte[][] elements = body.toArray(new byte[body.size()][]);
        return RLP.encodeList(elements);
    }

    private List<byte[]> getBodyElements() {
        if (!parsed) parseRLP();

        byte[] sigAndOption = getSigAndOptionEncoded();
        byte[] transactions = getTransactionsEncoded();

        List<byte[]> body = new ArrayList<>();
        body.add(sigAndOption);
        body.add(transactions);

        return body;
    }

//    public String getShortHash() {
//        if (!parsed) parseRLP();
//        return Hex.toHexString(getHash()).substring(0, 6);
//    }

    public static class Builder {

        private BlockHeader header;
        private byte[] body;

        public Builder withHeader(BlockHeader header) {
            this.header = header;
            return this;
        }

        public Builder withBody(byte[] body) {
            this.body = body;
            return this;
        }

        public Block create() {
            if (header == null || body == null) {
                return null;
            }

            Block block = new Block();
            block.header = header;
            block.parsed = true;
            block.blockSignature = RLP.decode2(body).get(0).getRLPData();
            block.option = RLP.decode2(body).get(1).getRLPData()[0];
            RLPList transactions = (RLPList) RLP.decode2(body).get(2);
            //RLPList transactions = (RLPList) items.get(0);

//            if (!block.parseTxs(header.getTxTrieRoot(), transactions)) {
//                return null;
//            }
            //TODO:decodeRLPList---->List<Transactions>
            //we avoid trie,because we think block header doesn't have large capacity

            return block;
        }
    }
}
