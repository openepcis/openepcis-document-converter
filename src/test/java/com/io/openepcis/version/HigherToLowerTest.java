package com.io.openepcis.version;

import io.openepcis.convert.version.XmlTransformer;
import io.smallrye.mutiny.Uni;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.ws.rs.core.StreamingOutput;
import org.junit.Test;

public class HigherToLowerTest {

  @Test
  public void convertHigherToLower() {
    final InputStream xmlStream = getClass().getResourceAsStream("/version/HigherVersionXml.xml");
    final Uni<StreamingOutput> lowerVersionXml = XmlTransformer.fromHigher(xmlStream);
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    lowerVersionXml
        .subscribe()
        .with(
            item -> {
              try {
                item.write(output);
              } catch (IOException e) {
                e.printStackTrace();
              }
            });
    System.out.println(output.toString(StandardCharsets.UTF_8));
  }
}
