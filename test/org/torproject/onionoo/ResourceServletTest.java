/* Copyright 2013 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.Before;
import org.junit.Test;
import org.torproject.onionoo.ResourceServlet.HttpServletRequestWrapper;
import org.torproject.onionoo.ResourceServlet.HttpServletResponseWrapper;

import com.google.gson.Gson;

/* TODO This test class could (should?) be split into ResponseBuilderTest
 * which tests ResponseBuilder and a much shorter ResourceServletTest
 * which tests servlet specifics. */
public class ResourceServletTest {

  private SortedMap<String, String> relays, bridges;

  private long currentTimeMillis = DateTimeHelper.parse(
      "2013-04-24 12:22:22");

  private class TestingHttpServletRequestWrapper
      extends HttpServletRequestWrapper {
    private String requestURI;
    private Map<String, String[]> parameterMap;
    private TestingHttpServletRequestWrapper(String requestURI,
        Map<String, String[]> parameterMap) {
      super(null);
      this.requestURI = requestURI;
      this.parameterMap = parameterMap == null
          ? new HashMap<String, String[]>() : parameterMap;
    }
    protected String getRequestURI() {
      return this.requestURI;
    }
    protected Map getParameterMap() {
      return this.parameterMap;
    }
    protected String[] getParameterValues(String parameterKey) {
      return this.parameterMap.get(parameterKey);
    }
  }

  private class TestingHttpServletResponseWrapper extends
      HttpServletResponseWrapper {
    private TestingHttpServletResponseWrapper() {
      super(null);
    }
    private int errorStatusCode;
    protected void sendError(int errorStatusCode) throws IOException {
      this.errorStatusCode = errorStatusCode;
    }
    private Map<String, String> headers = new HashMap<String, String>();
    protected void setHeader(String headerName, String headerValue) {
      this.headers.put(headerName, headerValue);
    }
    protected void setContentType(String contentType) {
    }
    protected void setCharacterEncoding(String characterEncoding) {
    }
    private StringWriter stringWriter;
    protected PrintWriter getWriter() throws IOException {
      if (this.stringWriter == null) {
        this.stringWriter = new StringWriter();
        return new PrintWriter(this.stringWriter);
      } else {
        throw new IOException("Can only request writer once");
      }
    }
    private String getWrittenContent() {
      return this.stringWriter == null ? null
          : this.stringWriter.toString();
    }
  }

  private TestingHttpServletRequestWrapper request;

  private TestingHttpServletResponseWrapper response;

  private String responseString;

  private SummaryDocument summaryDocument;

  @Before
  public void createSampleRelaysAndBridges() {
    this.relays = new TreeMap<String, String>();
    this.relays.put("000C5F55BD4814B917CC474BD537F1A3B33CCE2A",
        "r\tTorkaZ\t000C5F55BD4814B917CC474BD537F1A3B33CCE2A\t"
        + "62.216.201.221;;62.216.201.222+62.216.201.223\t"
        + "2013-04-19\t05:00:00\t9001\t0\tRunning,Valid\t20\tde\tnull\t"
        + "-1\treject\t1-65535\t2013-04-18\t05:00:00\t"
        + "2013-04-19\t05:00:00\tAS8767\ttorkaz <klaus dot zufall at "
        + "gmx dot de> <fb-token:np5_g_83jmf=>");
    this.relays.put("001C13B3A55A71B977CA65EC85539D79C653A3FC",
        "r\tFerrari458\t001C13B3A55A71B977CA65EC85539D79C653A3FC\t"
        + "68.38.171.200;[2001:4f8:3:2e::51]:9001;\t"
        + "2013-04-24\t12:00:00\t9001\t9030\t"
        + "Fast,Named,Running,V2Dir,Valid\t1140\tus\t"
        + "c-68-38-171-200.hsd1.pa.comcast.net\t1366805763009\treject\t"
        + "1-65535\t2013-02-12\t16:00:00\t2013-02-26\t18:00:00\t"
        + "AS7922\t");
    this.relays.put("0025C136C1F3A9EEFE2AE3F918F03BFA21B5070B",
        "r\tTimMayTribute\t0025C136C1F3A9EEFE2AE3F918F03BFA21B5070B\t"
        + "89.69.68.246;;\t2013-04-22\t20:00:00\t9001\t9030\t"
        + "Fast,Running,Unnamed,V2Dir,Valid\t63\ta1\tnull\t-1\treject\t"
        + "1-65535\t2013-04-16\t18:00:00\t2013-04-16\t18:00:00\t"
        + "AS6830\t1024D/51E2A1C7 steven j. murdoch "
        + "<tor+steven.murdoch@cl.cam.ac.uk> <fb-token:5sr_k_zs2wm=>");
    this.bridges = new TreeMap<String, String>();
    this.bridges.put("0000831B236DFF73D409AD17B40E2A728A53994F",
        "b\tec2bridgercc7f31fe\t"
        + "0000831B236DFF73D409AD17B40E2A728A53994F\t10.199.7.176;;\t"
        + "2013-04-21\t18:07:03\t443\t0\tValid\t-1\t??\tnull\t-1\t"
        + "null\tnull\t2013-04-20\t15:37:04\tnull\tnull\tnull\tnull");
    this.bridges.put("0002D9BDBBC230BD9C78FF502A16E0033EF87E0C",
        "b\tUnnamed\t0002D9BDBBC230BD9C78FF502A16E0033EF87E0C\t"
        + "10.0.52.84;;\t2013-04-20\t17:37:04\t443\t0\tValid\t-1\t??\t"
        + "null\t-1\tnull\tnull\t2013-04-14\t07:07:05\tnull\tnull\tnull\t"
        + "null");
    this.bridges.put("1FEDE50ED8DBA1DD9F9165F78C8131E4A44AB756",
        "b\tgummy\t1FEDE50ED8DBA1DD9F9165F78C8131E4A44AB756\t"
        + "10.63.169.98;;\t2013-04-24\t01:07:04\t9001\t0\tRunning,Valid\t"
        + "-1\t??\tnull\t-1\tnull\tnull\t2013-01-16\t21:07:04\tnull\t"
        + "null\tnull\tnull");
  }

  private void runTest(String requestURI,
      Map<String, String[]> parameterMap) {
    try {
      this.createDummyTime();
      this.createDummyDocumentStore();
      this.createNodeIndexer();
      this.makeRequest(requestURI, parameterMap);
      this.parseResponse();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void createDummyTime() {
    Time dummyTime = new DummyTime(this.currentTimeMillis);
    ApplicationFactory.setTime(dummyTime);
  }

  private void createDummyDocumentStore() {
    DummyDocumentStore documentStore = new DummyDocumentStore();
    UpdateStatus updateStatus = new UpdateStatus();
    updateStatus.setDocumentString(String.valueOf(
        this.currentTimeMillis));
    documentStore.addDocument(updateStatus, null);
    for (Map.Entry<String, String> e : relays.entrySet()) {
      documentStore.addDocument(NodeStatus.fromString(e.getValue()),
          e.getKey());
    }
    for (Map.Entry<String, String> e : bridges.entrySet()) {
      documentStore.addDocument(NodeStatus.fromString(e.getValue()),
          e.getKey());
    }
    ApplicationFactory.setDocumentStore(documentStore);
  }

  private void createNodeIndexer() {
    NodeIndexer newNodeIndexer = new NodeIndexer();
    newNodeIndexer.startIndexing();
    ApplicationFactory.setNodeIndexer(newNodeIndexer);
  }

  private void makeRequest(String requestURI,
      Map<String, String[]> parameterMap) throws IOException {
    ResourceServlet rs = new ResourceServlet();
    this.request = new TestingHttpServletRequestWrapper(requestURI,
       parameterMap);
    this.response = new TestingHttpServletResponseWrapper();
    rs.doGet(this.request, this.response);
  }

  private void parseResponse() {
    this.responseString = this.response.getWrittenContent();
    if (this.responseString != null) {
      Gson gson = new Gson();
      this.summaryDocument = gson.fromJson(this.responseString,
          SummaryDocument.class);
    }
  }

  private void assertErrorStatusCode(String request,
      int errorStatusCode) {
    String requestURI = parseRequestURI(request);
    Map<String, String[]> parameters = parseParameters(request);
    this.runTest(requestURI, parameters);
    assertEquals(errorStatusCode, this.response.errorStatusCode);
  }

  private void assertSummaryDocument(String request,
      int expectedRelaysNumber, String[] expectedRelaysNicknames,
      int expectedBridgesNumber, String[] expectedBridgesNicknames) {
    String requestURI = parseRequestURI(request);
    Map<String, String[]> parameters = parseParameters(request);
    this.runTest(requestURI, parameters);
    assertNotNull(this.summaryDocument);
    assertEquals(expectedRelaysNumber,
        this.summaryDocument.relays.length);
    if (expectedRelaysNicknames != null) {
      for (int i = 0; i < expectedRelaysNumber; i++) {
        assertEquals(expectedRelaysNicknames[i],
            this.summaryDocument.relays[i].n);
      }
    }
    assertEquals(expectedBridgesNumber,
        this.summaryDocument.bridges.length);
    if (expectedBridgesNicknames != null) {
      for (int i = 0; i < expectedBridgesNumber; i++) {
        assertEquals(expectedBridgesNicknames[i],
            this.summaryDocument.bridges[i].n);
      }
    }
  }

  private String parseRequestURI(String request) {
    return request.split("\\?")[0];
  }

  private Map<String, String[]> parseParameters(String request) {
    Map<String, String[]> parameters = null;
    String[] uriParts = request.split("\\?");
    if (uriParts.length == 2) {
      Map<String, List<String>> parameterLists =
          new HashMap<String, List<String>>();
      for (String parameter : uriParts[1].split("&")) {
        String[] parameterParts = parameter.split("=");
        if (!parameterLists.containsKey(parameterParts[0])) {
          parameterLists.put(parameterParts[0],
              new ArrayList<String>());
        }
        parameterLists.get(parameterParts[0]).add(parameterParts[1]);
      }
      parameters = new HashMap<String, String[]>();
      for (Map.Entry<String, List<String>> e :
          parameterLists.entrySet()) {
        parameters.put(e.getKey(),
            e.getValue().toArray(new String[e.getValue().size()]));
      }
    }
    return parameters;
  }

  private static class SummaryDocument {
    private String relays_published;
    private RelaySummary[] relays;
    private String bridges_published;
    private BridgeSummary[] bridges;
  }

  private static class RelaySummary {
    private String n;
    private String f;
    private String[] a;
    private boolean r;
  }

  private static class BridgeSummary {
    private String n;
    private String h;
    private boolean r;
  }

  @Test()
  public void testValidSummaryRelay() throws IOException {
    this.relays.clear();
    this.relays.put("000C5F55BD4814B917CC474BD537F1A3B33CCE2A",
        "r TorkaZ 000C5F55BD4814B917CC474BD537F1A3B33CCE2A "
        + "62.216.201.221;; 2013-04-19 05:00:00 9001 0 Running,Valid 20 "
        + "de null -1 reject 1-65535 2013-04-18 05:00:00 2013-04-19 "
        + "05:00:00 AS8767");
    this.runTest("/summary", null);
    assertEquals("2013-04-19 05:00:00",
        this.summaryDocument.relays_published);
    assertEquals(1, this.summaryDocument.relays.length);
    RelaySummary relay = this.summaryDocument.relays[0];
    assertEquals("TorkaZ", relay.n);
    assertEquals("000C5F55BD4814B917CC474BD537F1A3B33CCE2A", relay.f);
    assertEquals(1, relay.a.length);
    assertEquals("62.216.201.221", relay.a[0]);
    assertTrue(relay.r);
  }

  @Test()
  public void testValidSummaryBridge() {
    this.bridges.clear();
    this.bridges.put("0000831B236DFF73D409AD17B40E2A728A53994F",
        "b ec2bridgercc7f31fe 0000831B236DFF73D409AD17B40E2A728A53994F "
        + "10.199.7.176;; 2013-04-21 18:07:03 443 0 Valid -1 ?? null -1 "
        + "null null 2013-04-20 15:37:04 null null null");
    this.runTest("/summary", null);
    assertEquals("2013-04-21 18:07:03",
        this.summaryDocument.bridges_published);
    assertEquals(1, this.summaryDocument.bridges.length);
    BridgeSummary bridge = this.summaryDocument.bridges[0];
    assertEquals("ec2bridgercc7f31fe", bridge.n);
    assertEquals("0000831B236DFF73D409AD17B40E2A728A53994F", bridge.h);
    assertFalse(bridge.r);
  }

  @Test()
  public void testNonExistantDocumentType() {
    this.assertErrorStatusCode(
        "/doesnotexist", 400);
  }

  @Test()
  public void testSUMMARYDocument() {
    this.assertErrorStatusCode(
        "/SUMMARY", 400);
  }

  @Test()
  public void testTypeRelay() {
    this.assertSummaryDocument(
        "/summary?type=relay", 3, null, 0, null);
  }

  @Test()
  public void testTypeBridge() {
    this.assertSummaryDocument(
        "/summary?type=bridge", 0, null, 3, null);
  }

  @Test()
  public void testTypeBridgerelay() {
    this.assertErrorStatusCode(
        "/summary?type=bridgerelay", 400);
  }

  @Test()
  public void testTypeRelayBridge() {
    this.assertSummaryDocument(
        "/summary?type=relay&type=bridge", 3, null, 0, null);
  }

  @Test()
  public void testTypeBridgeRelay() {
    this.assertSummaryDocument(
        "/summary?type=bridge&type=relay", 0, null, 3, null);
  }

  @Test()
  public void testTypeRelayRelay() {
    this.assertSummaryDocument(
        "/summary?type=relay&type=relay", 3, null, 0, null);
  }

  @Test()
  public void testTYPERelay() {
    this.assertErrorStatusCode(
        "/summary?TYPE=relay", 400);
  }

  @Test()
  public void testTypeRELAY() {
    this.assertSummaryDocument(
        "/summary?type=RELAY", 3, null, 0, null);
  }

  @Test()
  public void testRunningTrue() {
    this.assertSummaryDocument(
        "/summary?running=true", 1, new String[] { "Ferrari458" }, 1,
        new String[] { "gummy" });
  }

  @Test()
  public void testRunningFalse() {
    this.assertSummaryDocument(
        "/summary?running=false", 2, null, 2, null);
  }

  @Test()
  public void testRunningTruefalse() {
    this.assertErrorStatusCode(
        "/summary?running=truefalse", 400);
  }

  @Test()
  public void testRunningTrueFalse() {
    this.assertSummaryDocument(
        "/summary?running=true&running=false", 1,
        new String[] { "Ferrari458" }, 1,  new String[] { "gummy" });
  }

  @Test()
  public void testRunningFalseTrue() {
    this.assertSummaryDocument(
        "/summary?running=false&running=true", 2, null, 2, null);
  }

  @Test()
  public void testRunningTrueTrue() {
    this.assertSummaryDocument(
        "/summary?running=true&running=true", 1,
        new String[] { "Ferrari458" }, 1, new String[] { "gummy" });
  }

  @Test()
  public void testRUNNINGTrue() {
    this.assertErrorStatusCode(
        "/summary?RUNNING=true", 400);
  }

  @Test()
  public void testRunningTRUE() {
    this.assertSummaryDocument(
        "/summary?running=TRUE", 1, null, 1, null);
  }

  @Test()
  public void testSearchTorkaZ() {
    this.assertSummaryDocument(
        "/summary?search=TorkaZ", 1, new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testSearchTorkaX() {
    this.assertSummaryDocument(
        "/summary?search=TorkaX", 0, null, 0, null);
  }

  @Test()
  public void testSearchOrkaZ() {
    this.assertSummaryDocument(
        "/summary?search=orkaZ", 1, new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testSearchTorka() {
    this.assertSummaryDocument(
        "/summary?search=Torka", 1, new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testSearchTORKAZ() {
    this.assertSummaryDocument(
        "/summary?search=TORKAZ", 1, new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testSearchDollarFingerprint() {
    this.assertSummaryDocument(
        "/summary?search=$000C5F55BD4814B917CC474BD537F1A3B33CCE2A", 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testSearchFingerprint() {
    this.assertSummaryDocument(
        "/summary?search=000C5F55BD4814B917CC474BD537F1A3B33CCE2A", 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testSearchDollarFingerprint39() {
    this.assertSummaryDocument(
        "/summary?search=$000C5F55BD4814B917CC474BD537F1A3B33CCE2", 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testSearchDollarFingerprintLowerCase39() {
    this.assertSummaryDocument(
        "/summary?search=$000c5f55bd4814b917cc474bd537f1a3b33cce2", 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testSearchFingerprintLowerCase39() {
    this.assertSummaryDocument(
        "/summary?search=000c5f55bd4814b917cc474bd537f1a3b33cce2", 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testSearchDollarHashedFingerprint() {
    this.assertSummaryDocument(
        "/summary?search=$5aa14c08d62913e0057a9ad5863b458c0ce94cee", 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testSearchDollarHashedFingerprint39() {
    this.assertSummaryDocument(
        "/summary?search=$5aa14c08d62913e0057a9ad5863b458c0ce94ce", 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testSearchDollarHashedFingerprint41() {
    this.assertErrorStatusCode(
        "/summary?search=$5aa14c08d62913e0057a9ad5863b458c0ce94ceee",
        400);
  }

  @Test()
  public void testSearchIp() {
    this.assertSummaryDocument(
        "/summary?search=62.216.201.221", 1, new String[] { "TorkaZ" }, 0,
        null);
  }

  @Test()
  public void testSearchIp24Network() {
    this.assertSummaryDocument(
        "/summary?search=62.216.201", 1, new String[] { "TorkaZ" }, 0,
        null);
  }

  @Test()
  public void testSearchIpExit() {
    this.assertSummaryDocument(
        "/summary?search=62.216.201.222", 1, new String[] { "TorkaZ" }, 0,
        null);
  }

  @Test()
  public void testSearchIpv6() {
    this.assertSummaryDocument(
        "/summary?search=[2001:4f8:3:2e::51]", 1,
        new String[] { "Ferrari458" }, 0, null);
  }

  @Test()
  public void testSearchIpv6Slash64NoTrailingBracket() {
    this.assertSummaryDocument(
        "/summary?search=[2001:4f8:3:2e::", 1,
        new String[] { "Ferrari458" }, 0, null);
  }

  @Test()
  public void testSearchIpv6Slash64TrailingBracket() {
    this.assertSummaryDocument(
        "/summary?search=[2001:4f8:3:2e::]", 0, null, 0, null);
  }

  @Test()
  public void testSearchIpv6Uncompressed() {
    this.assertSummaryDocument(
        "/summary?search=[2001:04f8:0003:002e:0000:0000:0000:0051]", 0,
        null, 0, null);
  }

  @Test()
  public void testSearchIpv6UpperCase() {
    this.assertSummaryDocument(
        "/summary?search=[2001:4F8:3:2E::51]", 1,
        new String[] { "Ferrari458" }, 0, null);
  }

  @Test()
  public void testSearchIpv6ThreeColons() {
    this.assertSummaryDocument(
        "/summary?search=[2001:4f8:3:2e:::51]", 0, null, 0, null);
  }

  @Test()
  public void testSearchIpv6FiveHex() {
    this.assertSummaryDocument(
        "/summary?search=[20014:f80:3:2e::51]", 0, null, 0, null);
  }

  @Test()
  public void testSearchIpv6NineGroups() {
    this.assertSummaryDocument(
        "/summary?search=[1:2:3:4:5:6:7:8:9]", 0, null, 0, null);
  }

  @Test()
  public void testSearchIpv6TcpPort() {
    this.assertErrorStatusCode(
        "/summary?search=[2001:4f8:3:2e::51]:9001", 400);
  }

  @Test()
  public void testSearchGummy() {
    this.assertSummaryDocument(
        "/summary?search=gummy", 0, null, 1, new String[] { "gummy" });
  }

  @Test()
  public void testSearchGummi() {
    this.assertSummaryDocument(
        "/summary?search=gummi", 0, null, 0, null);
  }

  @Test()
  public void testSearchUmmy() {
    this.assertSummaryDocument(
        "/summary?search=ummy", 0, null, 1, new String[] { "gummy" });
  }

  @Test()
  public void testSearchGumm() {
    this.assertSummaryDocument(
        "/summary?search=gumm", 0, null, 1, new String[] { "gummy" });
  }

  @Test()
  public void testSearchGUMMY() {
    this.assertSummaryDocument(
        "/summary?search=GUMMY", 0, null, 1, new String[] { "gummy" });
  }

  @Test()
  public void testSearchBridgeDollarHashedFingerprint() {
    this.assertSummaryDocument(
        "/summary?search=$1FEDE50ED8DBA1DD9F9165F78C8131E4A44AB756", 0,
        null, 1, new String[] { "gummy" });
  }

  @Test()
  public void testSearchBridgeHashedFingerprint() {
    this.assertSummaryDocument(
        "/summary?search=1FEDE50ED8DBA1DD9F9165F78C8131E4A44AB756", 0,
        null, 1, new String[] { "gummy" });
  }

  @Test()
  public void testSearchBridgeDollarHashedFingerprint39() {
    this.assertSummaryDocument(
        "/summary?search=$1FEDE50ED8DBA1DD9F9165F78C8131E4A44AB75", 0,
        null, 1, new String[] { "gummy" });
  }

  @Test()
  public void testSearchBridgeDollarHashedFingerprintLowerCase39() {
    this.assertSummaryDocument(
        "/summary?search=$1fede50ed8dba1dd9f9165f78c8131e4a44ab75", 0,
        null, 1, new String[] { "gummy" });
  }

  @Test()
  public void testSearchBridgeHashedFingerprintLowerCase39() {
    this.assertSummaryDocument(
        "/summary?search=1fede50ed8dba1dd9f9165f78c8131e4a44ab75", 0,
        null, 1, new String[] { "gummy" });
  }

  @Test()
  public void testSearchBridgeDollarHashedHashedFingerprint() {
    this.assertSummaryDocument(
        "/summary?search=$CE52F898DB3678BCE33FAC28C92774DE90D618B5", 0,
        null, 1, new String[] { "gummy" });
  }

  @Test()
  public void testSearchBridgeDollarHashedHashedFingerprint39() {
    this.assertSummaryDocument(
        "/summary?search=$CE52F898DB3678BCE33FAC28C92774DE90D618B", 0,
        null, 1, new String[] { "gummy" });
  }

  @Test()
  public void testSearchBridgeDollarOriginalFingerprint() {
    this.assertSummaryDocument(
        "/summary?search=$0010D49C6DA1E46A316563099F41BFE40B6C7183", 0,
        null, 0, null);
  }

  @Test()
  public void testSearchUnderscore() {
    this.assertErrorStatusCode(
        "/summary?search=_", 400);
  }

  @Test()
  public void testLookupFingerprint() {
    this.assertSummaryDocument(
        "/summary?lookup=000C5F55BD4814B917CC474BD537F1A3B33CCE2A", 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testLookupDollarFingerprint() {
    this.assertErrorStatusCode(
        "/summary?lookup=$000C5F55BD4814B917CC474BD537F1A3B33CCE2A", 400);
  }

  @Test()
  public void testLookupDollarFingerprint39() {
    this.assertErrorStatusCode(
        "/summary?lookup=$000C5F55BD4814B917CC474BD537F1A3B33CCE2", 400);
  }

  @Test()
  public void testLookupFingerprintLowerCase39() {
    this.assertErrorStatusCode(
        "/summary?lookup=000c5f55bd4814b917cc474bd537f1a3b33cce2", 400);
  }

  @Test()
  public void testLookupHashedFingerprint() {
    this.assertSummaryDocument(
        "/summary?lookup=5aa14c08d62913e0057a9ad5863b458c0ce94cee", 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testLookupBridgeHashedFingerprint() {
    this.assertSummaryDocument(
        "/summary?lookup=1FEDE50ED8DBA1DD9F9165F78C8131E4A44AB756", 0,
        null, 1, new String[] { "gummy" });
  }

  @Test()
  public void testSearchBridgeHashedHashedFingerprint() {
    this.assertSummaryDocument(
        "/summary?lookup=CE52F898DB3678BCE33FAC28C92774DE90D618B5", 0,
        null, 1, new String[] { "gummy" });
  }

  @Test()
  public void testLookupBridgeOriginalFingerprint() {
    this.assertSummaryDocument(
        "/summary?lookup=0010D49C6DA1E46A316563099F41BFE40B6C7183", 0,
        null, 0, null);
  }

  @Test()
  public void testCountryDe() {
    this.assertSummaryDocument(
        "/summary?country=de", 1, new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testCountryFr() {
    this.assertSummaryDocument(
        "/summary?country=fr", 0, null, 0, null);
  }

  @Test()
  public void testCountryZz() {
    this.assertSummaryDocument(
        "/summary?country=zz", 0, null, 0, null);
  }

  @Test()
  public void testCountryDE() {
    this.assertSummaryDocument(
        "/summary?country=DE", 1, new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testCountryDeu() {
    this.assertErrorStatusCode(
        "/summary?country=deu", 400);
  }

  @Test()
  public void testCountryD() {
    this.assertErrorStatusCode(
        "/summary?country=d", 400);
  }

  @Test()
  public void testCountryA1() {
    this.assertSummaryDocument(
        "/summary?country=a1", 1, new String[] { "TimMayTribute" }, 0,
        null);
  }

  @Test()
  public void testCountryDeDe() {
    this.assertSummaryDocument(
        "/summary?country=de&country=de", 1, new String[] { "TorkaZ" }, 0,
        null);
  }

  @Test()
  public void testAsAS8767() {
    this.assertSummaryDocument(
        "/summary?as=AS8767", 1, new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testAs8767() {
    this.assertSummaryDocument(
        "/summary?as=8767", 1, new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testAsAS() {
    this.assertErrorStatusCode(
        "/summary?as=AS", 400);
  }

  @Test()
  public void testAsas8767() {
    this.assertSummaryDocument(
        "/summary?as=as8767", 1, new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testAsASSpace8767() {
    this.assertErrorStatusCode(
        "/summary?as=AS 8767", 400);
  }

  @Test()
  public void testFlagRunning() {
    this.assertSummaryDocument(
        "/summary?flag=Running", 3, null, 1, null);
  }

  @Test()
  public void testFlagValid() {
    this.assertSummaryDocument(
        "/summary?flag=Valid", 3, null, 3, null);
  }

  @Test()
  public void testFlagFast() {
    this.assertSummaryDocument(
        "/summary?flag=Fast", 2, null, 0, null);
  }

  @Test()
  public void testFlagNamed() {
    this.assertSummaryDocument(
        "/summary?flag=Named", 1, null, 0, null);
  }

  @Test()
  public void testFlagUnnamed() {
    this.assertSummaryDocument(
        "/summary?flag=Unnamed", 1, null, 0, null);
  }

  @Test()
  public void testFlagV2Dir() {
    this.assertSummaryDocument(
        "/summary?flag=V2Dir", 2, null, 0, null);
  }

  @Test()
  public void testFlagGuard() {
    this.assertSummaryDocument(
        "/summary?flag=Guard", 0, null, 0, null);
  }

  @Test()
  public void testFlagCool() {
    this.assertSummaryDocument(
        "/summary?flag=Cool", 0, null, 0, null);
  }

  @Test()
  public void testFirstSeenDaysZeroToTwo() {
    this.assertSummaryDocument(
        "/summary?first_seen_days=0-2", 0, null, 0, null);
  }

  @Test()
  public void testFirstSeenDaysUpToThree() {
    this.assertSummaryDocument(
        "/summary?first_seen_days=-3", 0, null, 1, null);
  }

  @Test()
  public void testFirstSeenDaysThree() {
    this.assertSummaryDocument(
        "/summary?first_seen_days=3", 0, null, 1, null);
  }

  @Test()
  public void testFirstSeenDaysTwoToFive() {
    this.assertSummaryDocument(
        "/summary?first_seen_days=2-5", 0, null, 1, null);
  }

  @Test()
  public void testFirstSeenDaysSixToSixteen() {
    this.assertSummaryDocument(
        "/summary?first_seen_days=6-16", 2, null, 1, null);
  }

  @Test()
  public void testFirstSeenDaysNinetysevenOrMore() {
    this.assertSummaryDocument(
        "/summary?first_seen_days=97-", 0, null, 1, null);
  }

  @Test()
  public void testFirstSeenDaysNinetyeightOrMore() {
    this.assertSummaryDocument(
        "/summary?first_seen_days=98-", 0, null, 0, null);
  }

  @Test()
  public void testFirstSeenDaysDashDash() {
    this.assertErrorStatusCode(
        "/summary?first_seen_days=--", 400);
  }

  @Test()
  public void testFirstSeenDaysDashOneDash() {
    this.assertErrorStatusCode(
        "/summary?first_seen_days=-1-", 400);
  }

  @Test()
  public void testFirstSeenDaysZeroDotDotOne() {
    this.assertErrorStatusCode(
        "/summary?first_seen_days=0..1", 400);
  }

  @Test()
  public void testFirstSeenDaysElevenDigits() {
    this.assertErrorStatusCode(
        "/summary?first_seen_days=12345678901", 400);
  }

  @Test()
  public void testFirstSeenDaysLargeTenDigitNumber() {
    this.assertErrorStatusCode(
        "/summary?first_seen_days=9999999999", 400);
  }

  @Test()
  public void testFirstSeenDaysMaxInt() {
    this.assertSummaryDocument(
        "/summary?last_seen_days=" + String.valueOf(Integer.MAX_VALUE), 0,
        null, 0, null);
  }

  @Test()
  public void testFirstSeenDaysMaxIntPlusOne() {
    this.assertErrorStatusCode(
        "/summary?first_seen_days="
        + String.valueOf(Integer.MAX_VALUE + 1), 400);
  }

  @Test()
  public void testLastSeenDaysZero() {
    this.assertSummaryDocument(
        "/summary?last_seen_days=0", 1, null, 1, null);
  }

  @Test()
  public void testLastSeenDaysUpToZero() {
    this.assertSummaryDocument(
        "/summary?last_seen_days=-0", 1, null, 1, null);
  }

  @Test()
  public void testLastSeenDaysOneToThree() {
    this.assertSummaryDocument(
        "/summary?last_seen_days=1-3", 1, null, 2, null);
  }

  @Test()
  public void testLastSeenDaysSixOrMore() {
    this.assertSummaryDocument(
        "/summary?last_seen_days=6-", 0, null, 0, null);
  }

  @Test()
  public void testContactSteven() {
    this.assertSummaryDocument(
        "/summary?contact=Steven", 1, null, 0, null);
  }

  @Test()
  public void testContactStevenMurdoch() {
    this.assertSummaryDocument(
        "/summary?contact=Steven Murdoch", 1, null, 0, null);
  }

  @Test()
  public void testContactMurdochSteven() {
    this.assertSummaryDocument(
        "/summary?contact=Murdoch Steven", 1, null, 0, null);
  }

  @Test()
  public void testContactStevenDotMurdoch() {
    this.assertSummaryDocument(
        "/summary?contact=Steven.Murdoch", 1, null, 0, null);
  }

  @Test()
  public void testContactFbTokenFive() {
    this.assertSummaryDocument(
        "/summary?contact=<fb-token:5sR_K_zs2wM=>", 1, null, 0, null);
  }

  @Test()
  public void testContactFbToken() {
    this.assertSummaryDocument(
        "/summary?contact=<fb-token:", 2, null, 0, null);
  }

  @Test()
  public void testContactDash() {
    this.assertSummaryDocument(
        "/summary?contact=-", 2, null, 0, null);
  }

  @Test()
  public void testOrderConsensusWeightAscending() {
    this.assertSummaryDocument(
        "/summary?order=consensus_weight", 3,
        new String[] { "TorkaZ", "TimMayTribute", "Ferrari458" }, 3,
        null);
  }

  @Test()
  public void testOrderConsensusWeightDescending() {
    this.assertSummaryDocument(
        "/summary?order=-consensus_weight", 3,
        new String[] { "Ferrari458", "TimMayTribute", "TorkaZ" }, 3,
        null);
  }

  @Test()
  public void testOrderConsensusWeightAscendingTwice() {
    this.assertErrorStatusCode(
        "/summary?order=consensus_weight,consensus_weight", 400);
  }

  @Test()
  public void testOrderConsensusWeightAscendingThenDescending() {
    this.assertErrorStatusCode(
        "/summary?order=consensus_weight,-consensus_weight", 400);
  }

  @Test()
  public void testOrderConsensusWeightThenNickname() {
    this.assertErrorStatusCode(
        "/summary?order=consensus_weight,nickname", 400);
  }

  @Test()
  public void testOrderCONSENSUS_WEIGHT() {
    this.assertSummaryDocument(
        "/summary?order=CONSENSUS_WEIGHT", 3,
        new String[] { "TorkaZ", "TimMayTribute", "Ferrari458" }, 3,
        null);
  }

  @Test()
  public void testOrderConsensusWeightAscendingLimit1() {
    this.assertSummaryDocument(
        "/summary?order=consensus_weight&limit=1", 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testOrderConsensusWeightDecendingLimit1() {
    this.assertSummaryDocument(
        "/summary?order=-consensus_weight&limit=1", 1,
        new String[] { "Ferrari458" }, 0, null);
  }

  @Test()
  public void testOffsetOne() {
    this.assertSummaryDocument(
        "/summary?offset=1", 2, null, 3, null);
  }

  @Test()
  public void testOffsetAllRelays() {
    this.assertSummaryDocument(
        "/summary?offset=3", 0, null, 3, null);
  }

  @Test()
  public void testOffsetAllRelaysAndOneBridge() {
    this.assertSummaryDocument(
        "/summary?offset=4", 0, null, 2, null);
  }

  @Test()
  public void testOffsetAllRelaysAndAllBridges() {
    this.assertSummaryDocument(
        "/summary?offset=6", 0, null, 0, null);
  }

  @Test()
  public void testOffsetMoreThanAllRelaysAndAllBridges() {
    this.assertSummaryDocument(
        "/summary?offset=7", 0, null, 0, null);
  }

  @Test()
  public void testOffsetZero() {
    this.assertSummaryDocument(
        "/summary?offset=0", 3, null, 3, null);
  }

  @Test()
  public void testOffsetMinusOne() {
    this.assertSummaryDocument(
        "/summary?offset=-1", 3, null, 3, null);
  }

  @Test()
  public void testOffsetOneWord() {
    this.assertErrorStatusCode(
        "/summary?offset=one", 400);
  }

  @Test()
  public void testLimitOne() {
    this.assertSummaryDocument(
        "/summary?limit=1", 1, null, 0, null);
  }

  @Test()
  public void testLimitAllRelays() {
    this.assertSummaryDocument(
        "/summary?limit=3", 3, null, 0, null);
  }

  @Test()
  public void testLimitAllRelaysAndOneBridge() {
    this.assertSummaryDocument(
        "/summary?limit=4", 3, null, 1, null);
  }

  @Test()
  public void testLimitAllRelaysAndAllBridges() {
    this.assertSummaryDocument(
        "/summary?limit=6", 3, null, 3, null);
  }

  @Test()
  public void testLimitMoreThanAllRelaysAndAllBridges() {
    this.assertSummaryDocument(
        "/summary?limit=7", 3, null, 3, null);
  }

  @Test()
  public void testLimitZero() {
    this.assertSummaryDocument(
        "/summary?limit=0", 0, null, 0, null);
  }

  @Test()
  public void testLimitMinusOne() {
    this.assertSummaryDocument(
        "/summary?limit=-1", 0, null, 0, null);
  }

  @Test()
  public void testLimitOneWord() {
    this.assertErrorStatusCode(
        "/summary?limit=one", 400);
  }
}

