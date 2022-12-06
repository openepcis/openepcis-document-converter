package io.openepcis.convert;

public enum EpcisVersion {
  VERSION_2_0("2.0"),
  VERSION_1_2("1.2");

  private final String version;

  public String getVersion() {
    return this.version;
  }

  EpcisVersion(final String version) {
    this.version = version;
  }
}
