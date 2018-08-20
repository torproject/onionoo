/* Copyright 2011--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.server;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
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
  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    this.maintenanceMode = config.getInitParameter("maintenance") != null
        && config.getInitParameter("maintenance").equals("1");
  }

  private static final long INDEX_WAITING_TIME = 10L * 1000L;

  @Override
  public long getLastModified(HttpServletRequest request) {
    if (this.maintenanceMode) {
      return super.getLastModified(request);
    } else {
      return NodeIndexerFactory.getNodeIndexer().getLastIndexed(
          INDEX_WAITING_TIME);
    }
  }

  @Override
  public void doGet(HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    HttpServletRequestWrapper requestWrapper =
        new HttpServletRequestWrapper(request);
    HttpServletResponseWrapper responseWrapper =
        new HttpServletResponseWrapper(response);
    this.doGet(requestWrapper, responseWrapper);
  }

  private static final long CACHE_MIN_TIME = 5L * 60L * 1000L;

  private static final long CACHE_MAX_TIME = 45L * 60L * 1000L;

  private static final long CACHE_INTERVAL = 5L * 60L * 1000L;

  private static Set<String> knownParameters = new HashSet<>(
      Arrays.asList("type", "running", "search", "lookup", "fingerprint",
          "country", "as", "as_name", "flag", "first_seen_days",
          "last_seen_days", "contact", "order", "limit", "offset", "fields",
          "family", "version", "os", "host_name", "recommended_version"));

  private static Set<String> illegalSearchQualifiers =
      new HashSet<>(Arrays.asList(("search,fingerprint,order,limit,"
          + "offset,fields").split(",")));

  private static String ipv6AddressPatternString =
      "^\\[?[0-9a-fA-F:.]{1,39}\\]?$";

  private static Pattern ipv6AddressPattern =
      Pattern.compile(ipv6AddressPatternString);

  public void doGet(HttpServletRequestWrapper request,
      HttpServletResponseWrapper response) throws IOException {
    doGet(request, response, System.currentTimeMillis());
  }

  /** Handles the HTTP GET request in the wrapped <code>request</code> by
   * writing an HTTP GET response to the likewise <code>response</code>,
   * both of which are wrapped to facilitate testing. */
  @SuppressWarnings("checkstyle:variabledeclarationusagedistance")
  public void doGet(HttpServletRequestWrapper request,
      HttpServletResponseWrapper response, long receivedRequestMillis)
      throws IOException {

    if (this.maintenanceMode) {
      response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
      return;
    }

    NodeIndex nodeIndex = NodeIndexerFactory.getNodeIndexer()
        .getLatestNodeIndex(INDEX_WAITING_TIME);
    if (nodeIndex == null) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }
    String uri = request.getRequestURI();
    if (uri.startsWith("/onionoo/")) {
      uri = uri.substring("/onionoo".length());
    }
    String resourceType;
    if (uri.startsWith("/summary")) {
      resourceType = "summary";
    } else if (uri.startsWith("/details")) {
      resourceType = "details";
    } else if (uri.startsWith("/bandwidth")) {
      resourceType = "bandwidth";
    } else if (uri.startsWith("/weights")) {
      resourceType = "weights";
    } else if (uri.startsWith("/clients")) {
      resourceType = "clients";
    } else if (uri.startsWith("/uptime")) {
      resourceType = "uptime";
    } else {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    RequestHandler rh = new RequestHandler(nodeIndex);
    rh.setResourceType(resourceType);

    /* Extract parameters either from the old-style URI or from request
     * parameters. */
    Map<String, String> parameterMap = new HashMap<>();
    for (String parameterKey : request.getParameterMap().keySet()) {
      String[] parameterValues =
          request.getParameterValues(parameterKey);
      parameterMap.put(parameterKey, parameterValues[0]);
    }

    /* Make sure that the request doesn't contain any unknown
     * parameters. */
    for (String parameterKey : parameterMap.keySet()) {
      if (!knownParameters.contains(parameterKey)) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
    }

    /* Filter relays and bridges matching the request. */
    if (parameterMap.containsKey("search")) {
      String[] searchTerms = parseSearchParameters(
          request.getQueryString());
      if (searchTerms == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      List<String> unqualifiedSearchTerms = new ArrayList<>();
      for (String searchTerm : searchTerms) {
        if (searchTerm.contains(":")) {
          String[] parts = searchTerm.split(":", 2);
          String parameterKey = parts[0];
          if (!knownParameters.contains(parameterKey)
              || illegalSearchQualifiers.contains(parameterKey)) {
            if (ipv6AddressPattern.matcher(parameterKey).matches()) {
              unqualifiedSearchTerms.add(searchTerm);
            } else {
              response.sendError(HttpServletResponse.SC_BAD_REQUEST);
              return;
            }
          }
          if (!parameterMap.containsKey(parameterKey)) {
            String parameterValue = parts[1];
            if (parameterValue.startsWith("\"")
                && parameterValue.endsWith("\"")) {
              parameterValue = parameterValue
                  .substring(1, parameterValue.length() - 1)
                  .replaceAll("\\\\\"", "\"");
            }
            parameterMap.put(parameterKey, parameterValue);
          }
        } else {
          unqualifiedSearchTerms.add(searchTerm);
        }
      }
      rh.setSearch(unqualifiedSearchTerms.toArray(
          new String[unqualifiedSearchTerms.size()]));
    }
    if (parameterMap.containsKey("type")) {
      String typeParameterValue = parameterMap.get("type").toLowerCase();
      boolean relaysRequested = true;
      if (typeParameterValue.equals("bridge")) {
        relaysRequested = false;
      } else if (!typeParameterValue.equals("relay")) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rh.setType(relaysRequested ? "relay" : "bridge");
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
      rh.setRunning(runningRequested ? "true" : "false");
    }
    if (parameterMap.containsKey("lookup")) {
      String[] lookupParameter = this.parseFingerprintParameter(
          parameterMap.get("lookup"));
      if (lookupParameter == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rh.setLookup(lookupParameter);
    }
    if (parameterMap.containsKey("fingerprint")) {
      String[] fingerprintParameter = this.parseFingerprintParameter(
          parameterMap.get("fingerprint"));
      if (null == fingerprintParameter || 1 != fingerprintParameter.length) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rh.setFingerprint(fingerprintParameter[0]);
    }
    if (parameterMap.containsKey("country")) {
      String countryCodeParameter = this.parseCountryCodeParameter(
          parameterMap.get("country"));
      if (countryCodeParameter == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rh.setCountry(countryCodeParameter);
    }
    if (parameterMap.containsKey("as")) {
      String[] asNumberParameter = this.parseAsNumberParameter(
          parameterMap.get("as"));
      if (asNumberParameter == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rh.setAs(asNumberParameter);
    }
    if (parameterMap.containsKey("as_name")) {
      String[] asNameParameter = this.parseAsNameParameter(
          parameterMap.get("as_name"));
      if (null == asNameParameter) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rh.setAsName(asNameParameter);
    }
    if (parameterMap.containsKey("flag")) {
      String flagParameter = this.parseFlagParameter(
          parameterMap.get("flag"));
      if (flagParameter == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rh.setFlag(flagParameter);
    }
    if (parameterMap.containsKey("first_seen_days")) {
      int[] days = this.parseDaysParameter(
          parameterMap.get("first_seen_days"));
      if (days == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rh.setFirstSeenDays(days);
    }
    if (parameterMap.containsKey("last_seen_days")) {
      int[] days = this.parseDaysParameter(
          parameterMap.get("last_seen_days"));
      if (days == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rh.setLastSeenDays(days);
    }
    if (parameterMap.containsKey("contact")) {
      String[] contactParts = this.parseContactParameter(
          parameterMap.get("contact"));
      if (contactParts == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rh.setContact(contactParts);
    }
    if (parameterMap.containsKey("version")) {
      String versionParameter = this.parseVersionParameter(
          parameterMap.get("version"));
      if (null == versionParameter) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rh.setVersion(versionParameter);
    }
    if (parameterMap.containsKey("os")) {
      String osParameter = this.parseOperatingSystemParameter(
              parameterMap.get("os"));
      if (null == osParameter) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rh.setOperatingSystem(osParameter);
    }
    if (parameterMap.containsKey("host_name")) {
      String hostNameParameter = this.parseHostNameParameter(
          parameterMap.get("host_name"));
      if (null == hostNameParameter) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rh.setHostName(hostNameParameter);
    }
    if (parameterMap.containsKey("recommended_version")) {
      String recommendedVersionParameterValue =
          parameterMap.get("recommended_version").toLowerCase();
      boolean recommendedVersionRequested = true;
      if (recommendedVersionParameterValue.equals("false")) {
        recommendedVersionRequested = false;
      } else if (!recommendedVersionParameterValue.equals("true")) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rh.setRecommendedVersion(recommendedVersionRequested);
    }
    if (parameterMap.containsKey("order")) {
      String[] order = this.parseOrderParameter(parameterMap.get("order"));
      if (order == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rh.setOrder(order);
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
      rh.setOffset(offsetParameter);
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
      rh.setLimit(limitParameter);
    }
    if (parameterMap.containsKey("family")) {
      String[] familyParameter = this.parseFingerprintParameter(
          parameterMap.get("family"));
      if (null == familyParameter || 1 != familyParameter.length) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rh.setFamily(familyParameter[0]);
    }
    rh.handleRequest();
    long parsedRequestMillis = System.currentTimeMillis();

    ResponseBuilder rb = new ResponseBuilder();
    rb.setResourceType(resourceType);
    rb.setRelaysPublishedString(rh.getRelaysPublishedString());
    rb.setBridgesPublishedString(rh.getBridgesPublishedString());
    rb.setOrderedRelays(rh.getOrderedRelays());
    rb.setOrderedBridges(rh.getOrderedBridges());
    rb.setRelaysSkipped(rh.getRelaysSkipped());
    rb.setBridgesSkipped(rh.getBridgesSkipped());
    rb.setRelaysTruncated(rh.getRelaysTruncated());
    rb.setBridgesTruncated(rh.getBridgesTruncated());
    String[] fields;
    if (parameterMap.containsKey("fields")) {
      fields = this.parseFieldsParameter(parameterMap.get("fields"));
      if (fields == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      rb.setFields(fields);
    }

    long indexWrittenMillis =
        NodeIndexerFactory.getNodeIndexer().getLastIndexed(
        INDEX_WAITING_TIME);
    long indexAgeMillis = receivedRequestMillis - indexWrittenMillis;
    long cacheMaxAgeMillis = Math.max(CACHE_MIN_TIME,
        ((CACHE_MAX_TIME - indexAgeMillis)
        / CACHE_INTERVAL) * CACHE_INTERVAL);

    response.setHeader("Access-Control-Allow-Origin", "*");
    response.setContentType("application/json");
    response.setCharacterEncoding("utf-8");
    response.setHeader("Cache-Control", "public, max-age="
        + (cacheMaxAgeMillis / 1000L));
    try (PrintWriter pw = response.getWriter()) {
      rb.buildResponse(pw);
    }
    int relayDocumentsWritten = rh.getOrderedRelays().size();
    int bridgeDocumentsWritten = rh.getOrderedBridges().size();
    int charsWritten = rb.getCharsWritten();
    long writtenResponseMillis = System.currentTimeMillis();
    PerformanceMetrics.logStatistics(receivedRequestMillis, resourceType,
        parameterMap.keySet(), parsedRequestMillis, relayDocumentsWritten,
        bridgeDocumentsWritten, charsWritten, writtenResponseMillis);
  }

  private static Pattern searchQueryStringPattern =
      Pattern.compile("(?:.*[?&])*?" // lazily skip other parameters
          + "search=([\\p{Graph} &&[^&]]+)" // capture parameter
          + "(?:&.*)*"); // skip remaining parameters

  private static Pattern searchParameterPattern =
      Pattern.compile("^\\$?[0-9a-fA-F]{1,40}$|" /* Hex fingerprint. */
      + "^[0-9a-zA-Z+/]{1,27}$|" /* Base64 fingerprint. */
      + "^[0-9a-zA-Z.]{1,19}$|" /* Nickname or IPv4 address. */
      + ipv6AddressPatternString + "|" /* IPv6 address. */
      + "^[a-zA-Z_]+:\"?[\\p{Graph} ]+\"?$"); /* Qualified search term. */

  protected static String[] parseSearchParameters(String queryString) {
    Matcher searchQueryStringMatcher = searchQueryStringPattern.matcher(
        queryString);
    if (!searchQueryStringMatcher.matches()) {
      /* Search query contains illegal character(s). */
      return null;
    }
    String parameter = searchQueryStringMatcher.group(1);
    String[] spaceSeparatedParts =
        parameter.replaceAll("%20", " ").split(" ");
    List<String> searchParameters = new ArrayList<>();
    StringBuilder doubleQuotedSearchTerm = null;
    for (String spaceSeparatedPart : spaceSeparatedParts) {
      if ((StringUtils.countMatches(spaceSeparatedPart, '"')
          - StringUtils.countMatches(spaceSeparatedPart, "\\\"")) % 2 == 0) {
        if (null == doubleQuotedSearchTerm) {
          searchParameters.add(spaceSeparatedPart);
        } else {
          doubleQuotedSearchTerm.append(' ').append(spaceSeparatedPart);
        }
      } else {
        if (null == doubleQuotedSearchTerm) {
          doubleQuotedSearchTerm = new StringBuilder(spaceSeparatedPart);
        } else {
          doubleQuotedSearchTerm.append(' ').append(spaceSeparatedPart);
          searchParameters.add(doubleQuotedSearchTerm.toString());
          doubleQuotedSearchTerm = null;
        }
      }
    }
    if (null != doubleQuotedSearchTerm) {
      /* Opening double quote is not followed by closing double quote. */
      return null;
    }
    for (String searchParameter : searchParameters) {
      if (!searchParameterPattern.matcher(searchParameter).matches()) {
        /* Illegal search term. */
        return null;
      }
    }
    return searchParameters.toArray(new String[0]);
  }

  private static Pattern fingerprintParameterPattern =
      Pattern.compile("((^|,)[0-9a-zA-Z]{40})+$");

  private String[] parseFingerprintParameter(String parameter) {
    if (!fingerprintParameterPattern.matcher(parameter).matches()) {
      /* Fingerprint contains non-hex character(s). */
      return null;
    }
    return parameter.toUpperCase().split(",");
  }

  private static Pattern countryCodeParameterPattern =
      Pattern.compile("^[0-9a-zA-Z]{2}$");

  private String parseCountryCodeParameter(String parameter) {
    if (!countryCodeParameterPattern.matcher(parameter).matches()) {
      /* Country code contains illegal characters or is shorter/longer
       * than 2 characters. */
      return null;
    }
    return parameter;
  }

  private static Pattern asNumberParameterPattern =
      Pattern.compile("((^|,)([aA][sS])?0*[0-9]{1,10})+$");

  private String[] parseAsNumberParameter(String parameter) {
    if (!asNumberParameterPattern.matcher(parameter).matches()) {
      /* AS number contains illegal character(s). */
      return null;
    }
    String[] parameterParts = parameter.toUpperCase().split(",");
    String[] parsedParameter = new String[parameterParts.length];
    for (int i = 0; i < parameterParts.length; i++) {
      boolean asPrefix = parameterParts[i].startsWith("AS");
      Long asNumber = Long.parseLong(asPrefix
          ? parameterParts[i].substring(2) : parameterParts[i]);
      if (asNumber > 4294967295L) {
        /* AS number was too large */
        return null;
      }
      parsedParameter[i] = "AS" + asNumber.toString();
    }
    return parsedParameter;
  }

  private String[] parseAsNameParameter(String parameter) {
    for (char c : parameter.toCharArray()) {
      if (c < 32 || c >= 127) {
        /* Only accept printable ASCII. */
        return null;
      }
    }
    return parameter.toLowerCase().split(" ");
  }

  private static Pattern flagPattern =
      Pattern.compile("^[a-zA-Z0-9]{1,20}$");

  private String parseFlagParameter(String parameter) {
    if (!flagPattern.matcher(parameter).matches()) {
      /* Flag contains illegal character(s). */
      return null;
    }
    return parameter;
  }

  private static Pattern daysPattern = Pattern.compile("^[0-9-]{1,10}$");

  private int[] parseDaysParameter(String parameter) {
    if (!daysPattern.matcher(parameter).matches()) {
      /* Days contain illegal character(s). */
      return null;
    }
    int fromDays = 0;
    int toDays = Integer.MAX_VALUE;
    try {
      if (!parameter.contains("-")) {
        fromDays = Integer.parseInt(parameter);
        toDays = fromDays;
      } else {
        String[] parts = parameter.split("-", 2);
        if (parts[0].length() > 0) {
          fromDays = Integer.parseInt(parts[0]);
        }
        if (parts.length > 1 && parts[1].length() > 0) {
          toDays = Integer.parseInt(parts[1]);
        }
      }
    } catch (NumberFormatException e) {
      /* Invalid format. */
      return null;
    }
    if (fromDays > toDays) {
      /* Second number or days must exceed first number. */
      return null;
    }
    return new int[] { fromDays, toDays };
  }

  private String[] parseContactParameter(String parameter) {
    for (char c : parameter.toCharArray()) {
      if (c < 32 || c >= 127) {
        /* Only accept printable ASCII. */
        return null;
      }
    }
    return parameter.split(" ");
  }

  private static Pattern orderParameterPattern =
      Pattern.compile("^[0-9a-zA-Z_,-]*$");

  private static HashSet<String> knownOrderParameters = new HashSet<>(
      Arrays.asList(OrderParameterValues.CONSENSUS_WEIGHT_ASC,
          OrderParameterValues.CONSENSUS_WEIGHT_DES,
          OrderParameterValues.FIRST_SEEN_ASC,
          OrderParameterValues.FIRST_SEEN_DES));

  private String[] parseOrderParameter(String parameter) {
    if (!orderParameterPattern.matcher(parameter).matches()) {
      /* Orders contain illegal character(s). */
      return null;
    }
    String[] orderParameters = parameter.toLowerCase().split(",");
    Set<String> seenOrderParameters = new HashSet<>();
    for (String orderParameter : orderParameters) {
      if (!knownOrderParameters.contains(orderParameter)) {
        /* Unknown order parameter. */
        return null;
      }
      if (!seenOrderParameters.add(orderParameter.startsWith("-")
          ? orderParameter.substring(1) : orderParameter)) {
        /* Duplicate parameter. */
        return null;
      }
    }
    return orderParameters;
  }

  private static Pattern fieldsParameterPattern =
      Pattern.compile("^[0-9a-zA-Z_,]*$");

  private String[] parseFieldsParameter(String parameter) {
    if (!fieldsParameterPattern.matcher(parameter).matches()) {
      /* Fields contain illegal character(s). */
      return null;
    }
    return parameter.toLowerCase().split(",");
  }

  private static Pattern versionParameterPattern =
      Pattern.compile("^[0-9a-zA-Z.-]+$");

  private String parseVersionParameter(String parameter) {
    if (!versionParameterPattern.matcher(parameter).matches()) {
      /* Version contains illegal character(s). */
      return null;
    }
    return parameter;
  }

  private String parseOperatingSystemParameter(String parameter) {
    for (char c : parameter.toCharArray()) {
      if (c < 32 || c >= 127) {
        /* Only accept printable ASCII. */
        return null;
      }
    }
    return parameter.toLowerCase();
  }

  private static Pattern hostNameParameterPattern =
      Pattern.compile("^[0-9A-Za-z_.\\-]+$");

  private String parseHostNameParameter(String parameter) {
    if (!hostNameParameterPattern.matcher(parameter).matches()) {
      /* Host name contains illegal character(s). */
      return null;
    }
    return parameter;
  }
}

