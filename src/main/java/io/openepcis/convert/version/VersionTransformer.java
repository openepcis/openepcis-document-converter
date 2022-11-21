package io.openepcis.convert.version;

import io.openepcis.convert.EPCISVersion;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VersionTransformer {

  private ExecutorService executorService;

  public VersionTransformer() {
    this.executorService = Executors.newWorkStealingPool();
  }

  public VersionTransformer(final ExecutorService executorService) {
    this.executorService = executorService;
  }

  /**
   * shortcut with autodetect ECPIS version from inputstream
   *
   * @param inputStream
   * @param fromMediaType
   * @param to
   * @return
   * @throws UnsupportedOperationException
   */
  public final InputStream convert(
      final InputStream inputStream, final String fromMediaType, final EPCISVersion to)
      throws UnsupportedOperationException, IOException {
    // pre scan 1024 bytes to detect version
    final byte[] prescan = new byte[1024];
    final int len = inputStream.read(prescan);
    final String preScanVersion = new String(prescan, StandardCharsets.UTF_8);
    final boolean mustConvert =
        preScanVersion.contains("schemaVersion=\"1.2\"")
            || preScanVersion.contains("schemaVersion='1.2'");
    if (!preScanVersion.contains("schemaVersion")) {
      throw new UnsupportedOperationException("unable to detect EPCIS schemaVersion");
    }
    return inputStream;
  }

  public final InputStream convert(
      final InputStream inputStream,
      final String mediaType,
      final EPCISVersion from,
      final EPCISVersion to)
      throws UnsupportedOperationException, IOException {
    return convert(inputStream, mediaType, from, mediaType, to);
  }

  public final InputStream convert(
      final InputStream inputStream,
      final String fromMediaType,
      final EPCISVersion from,
      final String toMediaType,
      final EPCISVersion to)
      throws UnsupportedOperationException, IOException {
    if (from.equals(to)) {
      return inputStream;
    }
    return null;
  }
}
