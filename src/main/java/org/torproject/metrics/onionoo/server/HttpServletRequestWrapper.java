/* Copyright 2011--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.server;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

public class HttpServletRequestWrapper {

  private HttpServletRequest request;

  protected HttpServletRequestWrapper(HttpServletRequest request) {
    this.request = request;
  }

  @SuppressWarnings("abbreviationaswordinname")
  protected String getRequestURI() {
    return this.request.getRequestURI();
  }

  protected Map<String, String[]> getParameterMap() {
    return this.request.getParameterMap();
  }

  protected String[] getParameterValues(String parameterKey) {
    return this.request.getParameterValues(parameterKey);
  }

  protected String getQueryString() {
    return this.request.getQueryString();
  }
}

