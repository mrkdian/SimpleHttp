package com.simplehttp;

import com.simplehttp.catalina.Server;

public class Bootstrap {
    public static void main(String[] args) throws Exception {
        Server s = new Server();
        s.start();
    }
}