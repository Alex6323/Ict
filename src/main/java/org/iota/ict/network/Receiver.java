package org.iota.ict.network;

import org.iota.ict.Ict;
import org.iota.ict.model.Tangle;
import org.iota.ict.model.Transaction;
import org.iota.ict.network.event.GossipReceiveEvent;
import org.iota.ict.utils.Constants;
import org.iota.ict.utils.Trytes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class Receiver extends Thread {
    private final Tangle tangle;
    private final Ict ict;
    private final DatagramSocket socket;
    private long roundStart;


    public Receiver(Ict ict, Tangle tangle, DatagramSocket socket) {
        super("Receiver");
        this.ict = ict;
        this.tangle = tangle;
        this.socket = socket;
    }

    @Override
    public void run() {
        roundStart = System.currentTimeMillis();
        while (ict.isRunning()) {
            manageRounds();

            DatagramPacket packet = new DatagramPacket(new byte[Constants.PACKET_SIZE], Constants.PACKET_SIZE);
            try {
                socket.receive(packet);
                processIncoming(packet);
            } catch (IOException e) {
                if (ict.isRunning())
                    e.printStackTrace();
            }
        }
    }

    private void processIncoming(DatagramPacket packet) {
        Transaction transaction = new Transaction(new String(packet.getData()));
        Neighbor sender = determineNeighborWhoSent(packet);
        Tangle.TransactionLog log = tangle.createTransactionLogIfAbsent(transaction);
        log.senders.add(sender);
        sender.stats.receivedAll++;
        processRequest(transaction, sender);
        ict.notifyListeners(new GossipReceiveEvent(transaction));
    }

    private void processRequest(Transaction transaction, Neighbor requester) {
        if (transaction.requestHash.equals(Trytes.NULL_HASH))
            return; // no transaction requested
        Transaction requested = tangle.findTransactionByHash(transaction.requestHash);
        if (requested == null)
            return; // unknown transaction
        sendRequested(requested, requester);
        // unset requestHash because it's header information and does not actually belong to the transaction
        transaction.requestHash = Trytes.NULL_HASH;
    }

    private void sendRequested(Transaction requested, Neighbor requester) {
        Tangle.TransactionLog requestedLog = tangle.findTransactionLog(requested);
        requestedLog.senders.remove(requester); // remove so requester is no longer marked as already knowing this transaction
        ict.rebroadcast(requested);
    }

    private Neighbor determineNeighborWhoSent(DatagramPacket packet) {
        for (Neighbor nb : ict.getNeighbors())
            if (nb.getAddress().equals(packet.getSocketAddress())) {
                return nb;
            }
        throw new RuntimeException("Received transaction from unknown address: " + packet.getSocketAddress());
    }

    private void manageRounds() {
        if (roundStart + ict.getProperties().roundDuration < System.currentTimeMillis()) {
            ict.logRound();
            roundStart = System.currentTimeMillis();
        }
    }
}
