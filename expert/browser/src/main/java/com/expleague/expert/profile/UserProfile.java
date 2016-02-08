package com.expleague.expert.profile;

import com.spbsu.commons.io.StreamTools;

import java.io.*;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by solar on 04.02.16.
 */
public class UserProfile {
  private static final Logger log = Logger.getLogger(UserProfile.class.getName());
  public static final String LOGIN_PROPERTY = "com.expleague.login";

  private final File propertiesFile;
  private Properties userProperties = new Properties();

  public UserProfile(File profileDirectory) throws IOException {
    //noinspection ResultOfMethodCallIgnored
    profileDirectory.mkdirs();
    propertiesFile = new File(profileDirectory, "user.properties");
    if (propertiesFile.exists()) {
      userProperties.load(new InputStreamReader(new FileInputStream(propertiesFile), StreamTools.UTF));
    }
    else {
      userProperties.load(new InputStreamReader(getClass().getResourceAsStream("/default-user.properties"), StreamTools.UTF));
    }
  }

  private void onChange() {
    try {
      userProperties.store(new OutputStreamWriter(new FileOutputStream(propertiesFile), StreamTools.UTF), "User properties file");
    } catch (IOException e) {
      log.log(Level.SEVERE, "Unable to store user properties", e);
    }
  }

  public boolean isRegistered() {
    return userProperties.containsKey(LOGIN_PROPERTY);
  }
}
