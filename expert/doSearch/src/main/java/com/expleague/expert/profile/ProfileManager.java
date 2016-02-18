package com.expleague.expert.profile;

import com.expleague.expert.vk.VkUtils;
import com.expleague.expert.xmpp.ExpLeagueConnection;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.spbsu.commons.io.StreamTools;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Experts League
 * Created by solar on 09/02/16.
 */
public class ProfileManager extends WeakListenerHolderImpl<UserProfile> {
  private static final Logger log = Logger.getLogger(ProfileManager.class.getName());
  public static final String ACTIVE_PROFILE_FILENAME = "active";
  private static ProfileManager instance;

  public static synchronized ProfileManager instance() {
    if (instance == null) {
      try {
        instance = new ProfileManager(new File(System.getProperty("user.home") + "/.expleague"));
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return instance;
  }

  private final Map<String, UserProfile> knownProfiles = new HashMap<>();
  private final File root;
  public ProfileManager(File home) throws IOException {
    root = home;
    if (home.listFiles() == null)
      return;

    UserProfile first = null;
    //noinspection ConstantConditions
    for (final File file : home.listFiles()) {
      if (file.isDirectory()) {
        final UserProfile value = new UserProfile(file);
        if (first == null)
          first = value;
        if (!value.get(UserProfile.Key.EXP_LEAGUE_ID).equals(file.getName())) {
          log.warning("Profile in directory " + file.getAbsolutePath() + " is invalid. Skipping it");
          continue;
        }
        knownProfiles.put(file.getName(), value);
      }
    }
    final File activeProfileFile = new File(root, ACTIVE_PROFILE_FILENAME);
    if (activeProfileFile.exists()) {
      activate(knownProfiles.get(StreamTools.readFile(activeProfileFile).toString().trim()));
    }
    if (active == null && first != null) {
      activate(first);
    }
  }

  public UserProfile[] profiles() {
    return knownProfiles.values().toArray(new UserProfile[knownProfiles.values().size()]);
  }

  private UserProfile active;
  public UserProfile active() {
    return active;
  }

  public UserProfile register(UserProfile profile) {
    final UserProfile userProfile;
    try {
      if (!profile.has(UserProfile.Key.EXP_LEAGUE_ID)) {
        if (!profile.has(UserProfile.Key.EXP_LEAGUE_DOMAIN))
          throw new IllegalArgumentException("ExpLeague domain was not set");
        if (profile.has(UserProfile.Key.VK_TOKEN)) {
          log.info("Registering user by vk profile");
          VkUtils.fillProfile(profile);
          final String userName = "vk-" + profile.get(UserProfile.Key.VK_USER_ID) + InetAddress.getLocalHost().getHostName();
          profile.set(UserProfile.Key.EXP_LEAGUE_USER, userName);
          profile.set(UserProfile.Key.EXP_LEAGUE_ID, profile.get(UserProfile.Key.EXP_LEAGUE_USER) + "@" + profile.get(UserProfile.Key.EXP_LEAGUE_DOMAIN));
          ExpLeagueConnection.instance().register(profile);
        }
      }
      final String profileId = profile.get(UserProfile.Key.EXP_LEAGUE_ID);
      userProfile = new UserProfile(new File(root, profileId));
      userProfile.copy(profile);
      knownProfiles.put(profileId, userProfile);
      return userProfile;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void activate(UserProfile profile) {
    if (!knownProfiles.containsValue(profile))
      throw new IllegalArgumentException("Unknown profile");
    final String id = profile.get(UserProfile.Key.EXP_LEAGUE_ID);
    if (id == null)
      throw new IllegalArgumentException("Profile is not registered");
    active = profile;
    try {
      StreamTools.writeChars(id, new File(root, ACTIVE_PROFILE_FILENAME));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    invoke(profile);
//    new Timer().schedule(new TimerTask() {
//      @Override
//      public void run() {
//        ExpLeagueConnection.instance().start(profile);
//      }
//    }, 1000);
  }
}
