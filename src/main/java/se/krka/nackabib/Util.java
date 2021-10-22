package se.krka.nackabib;

import com.google.common.collect.Lists;
import java.io.File;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.Comparator;
import java.util.List;

public class Util {

  public static final Clock CLOCK = Clock.systemUTC();
  public static final ZoneId ZONE = CLOCK.getZone();
  public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZONE);

  public static String getCurrentTimestamp() {
    return FORMATTER.format(CLOCK.instant());
  }

  public static List<File> getDirectories(final File baseDir) {
    final List<File> list = Lists.newArrayList();
    for (File file : baseDir.listFiles()) {
      if (file.isDirectory() && isTimestamp(file)) {
        list.add(file);
      }
    }
    list.sort(Comparator.comparing(File::getName));
    return list;
  }

  public static Duration timeSinceLastUpdate(final File baseDir) {
    final List<File> directories = getDirectories(baseDir);
    if (directories.isEmpty()) {
      return Duration.ofDays(100);
    }
    final File mostRecent = directories.get(directories.size() - 1);

    final Temporal now = Clock.systemDefaultZone().instant();
    final LocalDateTime latest = parseFilename(mostRecent);
    return Duration.between(latest.toInstant(ZoneOffset.UTC), now);
  }

  private static LocalDateTime parseFilename(final File dir) {
    return LocalDateTime.parse(dir.getName(), FORMATTER);
  }

  private static boolean isTimestamp(final File dir) {
    try {
      return parseFilename(dir) != null;
    } catch (Exception e) {
      return false;
    }
  }
}

