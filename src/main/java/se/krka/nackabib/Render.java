package se.krka.nackabib;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Render {

  private final Map<String, String> displayNames = Maps.newHashMap();
  private final String scriptText;
  private final String style;
  private String mostRecentTimestamp;
  private List<Reservation> reservations;
  private List<Loan> history;
  private List<Loan> loans;

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

    mostRecentTimestamp = mostRecentName
        .replace("T", " kl ")
        .replaceAll(":[0-9]{2}\\.[0-9]{3}$", "");
    final Set<Loan> loansSet = getLoans(mostRecent);
    final Set<Loan> historySet = getHistory(allData);
    historySet.removeAll(loansSet);
    final Set<Reservation> reservationsSet = getReservations(mostRecent);

    addDisplayNames(mostRecent);
    for (File dir : allData) {
      addDisplayNames(dir);
    }

    loans = Lists.newArrayList(loansSet);
    loans.sort((o1, o2) -> ComparisonChain.start()
        .compare(o1.returnDate, o2.returnDate)
        .compare(o1.username, o2.username)
        .compare(o1.author, o2.author)
        .compare(o1.title, o2.title)
        .result());

    history = Lists.newArrayList(historySet);
    history.sort((o1, o2) -> ComparisonChain.start()
        .compare(o2.returnDate, o1.returnDate)
        .compare(o1.username, o2.username)
        .compare(o1.author, o2.author)
        .compare(o1.title, o2.title)
        .result());

    reservations = Lists.newArrayList(reservationsSet);
    reservations.sort((o1, o2) -> ComparisonChain.start()
        .compare(o2.lastFetchDate, o1.lastFetchDate)
        .compare(o1.reservedFrom, o2.reservedFrom)
        .compare(o1.username, o2.username)
        .compare(o1.author, o2.author)
        .compare(o1.title, o2.title)
        .result());
  }

  private void addDisplayNames(final File dir) throws IOException, JSONException {
    for (File subDir : dir.listFiles()) {
      if (!subDir.isDirectory()) {
        continue;
      }
      final String username = subDir.getName();
      final String displayName = readJsonArray(new File(subDir, "cards")).getJSONObject(0).getString("displayName");
      displayNames.putIfAbsent(username, displayName);
    }
  }

  private Set<Reservation> getReservations(final File dir) throws IOException, JSONException {
    final SortedSet<Reservation> set = Sets.newTreeSet();
    for (File subDir : dir.listFiles()) {
      if (subDir.isDirectory()) {
        final String username = subDir.getName();
        for (Object o : JsonIterator.of(readJsonArray(new File(subDir, "reservations")))) {
          set.add(new Reservation(username, (JSONObject) o));
        }
      }
    }
    return set;
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
        final String username = subDir.getName();
        for (Object o : JsonIterator.of(readJsonArray(new File(subDir, "loans")))) {
          set.add(new Loan(username, (JSONObject) o));
        }
      }
    }
  }

  public void toHtml(final File file) throws IOException {
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
    sb.append("<h3>Konton</h3>\n");
    sb.append(Joiner.on(", ").join(ImmutableSortedSet.copyOf(displayNames.values())));

    if (!reservations.isEmpty()) {
      sb.append("<h3>Reservationer</h3>\n");
      sb.append("<pre>\n");
      final List<Grouper.Group<Reservation, String>> grouped =
          Grouper.groupBy(reservations, Reservation::getUsername);
      for (Grouper.Group<Reservation, String> group : grouped) {
        sb.append(displayNames.get(group.getKey()));
        sb.append("\n");
        for (Reservation reservation : group.getObjects()) {
          sb.append("  ");
          if (!reservation.lastFetchDate.equals("")) {
            sb.append("Hämtas senast: ").append(dateSpan(reservation.lastFetchDate));
          } else {
            sb.append("Reservades:    ").append(historic(reservation.reservedFrom));
          }
          sb.append("  (");
          sb.append(reservation.queueNumber);
          sb.append(") ");
          sb.append(reservation.author);
          sb.append(": ");
          sb.append(reservation.title);
          sb.append("\n");
        }
      }
      sb.append("</pre>\n");
    }


    if (!loans.isEmpty()) {
      sb.append("<h3>Lån</h3>\n");
      sb.append("<pre>\n");
      for (Grouper.Group<Loan, String> group : Grouper.groupBy(loans, Loan::getUsername)) {
        sb.append(displayNames.get(group.getKey()));
        sb.append("\n");
        for (Loan loan : group.getObjects()) {
          sb.append("  ");
          sb.append(dateSpan(loan.returnDate));
          sb.append("  ");
          sb.append(loan.renewable ? "[L] " : "    ");
          sb.append(loan.author);
          sb.append(": ");
          sb.append(loan.title);
          sb.append("\n");
        }
      }
      sb.append("</pre>\n");
    }

    if (!history.isEmpty()) {
      sb.append("<h3>Historik</h3>\n");
      sb.append("<pre>\n");
      for (Grouper.Group<Loan, String> group : Grouper.groupBy(history, Loan::getUsername)) {
        sb.append(displayNames.get(group.getKey()));
        sb.append("\n");
        for (Loan loan : group.getObjects()) {
          sb.append("  ");
          sb.append(historic(loan.returnDate));
          sb.append("  ");
          sb.append(loan.renewable ? "[L] " : "    ");
          sb.append(loan.author);
          sb.append(": ");
          sb.append(loan.title);
          sb.append("\n");
        }
      }
      sb.append("</pre>\n");
    }

    sb.append("<script>\n").append(scriptText).append("\n</script>\n");
    sb.append("</body></html>\n");
    String s = sb.toString();

    FileUtils.write(file, s, Charsets.UTF_8);
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
    private final String username;
    private final String author;
    private final String title;
    private final String returnDate;
    private final boolean renewable;

    public Loan(String username, JSONObject data) throws JSONException {
      this.username = username;
      this.id = data.getString("id");
      this.author = data.getString("workAuthor");
      this.title = data.getString("workTitle");
      this.returnDate = data.getString("returnDate").replace("T00:00:00", "");
      this.renewable = data.getBoolean("isRenewable");
    }

    public String getUsername() {
      return username;
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

  private static class Reservation implements Comparable<Reservation> {
    private final String id;
    private final String username;
    private final String reservedFrom;
    private final String author;
    private final String title;
    private final String lastFetchDate;
    private final int queueNumber;

    public Reservation(String username, JSONObject data) throws JSONException {
      this.username = username;
      this.id = data.getString("id");
      this.author = data.getString("workAuthor");
      this.title = data.getString("workTitle");
      this.reservedFrom = data.getString("reservedFrom").replace("T00:00:00", "");
      if (data.getString("status").equals("fetchable")) {
        this.lastFetchDate = data.getString("lastFetchDate").replace("T00:00:00", "");
      } else {
        this.lastFetchDate = "";
      }
      this.queueNumber = data.getInt("queueNumber");
    }

    public String getUsername() {
      return username;
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
}
