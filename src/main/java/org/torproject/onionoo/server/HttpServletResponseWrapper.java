/* Copyright 2011--2017 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.server;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletResponse;

public class HttpServletResponseWrapper {

  private HttpServletResponse response = null;

  protected HttpServletResponseWrapper(HttpServletResponse response) {
    this.response = response;
  }

  protected void sendError(int errorStatusCode) throws IOException {
    this.response.sendError(errorStatusCode);
  }

  protected void setHeader(String headerName, String headerValue) {
    this.response.setHeader(headerName, headerValue);
  }

  protected void setContentType(String contentType) {
    this.response.setContentType(contentType);
  }

  protected void setCharacterEncoding(String characterEncoding) {
    this.response.setCharacterEncoding(characterEncoding);
  }

  protected PrintWriter getWriter() throws IOException {
    return this.response.getWriter();
  }
}

