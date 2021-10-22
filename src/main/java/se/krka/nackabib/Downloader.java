package se.krka.nackabib;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

  public static final Pattern PATTERN = Pattern.compile(" id=\"UrlToken\" value=\"([^\"]*)\"");
  private String urlToken;

  public static void download(final File baseDir, UserConfig config) throws Exception {
    final String timestamp = Util.getCurrentTimestamp();
    final File downloadDir = FileUtils.getFile(baseDir, timestamp);
    final File inprogressDir = FileUtils.getFile(baseDir, timestamp + ".inprogress");
    try {
      FileUtils.forceMkdir(inprogressDir);
      System.out.println("Writing to " + inprogressDir.getAbsolutePath());
      download(config, inprogressDir);
      System.out.println("Move " + inprogressDir.getName() + " to " + downloadDir.getName());
      FileUtils.moveDirectory(inprogressDir, downloadDir);
    } catch (final Throwable e) {
      FileUtils.deleteDirectory(inprogressDir);
      throw e;
    }
  }

  public static void main(String[] args) throws Exception {
    File baseDir = new File("data");
    Dedup.dedup(baseDir);
    download(new UserConfig(baseDir), baseDir);
  }

  private static void download(final UserConfig config, final File downloadDir) throws Exception {
    for (Map.Entry<String, UserConfig.User> entry : config.getUsersByUsername().entrySet()) {
      final String key = entry.getKey();
      final UserConfig.User user = entry.getValue();
      final Downloader downloader = new Downloader(key, user.getPassword());
      final File dir = new File(downloadDir, key);
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
    getUrlToken();

    final HttpPost request = new HttpPost();
    request.setURI(URI.create(Login.BASE_URL));

    request.setEntity(new UrlEncodedFormEntity(ImmutableList.of(
        new BasicNameValuePair("Username", username),
        new BasicNameValuePair("Password", password),
        new BasicNameValuePair("RememberLogin", "true"),
        new BasicNameValuePair(
            "ReturnUrl",
                Login.RETURN_URL),
        new BasicNameValuePair("UrlToken", urlToken)
    ), "UTF-8"));

    String response = sendFollowRedirect(request);
    return cookieStore.getCookies().stream()
        .filter(c -> c.getName().equals(".AspNetCore.Cookies"))
        .findAny()
        .isPresent();
  }

  private void getUrlToken() throws IOException {
    String initialResponse = sendWaitForCache(getRequest("https://bib.nacka.se/login"));
    Matcher matcher = PATTERN.matcher(initialResponse);
    if (!matcher.find()) {
      throw new RuntimeException("Could not find UrlToken on login page");
    }
    urlToken = matcher.group(1);
  }

  void fetchAll(final File dir) throws Exception {
    ensureLogin();

    saveResource(dir, "cards");
    saveResource(dir, "loans");
    saveResource(dir, "debts");
    saveResource(dir, "catalogs");
    saveResource(dir, "reservations");
    saveSettings(dir);
    saveResource2(dir, "catalogs/libraries", "libraries");
  }

  private void saveSettings(final File dir) throws IOException, JSONException {
    final JSONObject value = (JSONObject) getResource("settings");
    // This field is both useless and very volatile
    value.remove("id");
    value.put("username", username);
    value.put("urltoken", urlToken);
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
    //request.setHeader("Accept", "*/*");
    //request.setHeader("Accept-Encoding", "gzip, deflate, br");
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
      final URI uri = request.getURI();
      System.out.println("Sending request: " + uri);
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
