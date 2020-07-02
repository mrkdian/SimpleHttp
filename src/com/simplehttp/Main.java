package com.simplehttp;

import com.simplehttp.catalina.HttpStateMachine;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.*;

public class Main {
    @Test
    public void testHttpStateMachine() throws Exception {
        String http1 = "GET /a/sb.mother.jsp/haha HTTP/1.1\r\n" +
                "nmsl:ok\r\n\r\n";


        String http = "GET /a/sb.mother.jsp/haha HTTP/1.1\r\n" +
                "nmsl:ok\r\n\r\n" +
                "GET /a HTTP/1.1\r\n" +
                "nmsl:fdajsldas\r\nfdafda:fdafda\r\nfdadsac: xzccc\r\n" +
                "Content-Length: 5\r\nfdafda:fdf\r\n\r\nHello" +
                "GET /sugrec?pre=1&p=3&ie=utf-8&json=1&prod=pc HTTP/1.1\r\n" +
                "nmsl:fdajsldas\r\nfdafda:fdafda\r\nfdadsac: xzccc\r\n" +
                "Content-Length: 258\r\n\r\n" +
                "pre=1&p=3&ie=utf-8&json=1&prod=pc&from=pc_web&sugsid=32097,1447,31326,21118,31660,32045,30823&wd=chrome%20qi&req=2&bs=java%20byte%E5%8F%AF%E4%BB%A5%E4%B8%BA%E8%B4%9F%E5%90%97&csor=9&pwd=chrome%20q&cb=jQuery1102041145612231703277_1592508146625&_=1592508146635";

        http = http + http + http + http;
        Random r = new Random(777);
        List<Integer> sps = new ArrayList<>();
        for(int i = 0; i < 30; i++) {
            sps.add(r.nextInt(http.length()));
        }
        Collections.sort(sps);

        List<String> feeds = new ArrayList<>();
        for(int i = 0; i < sps.size(); i++) {
            if(i > 0) {
                feeds.add(http.substring(sps.get(i - 1), sps.get(i)));
            } else {
                feeds.add(http.substring(0, sps.get(i)));
            }
        }
        feeds.add(http.substring(sps.get(sps.size() - 1), http.length()));
        System.out.println(feeds.size());

        HttpStateMachine sm = new HttpStateMachine();
        HttpStateMachine smm = new HttpStateMachine();

        for(int i = 0; i < feeds.size(); i++) {
            sm.feed(feeds.get(i).getBytes());
            System.out.println(sm.requestBytesList.size());
            //System.out.println("-----------------------------------------------");
        }
        smm.feed(http.getBytes());
        //System.out.println(sm.requestBytes.size());

        String validate = "";
        Iterator<byte[]> it = sm.requestBytesList.iterator();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while(it.hasNext()) {
            byte[] bs = it.next();
            baos.write(bs);
            System.out.println(new String(bs));
            System.out.println("--------------------------");
        }
        String rec = baos.toString();

        System.out.printf("%d %d\n", sm.requestBytesList.size(), smm.requestBytesList.size());
        System.out.println(rec.equals(http));
    }

    public static void main(String[] args) throws Exception {

    }
}
