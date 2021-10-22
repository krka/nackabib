package se.krka.nackabib;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class Dedup {
  public static void dedup(final File baseDir) throws IOException {
    final File[] children = baseDir.listFiles();
    if (children == null) {
      return;
    }
    System.out.println("Running deduper");
    final List<File> files = Arrays.stream(children)
            .filter(file -> !file.getName().endsWith("inprogress"))
            .sorted(Comparator.comparing(File::getName)).collect(Collectors.toList());
    String prev1 = "";
    String prev2 = "";
    File prev2File = null;

    for (File file : files) {
      String hash = hash(file);
      if (hash.equals(prev2) && prev2.equals(prev1)) {
        System.out.println("Deleting " + prev2File);
        FileUtils.deleteDirectory(prev2File);
      }
      prev1 = prev2;
      prev2 = hash;
      prev2File = file;
    }

    System.out.println("Done with deduping");
  }

  private static String hash(File file) throws IOException {
    if (file.isDirectory()) {
      final Hasher hasher = Hashing.sha1().newHasher();
      final List<File> sortedChildren = Arrays.stream(file.listFiles()).sorted(Comparator.comparing(File::getName)).collect(Collectors.toList());
      for (File child : sortedChildren) {
        hasher.putString(hash(child), StandardCharsets.US_ASCII);
      }
      return hasher.hash().toString();
    } else {
      return Files.hash(file, Hashing.sha1()).toString();
    }
  }

}
