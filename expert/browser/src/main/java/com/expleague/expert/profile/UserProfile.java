package com.expleague.expert.profile;

import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.spbsu.commons.io.StreamTools;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by solar on 04.02.16.
 */
public class UserProfile extends WeakListenerHolderImpl<UserProfile.Key>{
  private static final Logger log = Logger.getLogger(UserProfile.class.getName());
  private final File propertiesFile;
  private Properties userProperties = new Properties();

  public UserProfile(File profileDirectory) throws IOException {
    if (profileDirectory == null) {
      propertiesFile = null;
      userProperties.load(new InputStreamReader(getClass().getResourceAsStream("/default-user.properties"), StreamTools.UTF));
      return;
    }
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
      if (propertiesFile != null)
        userProperties.store(new OutputStreamWriter(new FileOutputStream(propertiesFile), StreamTools.UTF), "User properties file");
    } catch (IOException e) {
      log.log(Level.SEVERE, "Unable to store user properties", e);
    }
  }

  public boolean isRegistered() {
    return has(Key.EXP_LEAGUE_ID);
  }

  public boolean has(Key key) {
    return userProperties.containsKey(key.name);
  }

  public <T> void set(Key key, T value) {
    if (value == null || !key.valueType.isAssignableFrom(value.getClass())) {
      throw new IllegalArgumentException();
    }
    userProperties.setProperty(key.name, value.toString());
    onChange();
    invoke(key);
  }

  public <T> T get(Key key) {
    try {
      if (key.valueType != String.class) {
        final Constructor<?> constructor = key.valueType.getConstructor(String.class);
        //noinspection unchecked
        return (T)constructor.newInstance(userProperties.getProperty(key.name));
      }
      //noinspection unchecked
      return (T) userProperties.getProperty(key.name);
    }
    catch (InstantiationException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public void copy(UserProfile profile) {
    this.userProperties.putAll(profile.userProperties);
    onChange();
  }

  public String name() {
    return get(Key.NAME);
  }

  public enum Key {
    EXP_LEAGUE_ID("com.expleague.jid", String.class),
    EXP_LEAGUE_DOMAIN("com.expleague.domain", String.class),
    EXP_LEAGUE_USER("com.expleague.user", String.class),
    EXP_LEAGUE_PASSWORD("com.expleague.secret", String.class),
    VK_TOKEN("com.expleague.vk.token", String.class),
    VK_USER_ID("com.expleague.vk.id", String.class),
    CITY("com.expleague.city", String.class),
    COUNTRY("com.expleague.country", String.class),
    AVATAR_URL("com.expleague.avatar", String.class),
    NAME("com.expleague.name", String.class),
    ;

    private final Class<?> valueType;
    private final String name;

    Key(String name, Class<?> valueType) {
      this.valueType = valueType;
      this.name = name;
    }

  }

}
