package com.io.openepcis.version;

import io.openepcis.convert.version.XmlTransformer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class LowerToHigherTest {

  @Test
  public void converLowerToHigher() {
    final InputStream xmlStream = getClass().getResourceAsStream("/version/LowerVersionXml.xml");
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    XmlTransformer.fromLower(xmlStream)
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
