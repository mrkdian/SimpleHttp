package com.simplehttp.catalina;

import cn.hutool.core.util.StrUtil;
import cn.hutool.log.LogFactory;
import com.simplehttp.http.Request;
import com.simplehttp.http.Response;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class HttpBlockingReader implements Runnable {

    private HttpStateMachine stateMachine;
    private int readBufSize = 1024;
    private byte[] readBuf;
    private Connector connector;
    private Socket socket;

    public HttpBlockingReader(Socket s, Connector connector) {
        this.stateMachine = new HttpStateMachine();
        this.readBuf = new byte[readBufSize];
        this.connector = connector;
        this.socket = s;
    }

    @Override
    public void run() {
        try {
            HttpProcessor processor = new HttpProcessor();
            InputStream is = socket.getInputStream();
            while(true) {
                int n = is.read(readBuf);
                if(n < 0) break;
                stateMachine.feed(readBuf, 0, n);
                while(stateMachine.requestBytesList.size() > 0) {
                    byte[] requestBytes = stateMachine.requestBytesList.removeFirst();
                    Request request = new Request(connector, requestBytes);
                    Response response = new Response();
                    LogFactory.get().info(StrUtil.format("{} {} {}",
                            request.getMethod(), request.getUri(), socket.toString()));
                    processor.execute(socket, request, response);
                }
            }
        } catch (IOException e) {
            if(e instanceof SocketTimeoutException) {
                LogFactory.get().info("断开连接：" + socket.toString());
            } else {
                e.printStackTrace();
            }
            System.out.println("exception socket: " + socket.toString());
        } finally {
            if (!socket.isClosed())
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
    }
}
