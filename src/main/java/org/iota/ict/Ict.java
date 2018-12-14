package org.iota.ict;

import org.iota.ict.model.Tangle;
import org.iota.ict.model.TransactionBuilder;
import org.iota.ict.network.Neighbor;
import org.iota.ict.network.event.GossipEvent;
import org.iota.ict.network.event.GossipListener;
import org.iota.ict.network.event.GossipSentEvent;
import org.iota.ict.network.Receiver;
import org.iota.ict.network.Sender;
import org.iota.ict.model.Transaction;
import org.iota.ict.utils.Constants;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.List;

public class Ict {
    private final List<GossipListener> listeners = new LinkedList<>();
    private final List<Neighbor> neighbors = new LinkedList<>();
    private final Sender sender;
    private final Receiver receiver;
    private State state;
    private Tangle tangle;
    private final Properties properties;
    private final DatagramSocket socket;
    private final InetSocketAddress address;
    public final long timeStarted = System.currentTimeMillis();

    public Ict(Properties properties) {
        this.properties = properties;
        this.tangle = new Tangle(this);
        this.address = new InetSocketAddress(properties.host, properties.port);

        try {
            this.socket = new DatagramSocket(address);
        } catch (SocketException socketException) {
            throw new RuntimeException(socketException);
        }

        this.sender = new Sender(this, properties, tangle, socket);
        this.receiver = new Receiver(this, tangle, socket);
        state = new StateRunning();
        sender.start();
        receiver.start();
    }

    /**
     * Opens a new connection to a neighbor. Both nodes will directly gossip transactions.
     *
     * @param neighborAddress address of neighbor to connect to.
     * @throws IllegalStateException if already has {@link Constants#MAX_NEIGHBOR_COUNT} neighbors.
     */
    public void neighbor(InetSocketAddress neighborAddress) {
        if (neighbors.size() > Constants.MAX_NEIGHBOR_COUNT)
            throw new IllegalStateException("Already reached maximum amount of neighbors.");
        neighbors.add(new Neighbor(neighborAddress));
    }

    /**
     * Adds a listener to this object. Every {@link GossipEvent} will be passed on to the listener.
     *
     * @param gossipListener The listener to add.
     */
    public void addGossipListener(GossipListener gossipListener) {
        listeners.add(gossipListener);
    }

    /**
     * @return the address of this node. Required by other nodes to neighbor.
     */
    public InetSocketAddress getAddress() {
        return address;
    }

    /**
     * @return A list containing all neighbors. This list is a copy: manipulating it directly will have no effects.
     */
    public List<Neighbor> getNeighbors() {
        return new LinkedList<>(neighbors);
    }

    public Properties getProperties() {
        return properties;
    }

    public Tangle getTangle() {
        return tangle;
    }

    /**
     * Submits a new message to the protocol. The message will be packaged as a Transaction and sent to all neighbors.
     *
     * @return Hash of sent transaction.
     */
    public Transaction submit(String asciiMessage) {
        TransactionBuilder builder = new TransactionBuilder();
        builder.asciiMessage(asciiMessage);
        Transaction transaction = builder.build();
        submit(transaction);
        return transaction;
    }

    /**
     * Submits a new transaction to the protocol. It will be sent to all neighbors.
     */
    public void submit(Transaction transaction) {
        tangle.createTransactionLogIfAbsent(transaction);
        sender.queueTransaction(transaction);
        notifyListeners(new GossipSentEvent(transaction));
    }

    public void rebroadcast(Transaction transaction) {
        sender.queueTransaction(transaction);
    }

    public void notifyListeners(GossipEvent event) {
        for (GossipListener listener : listeners)
            listener.on(event);
    }

    public void request(String requestedHash) {
        sender.request(requestedHash);
    }

    /**
     * @return Whether the Ict node is currently active/running.
     */
    public boolean isRunning() {
        return state instanceof StateRunning;
    }

    public void logRound() {
        for (Neighbor neighbor : neighbors) {
            Neighbor.Stats stats = neighbor.stats;
            System.out.println(stats.receivedAll + "/" + stats.receivedNew + "/" + stats.receivedInvalid + "[" + stats.prevReceivedAll + "/" + stats.prevReceivedNew + "/" + stats.prevReceivedInvalid + "]    " + neighbor.getAddress());
        }
    }

    public void terminate() {
        state.terminate();
        // TODO block until terminated
    }

    private class State {
        private final String name;

        private State(String name) {
            this.name = name;
        }

        private void illegalState(String actionName) {
            throw new IllegalStateException("Action '" + actionName + "' cannot be performed from state '" + name + "'.");
        }

        void terminate() {
            illegalState("terminate");
        }
    }

    private class StateRunning extends State {
        private StateRunning() {
            super("running");
        }

        @Override
        void terminate() {
            state = new StateTerminating();
            socket.close();
            sender.terminate();
            receiver.interrupt();
        }
    }

    private class StateTerminating extends State {
        private StateTerminating() {
            super("terminating");
        }
    }
}
