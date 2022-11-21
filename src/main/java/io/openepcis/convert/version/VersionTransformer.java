package io.openepcis.convert.version;

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
}
