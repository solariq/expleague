package com.expleague.expert.vk;

import com.expleague.expert.profile.UserProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spbsu.commons.io.StreamTools;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Experts League
 * Created by solar on 09/02/16.
 */
public class VkUtils {
  private static final Logger log = Logger.getLogger(VkUtils.class.getName());
  public static final String VK_API_URL = "https://api.vk.com/method/";

  public static void fillProfile(UserProfile profile) {
    try {
      final URL vkApiUrl = new URL(VK_API_URL + "users.get?user_ids=" + profile.get(UserProfile.Key.VK_USER_ID) + "&fields=photo_max,city,country&v=5.45&lang=ru");
      final HttpURLConnection urlConnection = (HttpURLConnection)vkApiUrl.openConnection();
      urlConnection.setRequestProperty("Accept-Language", "ru");
      final CharSequence contents = StreamTools.readStream(urlConnection.getInputStream());
      final UsersGetResponse usersResponse = new ObjectMapper().readValue(contents.toString(), UsersGetResponse.class);
      final User user = usersResponse.response[0];
      profile.set(UserProfile.Key.NAME, user.first_name + " " + user.last_name);
      profile.set(UserProfile.Key.CITY, user.city.title);
      profile.set(UserProfile.Key.COUNTRY, user.country.title);
      profile.set(UserProfile.Key.AVATAR_URL, user.photo_max);
    }
    catch (IOException e) {
      log.log(Level.WARNING, "Unable to populate user profile", e);
    }
  }

  private static class UsersGetResponse {
    public User[] response;
  }

  private static class User {
    public long id;
    public String first_name;
    public String last_name;
    public City city;
    public Country country;
    public String photo_max;
  }

  private static class City {
    public long id;
    public String title;
  }

  private static class Country {
    public long id;
    public String title;
  }
}
