package com.escontrela.lastmove.ui.support;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Provides the Maven project version embedded in the application resources. */
public final class ApplicationVersion {

  private static final String VERSION = loadVersion();

  private ApplicationVersion() {}

  public static String value() {
    return VERSION;
  }

  private static String loadVersion() {
    Properties properties = new Properties();
    try (InputStream input = ApplicationVersion.class.getResourceAsStream(
        "/application-version.properties")) {
      if (input != null) {
        properties.load(input);
        String version = properties.getProperty("version");
        if (version != null && !version.isBlank()) {
          return version.trim();
        }
      }
    } catch (IOException ignored) {
      // Fall through to package metadata for deployments with a custom resource layout.
    }

    String implementationVersion = ApplicationVersion.class.getPackage().getImplementationVersion();
    return implementationVersion == null || implementationVersion.isBlank()
        ? "development"
        : implementationVersion;
  }
}
