package se.krka.nackabib;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserConfig {

  private final Config config;

  private final Map<String, User> usersByUsername;

  public UserConfig(final File baseDir) {
    config = ConfigFactory.parseFile(FileUtils.getFile(baseDir, "bib.conf"));
    usersByUsername = new HashMap<>();

    final List<? extends Config> credentials = config.getConfigList("credentials");
    for (Config credential : credentials) {
      final String username = credential.getString("username");
      final String password = credential.getString("password");
      usersByUsername.put(username, new User(username, password));
    }
  }

  public Map<String, User> getUsersByUsername() {
    return usersByUsername;
  }

  class User {

    private final String username;
    private final String password;

    public User(String username, String password) {
      this.username = username;
      this.password = password;
    }

    public String getUsername() {
      return username;
    }

    public String getPassword() {
      return password;
    }
  }

}

