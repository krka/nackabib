package se.krka.nackabib;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Render {

  private final Map<String, User> usersByUserId = Maps.newTreeMap();
  private final Map<String, User> usersByUsername = Maps.newTreeMap();
  private final Set<String> shortNames = Sets.newHashSet();

  private final String scriptText;
  private final String style;
  private String mostRecentTimestamp;
  private List<Reservation> reservationsReady;
  private List<Reservation> reservations;
  private List<Loan> history;
  private List<Loan> loans;
  private JSONObject debts;

  public Render() throws IOException {
    scriptText = Resources.toString(Resources.getResource("script.js"), Charsets.UTF_8);
    style = Resources.toString(Resources.getResource("style.css"), Charsets.UTF_8);
  }

  public void collectData(File baseDir) throws JSONException, IOException {
    List<File> allData = Util.getDirectories(baseDir);
    if (allData.isEmpty()) {
      throw new RuntimeException("No data found in " + baseDir.getAbsolutePath());
    }
    File mostRecent = allData.remove(allData.size() - 1);

    final String mostRecentName = mostRecent.getName();

    addUsers(mostRecent);
    for (File dir : allData) {
      addUsers(dir);
    }

    mostRecentTimestamp = mostRecentName
        .replace("T", " kl ")
        .replaceAll(":[0-9]{2}\\.[0-9]{3}$", "");
    final Set<Loan> loansSet = getLoans(mostRecent);
    final Set<Loan> historySet = getHistory(allData);
    historySet.removeAll(loansSet);
    final Set<Reservation> reservationsSet = getReservations(mostRecent);

    debts = getDebts(mostRecent);
    loans = ImmutableList.sortedCopyOf(
        (o1, o2) -> ComparisonChain.start()
        .compare(o1.returnDate, o2.returnDate)
        .compare(o1.user, o2.user)
        .compare(o1.author, o2.author)
        .compare(o1.title, o2.title)
        .result(),
        loansSet);

    history = ImmutableList.sortedCopyOf(
        (o1, o2) -> ComparisonChain.start()
        .compare(o2.returnDate, o1.returnDate)
        .compare(o1.user, o2.user)
        .compare(o1.author, o2.author)
        .compare(o1.title, o2.title)
        .result(),
        historySet);

    reservationsReady = reservationsSet.stream()
        .filter(r -> !r.lastFetchDate.equals(""))
        .collect(Collectors.toList());

    reservationsReady.sort((o1, o2) -> ComparisonChain.start()
        .compare(o2.lastFetchDate, o1.lastFetchDate)
        .compare(o1.user, o2.user)
        .compare(o1.author, o2.author)
        .compare(o1.title, o2.title)
        .result());

    reservations = reservationsSet.stream()
        .filter(r -> r.lastFetchDate.equals(""))
        .collect(Collectors.toList());
    reservations.sort((o1, o2) -> ComparisonChain.start()
        .compare(o1.reservedFrom, o2.reservedFrom)
        .compare(o1.user, o2.user)
        .compare(o1.author, o2.author)
        .compare(o1.title, o2.title)
        .result());
  }

  private void addUsers(final File dir) throws IOException, JSONException {
    for (File subDir : dir.listFiles()) {
      if (!subDir.isDirectory()) {
        continue;
      }
      final JSONArray cards = readJsonArray(new File(subDir, "cards"));
      final String userId = cards.getJSONObject(0).getJSONObject("token").getString("userId");
      if (usersByUserId.get(userId) == null) {
        final String username = subDir.getName();
        final String displayName = cards.getJSONObject(0).getString("displayName");
        final String shortName = findShortName(displayName);
        final User user = new User(userId, username, displayName, shortName);
        usersByUserId.put(userId, user);
        usersByUsername.put(username, user);
        shortNames.add(shortName);
      }
    }
  }

  private String findShortName(final String displayName) {
    final String base = Splitter.on(CharMatcher.anyOf("- ")).splitToList(displayName)
        .stream()
        .filter(s1 -> !s1.isEmpty())
        .map(s1 -> s1.substring(0, 1))
        .reduce((a, b) -> a + b).orElse("");
    if (shortNames.add(base)) {
      return base;
    }
    for (int i = 0; i < 100; i++) {
      if (shortNames.add(base + i)) {
        return base + i;
      }
    }
    return displayName;
  }

  private Set<Reservation> getReservations(final File dir) throws IOException, JSONException {
    final SortedSet<Reservation> set = Sets.newTreeSet();
    for (File subDir : dir.listFiles()) {
      if (subDir.isDirectory()) {
        final String username = subDir.getName();
        final User user = getUser(subDir, username);
        for (Object o : readJsonArray(new File(subDir, "reservations"))) {
          set.add(new Reservation(user, (JSONObject) o));
        }
      }
    }
    return set;
  }

  // I have never seen a debts objects so I don't know what to do yet
  private JSONObject getDebts(final File dir) throws IOException, JSONException {
    JSONObject map = new JSONObject();
    for (File subDir : dir.listFiles()) {
      if (subDir.isDirectory()) {
        final String username = subDir.getName();
        final User user = getUser(subDir, username);
        final JSONArray debts = readJsonArray(new File(subDir, "debts"));
        if (debts.length() != 0) {
          map.put(user.shortName, debts);
        }
      }
    }
    return map;
  }

  private User getUser(final File subDir, final String username) {
    final User user = usersByUsername.get(username);
    if (user == null) {
      throw new RuntimeException("Could not find user in directory: " + subDir.getAbsolutePath());
    }
    return user;
  }

  private Set<Loan> getLoans(final File dir) throws IOException, JSONException {
    final SortedSet<Loan> set = Sets.newTreeSet();
    addLoan(set, dir);
    return set;
  }

  private Set<Loan> getHistory(final List<File> allData)
      throws IOException, JSONException {
    final SortedSet<Loan> set = Sets.newTreeSet();
    for (File dir : allData) {
      addLoan(set, dir);
    }
    return set;
  }

  private void addLoan(final SortedSet<Loan> set, final File dir)
      throws JSONException, IOException {
    for (File subDir : dir.listFiles()) {
      if (subDir.isDirectory()) {
        final User user = getUser(subDir, subDir.getName());
        for (Object o : readJsonArray(new File(subDir, "loans"))) {
          set.add(new Loan(user, (JSONObject) o));
        }
      }
    }
  }

  public void toHtml(final File file) throws IOException, JSONException {
    StringBuilder sb = new StringBuilder();

    sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>Bibliotekslån</title>\n");
    sb.append("<style>\n");
    sb.append(style);
    sb.append("\n</style>\n");
    sb.append("</head>\n");
    sb.append("<body>\n");
    sb.append("<p>Senast uppdaterat ");
    sb.append(mostRecentTimestamp);
    sb.append("</p>\n");

    showDebts(sb, "Skulder", debts);
    showReservation(sb, "Att hämta", reservationsReady, "Hämta senast", r -> dateSpan(r.lastFetchDate));
    showReservation(sb, "Reservationer", reservations, "Från", r -> historic(r.reservedFrom));

    showLoans(sb, "Lån", loans, "Tillbaka senast", loan -> dateSpan(loan.returnDate));
    showLoans(sb, "Historik", history, "Tillbaka senast", loan -> historic(loan.returnDate));
    showUsers(sb, ImmutableList.sortedCopyOf(usersByUserId.values()));

    sb.append("<script>\n").append(scriptText).append("\n</script>\n");
    sb.append("</body></html>\n");
    String s = sb.toString();

    FileUtils.write(file, s, Charsets.UTF_8);
  }

  private void showDebts(final StringBuilder sb, final String header, final JSONObject debts) throws JSONException {
    int total = 0;
    for (String key : debts.keySet()) {
      total += debts.getJSONArray(key).length();
    }
    if (debts.length() != 0) {
      sb.append("<h3>").append(header).append(" (").append(total).append(")").append("</h3>\n");
      for (String key : debts.keySet()) {
        sb.append("<pre>").append(debts.getJSONArray(key).toString(2)).append("</pre>\n");
      }
    }
  }

  private void showReservation(final StringBuilder sb, final String header, final List<Reservation> list,
                               final String dateColumn,
                               final Function<Reservation, String> dateSupplier) {
    if (!list.isEmpty()) {
      sb.append("<h3>").append(header).append(" (").append(list.size()).append(")").append("</h3>\n");
      sb.append("<table><thead><tr>");
      sb.append("<th>Låntagare</th>");
      sb.append("<th>").append(dateColumn).append("</th>");
      sb.append("<th>Köplats</th>");
      sb.append("<th>Författare</th>");
      sb.append("<th>Titel</th>");
      sb.append("</tr></thead>\n");
      sb.append("<tbody>\n");
      final List<Grouper.Group<Reservation, User>> grouped =
          Grouper.groupBy(list, Reservation::getUser);
      for (Grouper.Group<Reservation, User> group : grouped) {
        for (Reservation reservation : group.getObjects()) {
          sb.append("<tr>");
          sb.append("<td>");
          sb.append(group.getKey().shortName);
          sb.append("</td>");
          sb.append("<td>");
          sb.append(dateSupplier.apply(reservation));
          sb.append("</td>");
          sb.append("<td>");
          sb.append(reservation.queueNumber);
          sb.append("</td>");
          sb.append("<td>");
          sb.append(reservation.author);
          sb.append("</td>");
          sb.append("<td>");
          sb.append(reservation.title);
          sb.append("</td>");
          sb.append("</tr>\n");
        }
      }
      sb.append("</tbody></table>\n");
    }
  }

  private void showLoans(
      final StringBuilder sb,
      final String header,
      final List<Loan> list,
      final String dateColumn,
      final Function<Loan, String> dateSupplier) {
    if (!list.isEmpty()) {
      sb.append("<h3>").append(header).append(" (").append(list.size()).append(")").append("</h3>\n");
      sb.append("<table><thead><tr>");
      sb.append("<th>Låntagare</th>");
      sb.append("<th>").append(dateColumn).append("</th>");
      sb.append("<th>Författare</th>");
      sb.append("<th>Titel</th>");
      sb.append("</tr></thead>\n");
      sb.append("<tbody>\n");
      final List<Grouper.Group<Loan, User>> grouped =
          Grouper.groupBy(list, Loan::getUser);
      for (Grouper.Group<Loan, User> group : grouped) {
        for (Loan loan : group.getObjects()) {
          sb.append("<tr>");
          sb.append("<td>");
          sb.append(group.getKey().shortName);
          sb.append("</td>");
          sb.append("<td>");
          sb.append(dateSupplier.apply(loan));
          sb.append("</td>");
          sb.append("<td>");
          sb.append(loan.author);
          sb.append("</td>");
          sb.append("<td>");
          sb.append(loan.title);
          sb.append("</td>");
          sb.append("</tr>\n");
        }
      }
      sb.append("</tbody></table>\n");
    }

  }
  private void showUsers(
      final StringBuilder sb,
      final Collection<User> users) {
    if (!users.isEmpty()) {
      sb.append("<h3>").append("Låntagare").append("</h3>\n");
      sb.append("<table><thead><tr>");
      sb.append("<th>Förkortning</th>");
      sb.append("<th>Namn</th>");
      sb.append("</tr></thead>\n");
      sb.append("<tbody>\n");
      for (User user : users) {
        sb.append("<tr>");
        sb.append("<td>");
        sb.append(user.shortName);
        sb.append("</td>");
        sb.append("<td>");
        sb.append(user.displayName);
        sb.append("</td>");
        sb.append("</tr>\n");

      }
      sb.append("</tbody></table>\n");
    }
  }

  private String dateSpan(final String date) {
    return span(date, "date");
  }

  private String historic(final String date) {
    return span(date, "color-past");
  }

  private String span(final String s, final String clazz) {
    return "<span class=\"" + clazz + "\">" + s + "    </span>";
  }

  private JSONArray readJsonArray(final File file) throws JSONException, IOException {
    return new JSONArray(FileUtils.readFileToString(file, Charsets.UTF_8));
  }

  private static class Loan implements Comparable<Loan> {
    private final String id;
    private final User user;
    private final String author;
    private final String title;
    private final String returnDate;
    private final boolean renewable;

    public Loan(User user, JSONObject data) throws JSONException {
      this.user = user;
      this.id = data.getString("id");
      this.author = data.getString("workAuthor");
      this.title = data.getString("workTitle");
      this.returnDate = scrubDate(data.getString("returnDate"));
      this.renewable = data.getBoolean("isRenewable");
    }

    public User getUser() {
      return user;
    }

    @Override
    public int compareTo(final Loan o) {
      return id.compareTo(o.id);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final Loan loan = (Loan) o;

      return id.equals(loan.id);
    }

    @Override
    public int hashCode() {
      return id.hashCode();
    }
  }

  private static String scrubDate(final String s) {
    return s.replace("T00:00:00", "").replace("T23:59:59", "");
  }

  private static class Reservation implements Comparable<Reservation> {
    private final String id;
    private final User user;
    private final String reservedFrom;
    private final String author;
    private final String title;
    private final String lastFetchDate;
    private final int queueNumber;

    public Reservation(User user, JSONObject data) throws JSONException {
      this.user = user;
      this.id = data.getString("id");
      this.author = data.getString("workAuthor");
      this.title = data.getString("workTitle");
      this.reservedFrom = scrubDate(data.getString("reservedFrom"));
      if (data.getString("status").equals("fetchable")) {
        this.lastFetchDate = scrubDate(data.getString("lastFetchDate"));
      } else {
        this.lastFetchDate = "";
      }
      this.queueNumber = data.getInt("queueNumber");
    }

    public User getUser () {
      return user;
    }

    @Override
    public int compareTo(final Reservation o) {
      return id.compareTo(o.id);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final Reservation that = (Reservation) o;

      return id.equals(that.id);
    }

    @Override
    public int hashCode() {
      return id.hashCode();
    }
  }

  private static class User implements Comparable<User> {
    private final String userId;
    private final String username;
    private final String displayName;
    private final String shortName;

    private User(final String userId, final String username, final String displayName, final String shortName) {
      this.userId = userId;
      this.username = username;
      this.displayName = displayName;
      this.shortName = shortName;
    }

    @Override
    public int compareTo(final User o) {
      return shortName.compareTo(o.shortName);
    }
  }
}
