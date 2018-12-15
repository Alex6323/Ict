package org.iota.ict.ixi.rmi;

import org.iota.ict.Ict;
import org.iota.ict.model.Transaction;
import org.iota.ict.network.event.GossipListener;
import org.iota.ict.network.event.GossipReceiveEvent;
import org.iota.ict.network.event.GossipSubmitEvent;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.LinkedList;
import java.util.List;

public class RemoteIctImplementation extends UnicastRemoteObject implements RemoteIct {

    static {
        try {
            LocateRegistry.createRegistry(1099);
        } catch (RemoteException e) { }
    }

    private final List<RemoteIxiModule> ixiModules = new LinkedList<>();
    private final String name = "ict";
    private final Ict ict;

    public RemoteIctImplementation(final Ict ict) throws RemoteException {
        this.ict = ict;
        try {
            Naming.rebind("//localhost/"+name, this);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        ict.addGossipListener(new GossipListener() {
            @Override
            public void onTransactionSubmitted(GossipSubmitEvent event) {
                for(RemoteIxiModule ixiModule : ixiModules)
                    try {
                        ixiModule.onTransactionSubmitted(event);
                    } catch (RemoteException e) {}
            }

            @Override
            public void onTransactionReceived(GossipReceiveEvent event) {
                for(RemoteIxiModule ixiModule : ixiModules)
                    try {
                        ixiModule.onTransactionReceived(event);
                    } catch (RemoteException e) {}
            }
        });
    }

    public void connectToIxi(String name) {
        try {
            RemoteIxiModule ixiModule = (RemoteIxiModule) Naming.lookup("//localhost/"+name);
            ixiModules.add(ixiModule);
            ixiModule.onIctConnect("ict");
        } catch (MalformedURLException | NotBoundException | RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Transaction submit(String asciiMessage) {
        return ict.submit(asciiMessage);
    }

    @Override
    public void submit(Transaction transaction) {
        ict.submit(transaction);
    }

    public void terminate() {
        // TODO
    }

    @Override
    public Transaction findTransactionByHash(String hash) {
        return ict.getTangle().findTransactionByHash(hash);
    }
}