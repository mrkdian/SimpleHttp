package com.simplehttp.catalina;

import com.simplehttp.http.Request;
import com.simplehttp.http.Response;
import com.simplehttp.servlets.DefaultServlet;
import com.simplehttp.servlets.InvokerServlet;
import com.simplehttp.util.Constant;
import com.simplehttp.util.SessionManager;
import cn.hutool.core.util.*;
import cn.hutool.log.LogFactory;

import javax.servlet.Filter;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class HttpProcessor {
    public static void writeToChannel(SocketChannel sc, ByteBuffer buf) throws IOException {
        while(buf.remaining() > 0) {
            sc.write(buf);
        }
    }
    public void execute(SocketChannel sc, Request request, Response response) {
        try {
            String uri = request.getUri();
            if(null == uri)
                return;

            prepareSession(request, response);

            Context context = request.getContext();
            String servletClassName = context.getServletClassName(uri);
            HttpServlet workingServlet;

            if(null != servletClassName)
                workingServlet = InvokerServlet.getInstance();
            else
                workingServlet = DefaultServlet.getInstance();

            workingServlet.service(request, response);

            if(request.isForwarded() || sc.socket().isClosed())
                return;


            byte[] responseBytes;
            if(Constant.CODE_200 == response.getStatus()) {
                responseBytes = handle200(request, response);
                writeToChannel(sc, ByteBuffer.wrap(responseBytes));
            }
            if(Constant.CODE_302 == response.getStatus()){
                responseBytes = handle302(response);
                writeToChannel(sc, ByteBuffer.wrap(responseBytes));
            }
            if(Constant.CODE_404 == response.getStatus()){
                responseBytes = handle404(uri);
                writeToChannel(sc, ByteBuffer.wrap(responseBytes));
            }

        } catch (Exception e) {
            LogFactory.get().error(e);
            byte[] responseBytes = handle500(e);
            try {
                if(!sc.socket().isClosed())
                    writeToChannel(sc, ByteBuffer.wrap(responseBytes));
            } catch (IOException e1) {
                LogFactory.get().error(e);
            }
        }
    }

    public void execute(Socket s, Request request, Response response){
        try {
            String uri = request.getUri();
            if(null == uri)
                return;

            prepareSession(request, response);

            Context context = request.getContext();
            String servletClassName = context.getServletClassName(uri);
            HttpServlet workingServlet;
            if(null != servletClassName)
                workingServlet = InvokerServlet.getInstance();
            else
                workingServlet = DefaultServlet.getInstance();

            workingServlet.service(request, response);

            if(request.isForwarded() || s.isClosed())
                return;

            byte[] responseBytes;
            if(Constant.CODE_200 == response.getStatus()) {
                responseBytes = handle200(request, response);
                s.getOutputStream().write(responseBytes);
            }

            if(Constant.CODE_302 == response.getStatus()){
                responseBytes = handle302(response);
                s.getOutputStream().write(responseBytes);
            }
            if(Constant.CODE_404 == response.getStatus()){
                responseBytes = handle404(uri);
                s.getOutputStream().write(responseBytes);
            }
        } catch (Exception e) {
            LogFactory.get().error(e);
            byte[] responseBytes = handle500(e);
            try {
                s.getOutputStream().write(responseBytes);
            } catch (IOException e1) {
                LogFactory.get().error(e);
            }
        }
    }
    private byte[] handle200(Request request, Response response)
            throws IOException {


        String contentType = response.getContentType();

        byte[] body = response.getBody();
        String cookiesHeader = response.getCookiesHeader();

        boolean gzip = isGzip(request, body, contentType);

        String headText;
        if (gzip)
            headText = Constant.response_head_200_gzip;
        else
            headText = Constant.response_head_200;

        //headText = StrUtil.format(headText, contentType, cookiesHeader);

        if (gzip)
            body = ZipUtil.gzip(body);
        headText = StrUtil.format(headText, contentType, cookiesHeader, body.length);

        byte[] head = headText.getBytes();
        byte[] responseBytes = new byte[head.length + body.length];
        ArrayUtil.copy(head, 0, responseBytes, 0, head.length);
        ArrayUtil.copy(body, 0, responseBytes, head.length, body.length);

        return responseBytes;
        //os.close();

    }

    private byte[] handle404(String uri) throws IOException {
        String responseText = StrUtil.format(Constant.textFormat_404, uri, uri);
        responseText = Constant.response_head_404 + responseText;
        byte[] responseByte = responseText.getBytes("utf-8");
        return responseByte;
    }

    private byte[] handle302(Response response) throws IOException {
        String redirectPath = response.getRedirectPath();
        String head_text = Constant.response_head_302;
        String header = StrUtil.format(head_text, redirectPath);
        byte[] responseBytes = header.getBytes("utf-8");
        return responseBytes;
    }

    private byte[] handle500(Exception e) {
        StackTraceElement stes[] = e.getStackTrace();
        StringBuffer sb = new StringBuffer();
        sb.append(e.toString());
        sb.append("\r\n");
        for (StackTraceElement ste : stes) {
            sb.append("\t");
            sb.append(ste.toString());
            sb.append("\r\n");
        }

        String msg = e.getMessage();

        if (null != msg && msg.length() > 20)
            msg = msg.substring(0, 19);

        String text = StrUtil.format(Constant.textFormat_500, msg, e.toString(), sb.toString());
        text = Constant.response_head_500 + text;
        byte[] responseBytes = text.getBytes(StandardCharsets.UTF_8);
        return responseBytes;
    }
    public void prepareSession(Request request, Response response) {
        String jsessionid = request.getJSessionIdFromCookie();
        HttpSession session = SessionManager.getSession(jsessionid, request, response);
        request.setSession(session);
    }

    private boolean isGzip(Request request, byte[] body, String mimeType) {
        String acceptEncodings=  request.getHeader("Accept-Encoding");
        if(!StrUtil.containsAny(acceptEncodings, "gzip"))
            return false;

        Connector connector = request.getConnector();

        if (mimeType.contains(";"))
            mimeType = StrUtil.subBefore(mimeType, ";", false);

        if (!"on".equals(connector.getCompression()))
            return false;

        if (body.length < connector.getCompressionMinSize())
            return false;

        String userAgents = connector.getNoCompressionUserAgents();
        String[] eachUserAgents = userAgents.split(",");
        for (String eachUserAgent : eachUserAgents) {
            eachUserAgent = eachUserAgent.trim();
            String userAgent = request.getHeader("User-Agent");
            if (StrUtil.containsAny(userAgent, eachUserAgent))
                return false;
        }

        String mimeTypes = connector.getCompressableMimeType();
        String[] eachMimeTypes = mimeTypes.split(",");
        for (String eachMimeType : eachMimeTypes) {
            if (mimeType.equals(eachMimeType))
                return true;
        }

        return false;
    }
}
