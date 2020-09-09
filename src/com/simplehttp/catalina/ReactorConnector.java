package com.simplehttp.catalina;

import cn.hutool.core.collection.ConcurrentHashSet;
import cn.hutool.log.LogFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class ReactorConnector extends Connector {

    private int subReactorNum = Runtime.getRuntime().availableProcessors();
    private HttpReadSelector[] subReactors;
    private Thread[] subReactorThreads;
    private int subPointer = 0;

    private ConcurrentHashMap<InetAddress, SocketChannel> connectedIP = new ConcurrentHashMap<>();

    public ReactorConnector(Service service) {
        super(service);
    }

    @Override
    public void run() {
        try {
            ServerSocketChannel ssc = ServerSocketChannel.open();
            ssc.bind(new InetSocketAddress(port));
            ssc.configureBlocking(false);

            Selector selector = Selector.open();
            ssc.register(selector, SelectionKey.OP_ACCEPT);

            while(true) {
                int n = selector.select();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();

                while(iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    if(key.isAcceptable()) {
                        SocketChannel sc = ssc.accept();
                        InetAddress ip = ((InetSocketAddress)sc.getRemoteAddress()).getAddress();
                        if(connectedIP.containsKey(ip)) {
                            SocketChannel osc = connectedIP.get(ip);
                            if(!osc.socket().isClosed()) {
                                // the connect must close exclusive. may lead to race condition, not good
                                sc.close();
                                continue;
                            }
                        }
                        connectedIP.put(ip, sc);
                        System.out.println("Accept: " + sc.socket().toString());
                        sc.configureBlocking(false);
                        dispatch(sc);
                        ((InetSocketAddress) sc.getRemoteAddress()).getAddress();
                        InetSocketAddress c;
                    }
                }
            }

        } catch (IOException e) {
            LogFactory.get().error(e);
        }
    }

    synchronized public void removeIPRecord(InetAddress ip) {
        this.connectedIP.remove(ip);
    }


    synchronized public void dispatch(SocketChannel sc) throws IOException {
        //System.out.println("register to sub Reactor " + subPointer);
        subReactors[subPointer].register(sc);
        subPointer = (subPointer + 1) % subReactorNum;
    }

    @Override
    public void init() {
        try {
            subReactors = new HttpReadSelector[subReactorNum];
            subReactorThreads = new Thread[subReactorNum];
            for(int i = 0; i < subReactorNum; i++) {
                subReactors[i] = new HttpReadSelector(i,this);
                subReactorThreads[i] = new Thread(subReactors[i]);
                subReactorThreads[i].start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        LogFactory.get().info("Initializing ProtocolHandler [http-reactor-{}]", port);
    }

    @Override
    public void start() {
        LogFactory.get().info("Starting ProtocolHandler [http-reactor-{}]",port);
        new Thread(this).start();
    }
}
