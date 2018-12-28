package org.ethereum.net.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.ethereum.core.Transaction;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.net.MessageQueue;
import org.ethereum.net.client.Capability;
import org.ethereum.net.eth.handler.Eth;
import org.ethereum.net.eth.handler.EthAdapter;
import org.ethereum.net.eth.handler.EthHandler;
import org.ethereum.net.eth.handler.EthHandlerFactory;
import org.ethereum.net.eth.EthVersion;
import org.ethereum.net.eth.message.Eth60MessageFactory;
import org.ethereum.net.eth.message.Eth61MessageFactory;
import org.ethereum.net.eth.message.Eth62MessageFactory;
import org.ethereum.net.message.ReasonCode;
import org.ethereum.sync.SyncStateName;
import org.ethereum.sync.SyncStatistics;
import org.ethereum.net.message.MessageFactory;
import org.ethereum.net.message.StaticMessages;
import org.ethereum.net.p2p.HelloMessage;
import org.ethereum.net.p2p.P2pHandler;
import org.ethereum.net.p2p.P2pMessageFactory;
import org.ethereum.net.rlpx.FrameCodec;
import org.ethereum.net.rlpx.Node;
import org.ethereum.net.rlpx.discover.NodeManager;
import org.ethereum.net.rlpx.discover.NodeStatistics;
import org.ethereum.net.shh.ShhHandler;
import org.ethereum.net.shh.ShhMessageFactory;
import org.ethereum.net.swarm.bzz.BzzHandler;
import org.ethereum.net.rlpx.MessageCodec;
import org.ethereum.net.swarm.bzz.BzzMessageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import static org.ethereum.config.SystemProperties.CONFIG;
import static org.ethereum.net.eth.EthVersion.V61;
import static org.ethereum.net.eth.EthVersion.V62;

/**
 * @author Roman Mandeleil
 * @since 01.11.2014
 */
public class Channel {

    private final static Logger logger = LoggerFactory.getLogger("net");

    private MessageQueue msgQueue;

    private P2pHandler p2pHandler;

    private ShhHandler shhHandler;

    private BzzHandler bzzHandler;

    private MessageCodec messageCodec;

    private NodeManager nodeManager;

    private EthHandlerFactory ethHandlerFactory;

    private Eth eth = new EthAdapter();

    private InetSocketAddress inetSocketAddress;

    private Node node;
    private NodeStatistics nodeStatistics;

    private boolean discoveryMode;

    @Inject
    public Channel(MessageQueue msgQueue, P2pHandler p2pHandler
            , ShhHandler shhHandler, BzzHandler bzzHandler, MessageCodec messageCodec
            , NodeManager nodeManager, EthHandlerFactory ethHandlerFactory) {
        this.msgQueue = msgQueue;
        this.p2pHandler = p2pHandler;
        this.shhHandler = shhHandler;
        this.bzzHandler = bzzHandler;
        this.messageCodec = messageCodec;
        this.nodeManager = nodeManager;
        this.ethHandlerFactory = ethHandlerFactory;
    }

    public void init(ChannelPipeline pipeline, String remoteId, boolean discoveryMode) {

        pipeline.addLast("readTimeoutHandler",
                new ReadTimeoutHandler(CONFIG.peerChannelReadTimeout(), TimeUnit.SECONDS));
        pipeline.addLast("initiator", messageCodec.getInitiator());
        pipeline.addLast("messageCodec", messageCodec);

        this.discoveryMode = discoveryMode;

        if (discoveryMode) {
            // temporary key/nodeId to not accidentally smear our reputation with
            // unexpected disconnect
            messageCodec.generateTempKey();
        }

        messageCodec.setRemoteId(remoteId, this);

        p2pHandler.setMsgQueue(msgQueue);
        messageCodec.setP2pMessageFactory(new P2pMessageFactory());

        shhHandler.setMsgQueue(msgQueue);
        messageCodec.setShhMessageFactory(new ShhMessageFactory());

        bzzHandler.setMsgQueue(msgQueue);
        messageCodec.setBzzMessageFactory(new BzzMessageFactory());
    }

    public void publicRLPxHandshakeFinished(ChannelHandlerContext ctx, HelloMessage helloRemote) throws IOException, InterruptedException {
        ctx.pipeline().addLast(Capability.P2P, p2pHandler);

        p2pHandler.setChannel(this);
        p2pHandler.setHandshake(helloRemote, ctx);

        getNodeStatistics().rlpxHandshake.add();
    }

    public void sendHelloMessage(ChannelHandlerContext ctx, FrameCodec frameCodec, String nodeId) throws IOException, InterruptedException {

        // in discovery mode we are supplying fake port along with fake nodeID to not receive
        // incoming connections with fake public key
        HelloMessage helloMessage = discoveryMode ? StaticMessages.createHelloMessage(nodeId, 9) :
                StaticMessages.createHelloMessage(nodeId);

        byte[] payload = helloMessage.getEncoded();

        ByteBuf byteBufMsg = ctx.alloc().buffer();
        frameCodec.writeFrame(new FrameCodec.Frame(helloMessage.getCode(), payload), byteBufMsg);
        ctx.writeAndFlush(byteBufMsg).sync();

        if (logger.isInfoEnabled())
            logger.info("To: \t{} \tSend: \t{}", ctx.channel().remoteAddress(), helloMessage);
        getNodeStatistics().rlpxOutHello.add();
    }

    public void activateEth(ChannelHandlerContext ctx, EthVersion version) {
        EthHandler handler = ethHandlerFactory.create(version);
        MessageFactory messageFactory = createEthMessageFactory(version);
        messageCodec.setEthVersion(version);
        messageCodec.setEthMessageFactory(messageFactory);

        logger.info("Eth{} [ address = {} | id = {} ]", handler.getVersion(), inetSocketAddress, getPeerIdShort());

        ctx.pipeline().addLast(Capability.ETH, handler);

        handler.setMsgQueue(msgQueue);
        handler.setChannel(this);
        handler.setPeerDiscoveryMode(discoveryMode);

        handler.activate();

        eth = handler;
    }

    private MessageFactory createEthMessageFactory(EthVersion version) {
        switch (version) {
            case V60:   return new Eth60MessageFactory();
            case V61:   return new Eth61MessageFactory();
            case V62:   return new Eth62MessageFactory();
            default:    throw new IllegalArgumentException("Eth " + version + " is not supported");
        }
    }

    public void activateShh(ChannelHandlerContext ctx) {
        ctx.pipeline().addLast(Capability.SHH, shhHandler);
        shhHandler.activate();
    }

    public void activateBzz(ChannelHandlerContext ctx) {
        ctx.pipeline().addLast(Capability.BZZ, bzzHandler);
        bzzHandler.activate();
    }

    public void setInetSocketAddress(InetSocketAddress inetSocketAddress) {
        this.inetSocketAddress = inetSocketAddress;
    }

    public NodeStatistics getNodeStatistics() {
        return nodeStatistics;
    }

    public void setNode(byte[] nodeId) {
        node = new Node(nodeId, inetSocketAddress.getHostName(), inetSocketAddress.getPort());
        nodeStatistics = nodeManager.getNodeStatistics(node);
    }

    public Node getNode() {
        return node;
    }

    public void initMessageCodes(List<Capability> caps) {
        messageCodec.initMessageCodes(caps);
    }

    public boolean isProtocolsInitialized() {
        return eth.hasStatusPassed();
    }

    public void onDisconnect() {
    }

    public void onSyncDone() {
        eth.enableTransactions();
        eth.onSyncDone();
    }

    public boolean isDiscoveryMode() {
        return discoveryMode;
    }

    public String getPeerId() {
        return node == null ? "<null>" : node.getHexId();
    }

    public String getPeerIdShort() {
        return node == null ? "<null>" : node.getHexIdShort();
    }

    public byte[] getNodeId() {
        return node == null ? null : node.getId();
    }

    public ByteArrayWrapper getNodeIdWrapper() {
        return node == null ? null : new ByteArrayWrapper(node.getId());
    }

    public void disconnect(ReasonCode reason) {
        msgQueue.disconnect(reason);
    }

    public InetSocketAddress getInetSocketAddress() {
        return inetSocketAddress;
    }

    // ETH sub protocol

    public boolean isEthCompatible(Channel peer) {
        return peer != null && peer.getEthVersion().isCompatible(getEthVersion());
    }

    public boolean hasEthStatusSucceeded() {
        return eth.hasStatusSucceeded();
    }

    public void logSyncStats() {
        eth.logSyncStats();
    }

    public BigInteger getTotalDifficulty() {
        return nodeStatistics.getEthTotalDifficulty();
    }

    public void changeSyncState(SyncStateName newState) {
        eth.changeState(newState);
    }

    public boolean hasBlocksLack() {
        return eth.hasBlocksLack();
    }

    public void setMaxHashesAsk(int maxHashesAsk) {
        eth.setMaxHashesAsk(maxHashesAsk);
    }

    public int getMaxHashesAsk() {
        return eth.getMaxHashesAsk();
    }

    public void setLastHashToAsk(byte[] lastHashToAsk) {
        eth.setLastHashToAsk(lastHashToAsk);
    }

    public byte[] getLastHashToAsk() {
        return eth.getLastHashToAsk();
    }

    public byte[] getBestKnownHash() {
        return eth.getBestKnownHash();
    }

    public SyncStatistics getSyncStats() {
        return eth.getStats();
    }

    public boolean isHashRetrievingDone() {
        return eth.isHashRetrievingDone();
    }

    public boolean isHashRetrieving() {
        return eth.isHashRetrieving();
    }

    public boolean isIdle() {
        return eth.isIdle();
    }

    public void prohibitTransactionProcessing() {
        eth.disableTransactions();
    }

    public void sendTransaction(Transaction tx) {
        eth.sendTransaction(tx);
    }

    public EthVersion getEthVersion() {
        return eth.getVersion();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Channel channel = (Channel) o;

        if (inetSocketAddress != null ? !inetSocketAddress.equals(channel.inetSocketAddress) : channel.inetSocketAddress != null) return false;
        return !(node != null ? !node.equals(channel.node) : channel.node != null);

    }

    @Override
    public int hashCode() {
        int result = inetSocketAddress != null ? inetSocketAddress.hashCode() : 0;
        result = 31 * result + (node != null ? node.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return String.format("%s | %s", getPeerIdShort(), inetSocketAddress);
    }
}
