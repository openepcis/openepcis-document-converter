/*
 * Copyright 2022-2024 benelog GmbH & Co. KG
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */
package io.openepcis.convert.xml;

import io.openepcis.constants.EPCISVersion;
import io.openepcis.convert.Conversion;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

public interface XmlVersionTransformer {

  public InputStream xmlConverter(
          InputStream inputStream, final Function<Conversion.StartStage, Conversion.BuildStage> fn)
          throws UnsupportedOperationException, IOException;

  public InputStream xmlConverter(
          InputStream inputStream, Conversion conversion)
      throws UnsupportedOperationException, IOException;

  static XmlVersionTransformer newInstance() {
    return newInstance(Executors.newWorkStealingPool());
  }

  static XmlVersionTransformer newInstance(Class<? extends XmlVersionTransformerFactory> factory) {
    return newInstance(Executors.newWorkStealingPool(), factory);
  }

  static XmlVersionTransformer newInstance(final ExecutorService executorService) {
    final Optional<XmlVersionTransformerFactory> optionalFactory =
        ServiceLoader.load(XmlVersionTransformerFactory.class).findFirst();
    if (optionalFactory.isPresent()) {
      return optionalFactory.get().newXmlVersionTransformer(executorService);
    }
    return new DefaultXmlVersionTransformer(executorService);
  }

  static XmlVersionTransformer newInstance(
      final ExecutorService executorService, Class<? extends XmlVersionTransformerFactory> factory)
      throws IllegalArgumentException {
    final Optional<ServiceLoader.Provider<XmlVersionTransformerFactory>> optionalFactory =
        ServiceLoader.load(XmlVersionTransformerFactory.class).stream()
            .filter(f -> f.type().equals(factory))
            .findFirst();
    if (optionalFactory.isPresent()) {
      return optionalFactory.get().get().newXmlVersionTransformer(executorService);
    }
    throw new IllegalArgumentException(
        "no XmlVersionTransformerFactory found for class " + factory.getName());
  }
}
