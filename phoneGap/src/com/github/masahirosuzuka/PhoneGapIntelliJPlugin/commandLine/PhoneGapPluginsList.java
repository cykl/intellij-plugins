package com.github.masahirosuzuka.PhoneGapIntelliJPlugin.commandLine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.NetUtils;
import com.intellij.webcore.packaging.InstalledPackage;
import com.intellij.webcore.packaging.RepoPackage;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class PhoneGapPluginsList {
  public static final String PLUGINS_URL = "http://registry.cordova.io/-/all";

  public static volatile Map<String, PhoneGapRepoPackage> CACHED_REPO;

  private static boolean isExcludedProperty(String name) {
    return "_updated".equals(name);
  }

  private static final Lock lock = new ReentrantLock();

  public static final class PhoneGapRepoPackage extends RepoPackage {
    private final String myDesc;

    public PhoneGapRepoPackage(String name, JsonObject jsonObject) {
      super(name, PLUGINS_URL, getVersionLatest(jsonObject.getAsJsonObject()));
      myDesc = getDescr(jsonObject);
    }

    private static String getDescr(JsonObject jsonObject) {
      JsonElement descriptionElement = jsonObject.get("description");
      return descriptionElement == null ? "" : descriptionElement.getAsString();
    }

    private static String getVersionLatest(JsonObject jsonObject) {
      JsonElement element = jsonObject.get("dist-tags");
      if (element == null || !element.isJsonObject()) {
        return null;
      }
      JsonObject asObject = element.getAsJsonObject();
      JsonElement latest = asObject.get("latest");
      return latest == null ? null : latest.getAsString();
    }

    public String getDesc() {
      return myDesc;
    }
  }

  public static PhoneGapRepoPackage getPackage(String name) {
    return mapCached().get(name);
  }

  public static List<RepoPackage> listCached() {
    return ContainerUtil.<RepoPackage>newArrayList(mapCached().values());
  }

  public static Map<String, PhoneGapRepoPackage> mapCached() {
    Map<String, PhoneGapRepoPackage> value = CACHED_REPO;
    if (value == null) {
      lock.lock();
      try {
        value = CACHED_REPO;
        if (value == null) {
          value = listNoCache();
          CACHED_REPO = value;
        }
      }
      finally {
        lock.unlock();
      }
    }
    return value;
  }

  private static Map<String, PhoneGapRepoPackage> listNoCache() {
    HttpURLConnection urlConnection = null;
    Map<String, PhoneGapRepoPackage> result = ContainerUtil.newHashMap();
    try {
      urlConnection = HttpConfigurable.getInstance().openHttpConnection(PLUGINS_URL);
      int timeout = (int)TimeUnit.SECONDS.toMillis(30);
      urlConnection.setConnectTimeout(timeout);
      urlConnection.setReadTimeout(timeout);
      urlConnection.connect();

      InputStream rawStream = urlConnection.getInputStream();
      int contentLength = urlConnection.getContentLength();
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      NetUtils.copyStreamContent(null, rawStream, out, contentLength);

      JsonParser jsonParser = new JsonParser();
      final JsonElement jsonElement = jsonParser.parse(out.toString());

      JsonObject object = jsonElement.getAsJsonObject();
      for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
        if (!isExcludedProperty(entry.getKey())) {
          result.put(entry.getKey(), new PhoneGapRepoPackage(entry.getKey(), entry.getValue().getAsJsonObject()));
        }
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
    finally {
      if (urlConnection != null) {
        urlConnection.disconnect();
      }
    }
    return result;
  }

  public static void resetCache() {
    CACHED_REPO = null;
  }

  public static List<InstalledPackage> wrapInstalled(List<String> names) {
    return ContainerUtil.map(names, new Function<String, InstalledPackage>() {
      @Override
      public InstalledPackage fun(String s) {
        String[] split = s.split(" ");
        String name = ArrayUtil.getFirstElement(split);
        String version = split.length > 1 ? split[1] : "";
        return new InstalledPackage(name, version);
      }
    });
  }

  public static List<RepoPackage> wrapRepo(List<String> names) {
    return ContainerUtil.map(names, new Function<String, RepoPackage>() {
      @Override
      public RepoPackage fun(String s) {
        return new RepoPackage(s, s);
      }
    });
  }
}
