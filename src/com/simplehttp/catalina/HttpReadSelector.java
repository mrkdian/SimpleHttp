package com.simplehttp.catalina;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.LogFactory;
import com.simplehttp.http.Request;
import com.simplehttp.http.Response;
import com.simplehttp.util.ThreadPoolUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;

class HttpWorkRunner implements Runnable {
    private HttpProcessor processor;
    private HttpStateMachine stateMachine;
    private byte[] data;
    private Connector connector;
    private SocketChannel socketChannel;
    private HttpReadSelector selector;

    public HttpWorkRunner(HttpStateMachine stateMachine, byte[] data, HttpReadSelector selector, SocketChannel sc) {
        this.stateMachine = stateMachine;
        this.data = data;
        this.processor = selector.processor;
        this.connector = selector.connector;
        this.selector = selector;
        this.socketChannel = sc;
    }

    @Override
    public void run() {
        this.stateMachine.feed(data);
        while(this.stateMachine.requestBytesList.size() > 0) {
            byte[] requestBytes = stateMachine.requestBytesList.removeFirst();
            Request request = new Request(connector, requestBytes);
            Response response = new Response();
            LogFactory.get().info(StrUtil.format("{} {} {} {}",
                    request.getMethod(), request.getUri(), socketChannel.socket().toString(), selector.id));
            processor.execute(socketChannel, request, response);
        }
    }
}

public class HttpReadSelector implements Runnable{

    private Selector selector;
    private int bufSize = 1024;
    private ByteBuffer buf;
    private ByteBuffer writeBuf;

    private ByteArrayOutputStream baos;
    private LinkedList<SocketChannel> registerChannels;

    public HttpProcessor processor;
    public Connector connector;
    public int id;

    public int getRegisterNum() {
        return selector.keys().size();
    }

    public HttpReadSelector(int id, Connector connector) throws IOException {
        this.id = id;
        this.connector = connector;
        selector = Selector.open();
        buf = ByteBuffer.allocateDirect(bufSize);
        writeBuf = ByteBuffer.allocateDirect(bufSize);
        processor = new HttpProcessor();
        registerChannels = new LinkedList<>();
    }

    public void register(SocketChannel sc) throws IOException {
        sc.configureBlocking(false);
        synchronized (registerChannels) {
            registerChannels.add(sc);
        }
        selector.wakeup();
    }

    private void registerToSelector() throws IOException {
        synchronized (registerChannels) {
            while(registerChannels.size() > 0) {
                SocketChannel sc = registerChannels.removeFirst();
                SelectionKey key = sc.register(selector, SelectionKey.OP_READ);
                key.attach(new HttpStateMachine());
            }
        }
    }


    private byte[] read(SocketChannel sc, SelectionKey key) throws IOException {
        int n = 1;
        try {
            baos = new ByteArrayOutputStream();
            buf.clear();
            while(n > 0) {
                n = sc.read(buf);
                buf.flip();
                while(buf.hasRemaining()) {
                    baos.write(buf.get());
                }
                buf.clear();
            }
            if(n < 0) {
                key.cancel();
                closeSocketChannel(sc, true);
                return null;
            }
        } catch (IOException e) {
            LogFactory.get().error(e);
            key.cancel();
            closeSocketChannel(sc, false);
        }
        return baos.toByteArray();
    }

    public void closeSocketChannel(SocketChannel sc, boolean normal) throws IOException {
        InetAddress ip = ((InetSocketAddress)sc.getRemoteAddress()).getAddress();
        ReactorConnector connector = (ReactorConnector) this.connector;
        connector.removeIPRecord(ip);
        ThreadPoolUtil.removeKey(sc);
        sc.close();
        LogFactory.get().info(String.format("connect to %s %s close", ip.toString(), normal ? "normally" : "abnormally"));
    }


    @Override
    public void run() {
        while(true) {
            try {
                registerToSelector();
                int n = selector.select();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();

                while(iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    if(key.isReadable()) {
                        SocketChannel sc = (SocketChannel) key.channel();
                        byte[] data = read(sc, key);
                        if(data == null) continue;
                        HttpStateMachine stateMachine = (HttpStateMachine) key.attachment();
                        //System.out.printf("sub Reactor %d process request\n", id);
                        ThreadPoolUtil.runByOrder(new HttpWorkRunner(stateMachine, data, this, sc), sc);
                    }
                }
            } catch (IOException e) {
                LogFactory.get().error(e);
            }

        }
    }
}
