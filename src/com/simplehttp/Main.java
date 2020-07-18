package com.simplehttp;

import cn.hutool.core.io.FileUtil;
import com.simplehttp.catalina.HttpStateMachine;
import org.apache.tools.ant.taskdefs.Classloader;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Driver;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    class Heap {
        int cap;
        int[] data;
        int limit;
        Heap(int cap) {
            this.cap = cap;
            this.limit = 0;
            this.data = new int[cap];
        }

        int size() {
            return limit;
        }

        void add(int x) {
            data[limit] = x;
            limit++;
            fixup(limit - 1);
        }

        int peek() {
            return data[0];
        }

        int poll() {
            int ret = data[0];
            data[0] = data[limit - 1];
            limit--;
            fixdown();
            return ret;
        }

        void fixdown() {
            int p = 0;
            while(true) {
                int l = p * 2 + 1; int r = p * 2 + 2;
                int chose;
                if(r >= limit && l < limit) chose = l;
                else if(l >= limit && r < limit) chose = r;
                else if(r >= limit && l >= limit) chose = -1;
                else chose = data[l] < data[r] ? l: r;

                if(chose == -1) break;
                if(data[chose] > data[p]) {
                    int t = data[p];
                    data[p] = data[chose];
                    data[chose] = t;
                    p = chose;
                } else break;
            }
        }

        void fixup(int p) {
            int feather = (p - 1) / 2;
            while(feather > 0) {
                if(data[feather] > data[p]) {
                    int t = data[feather];
                    data[feather] = data[p];
                    data[p] = feather;
                    p = feather;
                    feather = (p - 1) / 2;
                } else break;
            }
        }
    }



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
        ServiceLoader<Driver> matcher = ServiceLoader.load(java.sql.Driver.class, new MyClassLoader());
        Iterator<Driver> matcherIter = matcher.iterator();
        while (matcherIter.hasNext()) {
            Driver wordMatcher = matcherIter.next();
            System.out.println(wordMatcher.getClass().getName());
        }
    }
}

class MyClassLoader extends URLClassLoader {

    public MyClassLoader() {
        super(new URL[] {}, Thread.currentThread().getContextClassLoader());

        try {
            File jdbc = new File("webapps/j2ee/WEB-INF/lib/mysql-connector-java-5.0.8-bin.jar");
            URL url = new URL("file:" + jdbc.getAbsolutePath());
            System.out.println(jdbc.getAbsolutePath());
            System.out.println(jdbc.exists());
            this.addURL(url);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
