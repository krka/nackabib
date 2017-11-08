package se.krka.nackabib;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.http.Header;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Downloader {

  public static void download(final File baseDir) throws Exception {
    final Config config = ConfigFactory.parseFile(FileUtils.getFile(baseDir, "bib.conf"));

    final String timestamp = Util.getCurrentTimestamp();
    final File downloadDir = FileUtils.getFile(baseDir, timestamp);
    final File inprogressDir = FileUtils.getFile(baseDir, timestamp + ".inprogress");
    FileUtils.forceMkdir(inprogressDir);
    System.out.println("Writing to " + inprogressDir.getAbsolutePath());
    try {
      download(config, inprogressDir);
      System.out.println("Move " + inprogressDir.getName() + " to " + downloadDir.getName());
      FileUtils.moveDirectory(inprogressDir, downloadDir);
    } catch (final Exception e) {
      FileUtils.deleteDirectory(inprogressDir);
      throw e;
    }
  }

  private static void download(final Config config, final File downloadDir) throws Exception {
    final List<? extends Config> credentials = config.getConfigList("credentials");
    for (Config credential : credentials) {
      final String username = credential.getString("username");
      final String password = credential.getString("password");

      final Downloader downloader = new Downloader(username, password);
      final File dir = new File(downloadDir, username);
      FileUtils.forceMkdir(dir);
      downloader.fetchAll(dir);
    }
  }

  private final BasicCookieStore cookieStore;
  private final CloseableHttpClient client;
  private final String username;
  private final String password;

  private boolean loggedIn = false;

  public Downloader(String username, String password) {
    this.username = username;
    this.password = password;
    cookieStore = new BasicCookieStore();
    client = HttpClientBuilder.create()
        .setDefaultCookieStore(cookieStore)
        .setDefaultRequestConfig(RequestConfig.custom()
            .setCookieSpec(CookieSpecs.STANDARD)
            .build())
        .build();
  }

  private void ensureLogin() throws IOException {
    if (loggedIn) {
      return;
    }

    loggedIn = login();
    if (!loggedIn) {
      throw new RuntimeException("Could not login with username " + username);
    }
  }

  private boolean login() throws IOException {
    final HttpPost request = new HttpPost();
    request.setURI(URI.create("https://auth.dvbib.se/"));

    request.setEntity(new UrlEncodedFormEntity(ImmutableList.of(
        new BasicNameValuePair("Username", username),
        new BasicNameValuePair("Password", password),
        new BasicNameValuePair("RememberLogin", "true"),
        new BasicNameValuePair(
            "ReturnUrl",
            "https://bib.nacka.se:443/sv/library-page/fisks%C3%A4tra-bibliotek"),
        new BasicNameValuePair(
            "LoginUrl",
            "https://bib.nacka.se/login?ReturnUrl=%2Fsv%2Flibrary-page%2Ffisks%25C3%25A4tra-bibliotek")
    ), "UTF-8"));

    sendWaitForCache(request);
    return cookieStore.getCookies().stream()
        .filter(c -> c.getName().equals(".AspNetCore.Cookies"))
        .findAny()
        .isPresent();
  }

  void fetchAll(final File dir) throws Exception {
    ensureLogin();

    saveResource(dir, "cards");
    saveResource(dir, "loans");
    saveResource(dir, "debts");
    saveResource(dir, "catalogs");
    saveResource(dir, "reservations");
    saveResource(dir, "electronicmedia");
    saveSettings(dir);
    saveResource2(dir, "catalogs/libraries", "libraries");
  }

  private void saveSettings(final File dir) throws IOException, JSONException {
    final JSONObject value = (JSONObject) getResource("settings");
    // This field is both useless and very volatile
    value.remove("id");
    writeFile(dir, "settings", value);
  }

  private void saveResource(final File dir, final String name) throws IOException, JSONException {
    saveResource2(dir, name, name);
  }

  private void saveResource2(final File dir, final String path, final String fileName) throws IOException, JSONException {
    final Object value = getResource(path);
    writeFile(dir, fileName, value);
  }

  private void writeFile(final File dir, final String name, Object value)
      throws IOException, JSONException {
    FileUtils.write(new File(dir, name), toString(value), Charsets.UTF_8);
  }

  private String toString(final Object value) throws JSONException {
    if (value instanceof JSONObject) {
      return ((JSONObject) value).toString(2);
    }
    if (value instanceof JSONArray) {
      return ((JSONArray) value).toString(2);
    }
    return String.valueOf(value);
  }

  private Object getResource(final String path) throws IOException, JSONException {
    final String baseUri = "https://bib.nacka.se/api/";

    final HttpGet request = getRequest(baseUri + path);
    final String s = sendWaitForCache(request);
    if (s.startsWith("{")) {
      return new JSONObject(s);
    } else if (s.startsWith("[")) {
      return new JSONArray(s);
    } else {
      return s;
    }
  }

  private static HttpGet getRequest(final String value) {
    final HttpGet request = new HttpGet();
    request.setURI(URI.create(value));
    return request;
  }

  private String sendWaitForCache(final HttpUriRequest request)
      throws IOException {
    while (true) {
      final String s = sendFollowRedirect(request);
      if (!s.equals("{\"cacheState\":\"working\"}")) {
        return s;
      }

      System.out.println(request.getURI() + " busy... retrying soon");
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  private String sendFollowRedirect(HttpUriRequest request)
      throws IOException {
    while (true) {
      System.out.println("Sending request: " + request.getURI());
      final CloseableHttpResponse response = client.execute(request);
      final int statusCode = response.getStatusLine().getStatusCode();

      if (statusCode == 200) {
        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        response.getEntity().writeTo(stream);
        return new String(stream.toByteArray(), Charsets.UTF_8);
      }

      if (statusCode != 302) {
        throw new RuntimeException("Failed request: " + response.getStatusLine());
      }

      final Header location = response.getFirstHeader("Location");
      if (location == null) {
        throw new RuntimeException("Failed request: missing Location header for 302");
      }

      // Follow the redirect
      request = getRequest(location.getValue());
    }
  }

}
