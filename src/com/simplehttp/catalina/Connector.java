package com.simplehttp.catalina;
 
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
 
import com.simplehttp.http.Request;
import com.simplehttp.http.Response;
import com.simplehttp.util.ThreadPoolUtil;
import cn.hutool.log.LogFactory;

public class Connector implements Runnable {
    int port;
    private Service service;

    private String compression;
    private int compressionMinSize;
    private String noCompressionUserAgents;
    private String compressableMimeType;
    private int keepAliveTimeout = 2 * 60 * 1000;

    public String getCompression() {
        return compression;
    }

    public void setCompression(String compression) {
        this.compression = compression;
    }

    public int getCompressionMinSize() {
        return compressionMinSize;
    }

    public void setCompressionMinSize(int compressionMinSize) {
        this.compressionMinSize = compressionMinSize;
    }

    public String getNoCompressionUserAgents() {
        return noCompressionUserAgents;
    }

    public void setNoCompressionUserAgents(String noCompressionUserAgents) {
        this.noCompressionUserAgents = noCompressionUserAgents;
    }

    public String getCompressableMimeType() {
        return compressableMimeType;
    }

    public void setCompressableMimeType(String compressableMimeType) {
        this.compressableMimeType = compressableMimeType;
    }
    public Connector(Service service) {
        this.service = service;
    }
 
    public Service getService() {
        return service;
    }
 
    public void setPort(int port) {
        this.port = port;
    }


    @Override
    public void run() {
        try {
            ServerSocket ss = new ServerSocket(port);
            while(true) {
                Socket s = ss.accept();
                //System.out.println("accept!: " + s.toString());
                s.setSoTimeout(keepAliveTimeout);
                Runnable r = new HttpReader(s, this);
                ThreadPoolUtil.run(r);
            }
        } catch (IOException e) {
            LogFactory.get().error(e);
            e.printStackTrace();
        }
    }
 
    public void init() {
        LogFactory.get().info("Initializing ProtocolHandler [http-bio-{}]", port);
    }
 
    public void start() {
        LogFactory.get().info("Starting ProtocolHandler [http-bio-{}]",port);
        new Thread(this).start();
    }
 

}