package com.simplehttp.catalina;

import cn.hutool.log.LogFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Set;



public class ReactorConnector extends Connector {

    private int subReactorNum = Runtime.getRuntime().availableProcessors();
    private HttpReadSelector[] subReactors;
    private Thread[] subReactorThreads;
    private int subPointer = 0;

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
                        //System.out.println("Accept: " + sc.socket().toString());
                        sc.configureBlocking(false);
                        dispatch(sc);
                    }
                }
            }

        } catch (IOException e) {
            LogFactory.get().error(e);
        }
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
