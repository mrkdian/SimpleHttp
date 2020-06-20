package com.simplehttp.catalina;

import cn.hutool.log.LogFactory;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class HttpStateMachine {
    static public byte space = " ".getBytes()[0];
    static public byte[] HTTPSymbol = "HTTP/1.1\r\n".getBytes();
    static public byte[] splitor = "\r\n\r\n".getBytes();
    static public byte headerSplitor = ":".getBytes()[0];
    static public byte[] contentLengthBytes = "Content-Length".getBytes();
    static public byte zero = "0".getBytes()[0];

    static public boolean byteArrEqual(byte[] buf1, byte[] buf2, int beg1, int beg2, int len) {
        int end1 = buf1.length;
        int end2 = buf2.length;
        int i = 0;
        for(; i < len && beg1 + i < end1 && beg2 + i < end2; i++) {
            if(buf1[beg1 + i] != buf2[beg2 + i]) return false;
        }
        return i == len;
    }


    public LinkedList<byte[]> requestBytesList;
    private byte state1;
    private short state2;
    private short state3;
    private byte state4;

    //private int begPos;
    private int contentLength;
    private ByteArrayOutputStream baos;

    public HttpStateMachine() {
        baos = new ByteArrayOutputStream();
        requestBytesList = new LinkedList<>();
    }

    public int feed(byte[] buf) {
        return feed(buf, 0, buf.length);
    }

    public int feed(byte[] buf, int offset, int len) {
        int begPos = 0;
        int i = offset; int l = len;
        for(; i < l; i++) {
//            String s = new String(buf, i, 1);
//            if(s.startsWith("\r")) s = "\\r";
//            if(s.startsWith("\n")) s = "\\n";
//            System.out.printf("%d %s %d %d %d %d %d %d\n",
//                    i, s, state1, state2, state3, state4, contentLength, requestBytesList.size());
            switch (state1) {
                case 0:
                    switch (state2) {
                        case 0:
                            if(buf[i] == space) {
                                state2++;
                                state3 = 0;
                            } else {
                                state3++;
                            }
                            break;
                        case 1:
                            if(buf[i] == space) {
                                state2++;
                                state3 = 0;
                            } else {
                                state3++;
                            }
                            break;
                        case 2://仅支持http/1.1
                            if(HTTPSymbol[state3] == buf[i]) {
                                state3++;
                            } else {
                                state2 = 0; state3 = 0;
                            }
                            if(state3 == HTTPSymbol.length) {
                                state1 = 1; state2 = 0; state3 = 0; state4 = 0;
                            }
                            break;
                    }
                    break;
                case 1:
                    switch (state2) {
                        case 0:
                            if(buf[i] == headerSplitor) {
                                state2++; state3 = 0;
                            } else if(buf[i] == contentLengthBytes[state3]) {
                                state3++;
                            }  else {
                                state3 = contentLengthBytes[0] == buf[i] ? (short)1: (short)0;
                            }
                            if(state3 == contentLengthBytes.length) {
                                state4 = 1; contentLength = 0;
                            }
                            break;
                        case 1:
                            if(state3 < splitor.length && buf[i] == splitor[state3]) {
                                state3++;
                            } else {
                                if(state4 == 1) {
                                    int diff = buf[i] - zero;
                                    if(diff >= 0 && diff < 10) contentLength = contentLength * 10 + diff;
                                }
                                if(state3 == 2) { //匹配\r\n
                                    state2 = 0; state3 = 0; state4 = 0; i--;
                                } else if(state3 == 4) { //匹配\r\n\r\n
                                    state1 = 2; state2 = 0; state3 = 0; state4 = 0; i--;
                                }
                            }
                            break;
                    }
                    break;
                case 2:
                    if(state2 < contentLength) {
                        state2++;
                    } else {
                        //已经解析完了一个完整的请求
                        baos.write(buf, begPos, i - begPos);
                        requestBytesList.add(baos.toByteArray());
                        baos = new ByteArrayOutputStream();
                        begPos = i;
                        state1 = 0; state2 = 0; state3 = 0; state4 = 0; contentLength = 0;
                        i--;
                    }
                    break;
            }
        }
        if(begPos < i) {
            //System.out.printf("%d %d %d\n", begPos, i, buf.length);
            baos.write(buf, begPos, i - begPos);
        }
        if((state1 == 2 && state2 == contentLength) ||
                (state1 == 1 && state3 == 4 && contentLength == 0)
        ) {
            requestBytesList.add(baos.toByteArray());
            baos = new ByteArrayOutputStream();
            state1 = 0; state2 = 0; state3 = 0; state4 = 0;
        }
        return requestBytesList.size();
    }
}
