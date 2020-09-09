package com.simplehttp;

import cn.hutool.core.io.FileUtil;
import com.simplehttp.catalina.HttpStateMachine;
import org.apache.tools.ant.taskdefs.Classloader;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Driver;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

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

    @Test
    public void sequentialThree() throws Exception {
        ReentrantLock lock12 = new ReentrantLock();
        Condition c12 = lock12.newCondition();
        ReentrantLock lock23 = new ReentrantLock();
        Condition c23 = lock12.newCondition();
        ReentrantLock lock31 = new ReentrantLock();
        Condition c31 = lock12.newCondition();

        Semaphore s12 = new Semaphore(0);
        Semaphore s23 = new Semaphore(0);
        Semaphore s31 = new Semaphore(1);

        AtomicInteger count = new AtomicInteger(0);

        CyclicBarrier cb = new CyclicBarrier(3);

        CountDownLatch cd1 = new CountDownLatch(1);
        CountDownLatch cd2 = new CountDownLatch(1);



        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean first = true;
                lock12.lock();
                try {
                    while(count.get() < 100) {
                        if(first) {
                            first = false;
                        } else {
                            c31.await();
                        }
                        cd1.countDown();
                        count.incrementAndGet();
                        System.out.println(Thread.currentThread().getName() + " " + count.get());
                        c12.signalAll();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }).start();

        cd1.await();

        new Thread(new Runnable() {
            @Override
            public void run() {
                lock12.lock();
                try {
                    boolean first = true;
                    while(count.get() < 100) {
                        if(first) {
                            first = false;
                        } else {
                            c12.await();
                        }
                        cd2.countDown();
                        count.incrementAndGet();
                        System.out.println(Thread.currentThread().getName() + " " + count.get());
                        c23.signalAll();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }).start();

        cd2.await();

        new Thread(new Runnable() {
            @Override
            public void run() {
                lock12.lock();
                try {
                    boolean first = true;
                    while(count.get() < 100) {
                        if(first) {
                            first = false;
                        } else {
                            c23.await();
                        }
                        count.incrementAndGet();
                        System.out.println(Thread.currentThread().getName() + " " + count.get());
                        c31.signalAll();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }).start();

    }

    static class VolatileExample {
        int x = 0 ;
        volatile boolean v = false;
        public void writer(){
            x = 42;
            v = true;
        }

        public int reader(){
            int count = 0;
            while(true) {
                if(v) {
                    return 123;
                } else {
                    count++;
                    //System.out.println("false");
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {

        System.out.println(test());
    }

    public static Integer test() {
        try {
            int a = 5 / 0;
            return null;
        } catch (Exception e) {

        }
        return 9;
    }
}