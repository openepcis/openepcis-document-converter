package io.openepcis.convert;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AttributePreScanUtil {

  private static final String SCHEMA_VERSION_REGEX ="schemaVersion\"?'?\\s*[=:]\\s*([\"'])?([^\"']*)[\"?'?]";
  private static final Pattern SCHEMA_VERSION_PATTERN = Pattern.compile(SCHEMA_VERSION_REGEX);
  private static final int READ_LIMIT = 4096;
  public static final String scanSchemaVersion(final BufferedInputStream input) throws IOException {
    input.mark(READ_LIMIT);
    try  {
      final StringBuilder sb = new StringBuilder();
      final byte[] buffer = new byte[64];
      int len = -1;
      int bytesReceived = 0;
      Matcher matcher = SCHEMA_VERSION_PATTERN.matcher(sb.toString());
      while (!matcher.find(0) && bytesReceived < READ_LIMIT && (len = input.read(buffer)) != -1) {
        sb.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
        bytesReceived += len;
        matcher = SCHEMA_VERSION_PATTERN.matcher(sb.toString());
      }
      if (matcher.find(0)) {
        return matcher.group(2);
      }
      return "";
    } finally {
      input.reset();
    }
  }

}
