/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ResourceServlet extends HttpServlet {

  private static final long serialVersionUID = 7236658979947465319L;

  private boolean maintenanceMode = false;

  /* Called by servlet container, not by test class. */
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    boolean maintenanceMode =
        config.getInitParameter("maintenance") != null
        && config.getInitParameter("maintenance").equals("1");
    File outDir = new File(config.getInitParameter("outDir"));
    Time time = new Time();
    DocumentStore documentStore = new DocumentStore(outDir, time);
    this.init(maintenanceMode, documentStore, time);
  }

  /* Called (indirectly) by servlet container and (directly) by test
   * class. */
  protected void init(boolean maintenanceMode,
      DocumentStore documentStore, Time time) {
    this.maintenanceMode = maintenanceMode;
    if (!maintenanceMode) {
      ResponseBuilder.initialize(documentStore, time);
    }
  }

  public long getLastModified(HttpServletRequest request) {
    if (this.maintenanceMode) {
      return super.getLastModified(request);
    } else {
      return ResponseBuilder.getLastModified();
    }
  }

  protected static class HttpServletRequestWrapper {
    private HttpServletRequest request;
    protected HttpServletRequestWrapper(HttpServletRequest request) {
      this.request = request;
    }
    protected String getRequestURI() {
      return this.request.getRequestURI();
    }
    protected Map getParameterMap() {
      return this.request.getParameterMap();
    }
    protected String[] getParameterValues(String parameterKey) {
      return this.request.getParameterValues(parameterKey);
    }
  }

  protected static class HttpServletResponseWrapper {
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

  public void doGet(HttpServletRequest request,
      HttpServletResponse response) throws IOException, ServletException {
    HttpServletRequestWrapper requestWrapper =
        new HttpServletRequestWrapper(request);
    HttpServletResponseWrapper responseWrapper =
        new HttpServletResponseWrapper(response);
    this.doGet(requestWrapper, responseWrapper);
  }

  public void doGet(HttpServletRequestWrapper request,
      HttpServletResponseWrapper response) throws IOException {

    if (this.maintenanceMode) {
      response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
      return;
    }

    if (!ResponseBuilder.update()) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    ResponseBuilder rb = new ResponseBuilder();
    String uri = request.getRequestURI();
    if (uri.startsWith("/onionoo/")) {
      uri = uri.substring("/onionoo".length());
    }
    String resourceType = null;
    if (uri.startsWith("/summary")) {
      resourceType = "summary";
    } else if (uri.startsWith("/details")) {
      resourceType = "details";
    } else if (uri.startsWith("/bandwidth")) {
      resourceType = "bandwidth";
    } else if (uri.startsWith("/weights")) {
      resourceType = "weights";
    } else {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    rb.setResourceType(resourceType);

    /* Extract parameters either from the old-style URI or from request
     * parameters. */
    Map<String, String> parameterMap = new HashMap<String, String>();
    for (Object parameterKey : request.getParameterMap().keySet()) {
      String[] parameterValues =
          request.getParameterValues((String) parameterKey);
      parameterMap.put((String) parameterKey, parameterValues[0]);
    }

    /* Make sure that the request doesn't contain any unknown
     * parameters. */
    Set<String> knownParameters = new HashSet<String>(Arrays.asList((
        "type,running,search,lookup,country,as,flag,first_seen_days,"
        + "last_seen_days,contact,order,limit,offset,fields").
        split(",")));
    for (String parameterKey : parameterMap.keySet()) {
      if (!knownParameters.contains(parameterKey)) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
    }

    /* Filter relays and bridges matching the request. */
    if (parameterMap.containsKey("type")) {
      String typeParameterValue = parameterMap.get("type").toLowerCase();
      boolean relaysRequested = true;
      if (typeParameterValue.equals("bridge")) {
        relaysRequested = false;
      } else if (!typeParameterValue.equals("relay")) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rb.setType(relaysRequested ? "relay" : "bridge");
    }
    if (parameterMap.containsKey("running")) {
      String runningParameterValue =
          parameterMap.get("running").toLowerCase();
      boolean runningRequested = true;
      if (runningParameterValue.equals("false")) {
        runningRequested = false;
      } else if (!runningParameterValue.equals("true")) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rb.setRunning(runningRequested ? "true" : "false");
    }
    if (parameterMap.containsKey("search")) {
      String[] searchTerms = this.parseSearchParameters(
          parameterMap.get("search"));
      if (searchTerms == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rb.setSearch(searchTerms);
    }
    if (parameterMap.containsKey("lookup")) {
      String fingerprintParameter = this.parseFingerprintParameter(
          parameterMap.get("lookup"));
      if (fingerprintParameter == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      String fingerprint = fingerprintParameter.toUpperCase();
      rb.setLookup(fingerprint);
    }
    if (parameterMap.containsKey("country")) {
      String countryCodeParameter = this.parseCountryCodeParameter(
          parameterMap.get("country"));
      if (countryCodeParameter == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rb.setCountry(countryCodeParameter);
    }
    if (parameterMap.containsKey("as")) {
      String aSNumberParameter = this.parseASNumberParameter(
          parameterMap.get("as"));
      if (aSNumberParameter == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rb.setAs(aSNumberParameter);
    }
    if (parameterMap.containsKey("flag")) {
      String flagParameter = this.parseFlagParameter(
          parameterMap.get("flag"));
      if (flagParameter == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rb.setFlag(flagParameter);
    }
    if (parameterMap.containsKey("first_seen_days")) {
      int[] days = this.parseDaysParameter(
          parameterMap.get("first_seen_days"));
      if (days == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rb.setFirstSeenDays(days);
    }
    if (parameterMap.containsKey("last_seen_days")) {
      int[] days = this.parseDaysParameter(
          parameterMap.get("last_seen_days"));
      if (days == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rb.setLastSeenDays(days);
    }
    if (parameterMap.containsKey("contact")) {
      String[] contactParts = this.parseContactParameter(
          parameterMap.get("contact"));
      if (contactParts == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rb.setContact(contactParts);
    }

    /* Re-order and limit results. */
    if (parameterMap.containsKey("order")) {
      String orderParameter = parameterMap.get("order").toLowerCase();
      String orderByField = orderParameter;
      if (orderByField.startsWith("-")) {
        orderByField = orderByField.substring(1);
      }
      if (!orderByField.equals("consensus_weight")) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rb.setOrder(new String[] { orderParameter });
    }
    if (parameterMap.containsKey("offset")) {
      String offsetParameter = parameterMap.get("offset");
      if (offsetParameter.length() > 6) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      try {
        Integer.parseInt(offsetParameter);
      } catch (NumberFormatException e) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rb.setOffset(offsetParameter);
    }
    if (parameterMap.containsKey("limit")) {
      String limitParameter = parameterMap.get("limit");
      if (limitParameter.length() > 6) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      try {
        Integer.parseInt(limitParameter);
      } catch (NumberFormatException e) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rb.setLimit(limitParameter);
    }

    /* Possibly include only a subset of fields in the response
     * document. */
    String[] fields = null;
    if (parameterMap.containsKey("fields")) {
      fields = this.parseFieldsParameter(parameterMap.get("fields"));
      if (fields == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rb.setFields(fields);
    }

    /* Set response headers and write the response. */
    response.setHeader("Access-Control-Allow-Origin", "*");
    response.setContentType("application/json");
    response.setCharacterEncoding("utf-8");
    PrintWriter pw = response.getWriter();
    rb.buildResponse(pw);
    pw.flush();
    pw.close();
  }

  private static Pattern searchParameterPattern =
      Pattern.compile("^\\$?[0-9a-fA-F]{1,40}$|" /* Fingerprint. */
      + "^[0-9a-zA-Z\\.]{1,19}$|" /* Nickname or IPv4 address. */
      + "^\\[[0-9a-fA-F:\\.]{1,39}\\]?$"); /* IPv6 address. */
  private String[] parseSearchParameters(String parameter) {
    String[] searchParameters;
    if (parameter.contains(" ")) {
      searchParameters = parameter.split(" ");
    } else {
      searchParameters = new String[] { parameter };
    }
    for (String searchParameter : searchParameters) {
      if (!searchParameterPattern.matcher(searchParameter).matches()) {
        return null;
      }
    }
    return searchParameters;
  }

  private static Pattern fingerprintParameterPattern =
      Pattern.compile("^[0-9a-zA-Z]{1,40}$");
  private String parseFingerprintParameter(String parameter) {
    if (!fingerprintParameterPattern.matcher(parameter).matches()) {
      return null;
    }
    if (parameter.length() != 40) {
      return null;
    }
    return parameter;
  }

  private static Pattern countryCodeParameterPattern =
      Pattern.compile("^[0-9a-zA-Z]{2}$");
  private String parseCountryCodeParameter(String parameter) {
    if (!countryCodeParameterPattern.matcher(parameter).matches()) {
      return null;
    }
    return parameter;
  }

  private static Pattern aSNumberParameterPattern =
      Pattern.compile("^[asAS]{0,2}[0-9]{1,10}$");
  private String parseASNumberParameter(String parameter) {
    if (!aSNumberParameterPattern.matcher(parameter).matches()) {
      return null;
    }
    return parameter;
  }

  private static Pattern flagPattern =
      Pattern.compile("^[a-zA-Z0-9]{1,20}$");
  private String parseFlagParameter(String parameter) {
    if (!flagPattern.matcher(parameter).matches()) {
      return null;
    }
    return parameter;
  }

  private static Pattern daysPattern = Pattern.compile("^[0-9-]{1,10}$");
  private int[] parseDaysParameter(String parameter) {
    if (!daysPattern.matcher(parameter).matches()) {
      return null;
    }
    int x = 0, y = Integer.MAX_VALUE;
    try {
      if (!parameter.contains("-")) {
        x = y = Integer.parseInt(parameter);
      } else {
        String[] parts = parameter.split("-", 2);
        if (parts[0].length() > 0) {
          x = Integer.parseInt(parts[0]);
        }
        if (parts.length > 1 && parts[1].length() > 0) {
          y = Integer.parseInt(parts[1]);
        }
      }
    } catch (NumberFormatException e) {
      return null;
    }
    if (x > y) {
      return null;
    }
    return new int[] { x, y };
  }

  private String[] parseContactParameter(String parameter) {
    for (char c : parameter.toCharArray()) {
      if (c < 32 || c >= 127) {
        return null;
      }
    }
    return parameter.split(" ");
  }

  private static Pattern fieldsParameterPattern =
      Pattern.compile("^[0-9a-zA-Z_,]*$");
  private String[] parseFieldsParameter(String parameter) {
    if (!fieldsParameterPattern.matcher(parameter).matches()) {
      return null;
    }
    return parameter.split(",");
  }
}

