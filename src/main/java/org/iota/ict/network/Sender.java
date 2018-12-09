package org.iota.ict.network;

import org.iota.ict.Ict;
import org.iota.ict.Properties;
import org.iota.ict.model.Tangle;
import org.iota.ict.model.Transaction;
import org.iota.ict.network.event.GossipListener;
import org.iota.ict.network.event.GossipReceiveEvent;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

public class Sender extends Thread {
    private final Tangle tangle;
    private final Properties properties;
    private final Ict ict;
    private final SendingTaskQueue queue = new SendingTaskQueue();
    private final DatagramSocket socket;


    public Sender(Ict ict, Properties properties, final Tangle tangle, DatagramSocket socket) {
        super("Sender");
        this.ict = ict;
        this.tangle = tangle;
        this.properties = properties;
        this.socket = socket;

        ict.addGossipListener(new GossipListener() {
            @Override
            public void onReceiveTransaction(GossipReceiveEvent e) {
                if(tangle.findTransactionLog(e.getTransaction()).senders.size() == 1)
                    queueTransaction(e.getTransaction());
            }
        });
    }

    @Override
    public void run() {
        while (ict.isRunning()) {
            if (!queue.isEmpty() && queue.peek().sendingTime <= System.currentTimeMillis()) {
                sendTransaction(queue.poll().transaction);
            } else {
                try {
                    // keep queue.isEmpty() within the synchronized block so notify is not called after the empty check and before queue.wait()
                    synchronized (queue) {
                        queue.wait(queue.isEmpty() ? 0 : Math.max(1, queue.peek().sendingTime - System.currentTimeMillis()));
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendTransaction(Transaction transaction) {
        Tangle.TransactionLog transactionLog = tangle.findTransactionLog(transaction);
        for (Neighbor nb : ict.getNeighbors())
            if (transactionLog == null || !transactionLog.senders.contains(nb))
                sendTransactionToNeighbor(nb, transaction);
    }

    private void sendTransactionToNeighbor(Neighbor nb, Transaction transaction) {
        try {
            DatagramPacket packet = transaction.toDatagramPacket();
            packet.setSocketAddress(nb.getAddress());
            socket.send(packet);
        } catch (Exception e) {
            // TODO more advanced handling
            e.printStackTrace();
        }
    }

    public void terminate() {
        if (ict.isRunning())
            throw new IllegalStateException("Cannot terminate: Ict is still running.");
        synchronized (queue) {
            queue.notify();
        }
    }

    public void queueTransaction(Transaction transaction) {
        long forwardDelay = properties.minForwardDelay + ThreadLocalRandom.current().nextLong(properties.maxForwardDelay - properties.minForwardDelay);
        queue.add(new SendingTask(System.currentTimeMillis() + forwardDelay, transaction));
        synchronized (queue) {
            queue.notify();
        }
    }

    private static class SendingTask {

        private final long sendingTime;
        private final Transaction transaction;

        private SendingTask(long sendingTime, Transaction transaction) {
            this.sendingTime = sendingTime;
            this.transaction = transaction;
        }
    }

    private static class SendingTaskQueue extends PriorityBlockingQueue<SendingTask> {
        private SendingTaskQueue() {
            super(1, new Comparator<SendingTask>() {
                @Override
                public int compare(SendingTask task1, SendingTask task2) {
                    return Long.compare(task1.sendingTime, task2.sendingTime);
                }
            });
        }
    }
}
