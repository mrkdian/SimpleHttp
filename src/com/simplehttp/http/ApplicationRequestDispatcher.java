package com.simplehttp.http;

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import com.simplehttp.catalina.HttpProcessor;

public class ApplicationRequestDispatcher implements RequestDispatcher {

	private String uri;
	public ApplicationRequestDispatcher(String uri) {
		if(!uri.startsWith("/")) 
			uri = "/" + uri;
		this.uri = uri;
	}
	
	@Override
	public void forward(ServletRequest servletRequest, ServletResponse servletResponse) throws ServletException, IOException {
		Request request = (Request) servletRequest;
		Response response = (Response) servletResponse;

		request.setUri(uri);
		
		HttpProcessor processor = new HttpProcessor();
		processor.execute(request.getSocket(), request, response);
		request.setForwarded(true);

	}

	@Override
	public void include(ServletRequest arg0, ServletResponse arg1) throws ServletException, IOException {
		// TODO Auto-generated method stub
		
	}

}
