/*
 * Copyright 2022-2025 benelog GmbH & Co. KG
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
package io.openepcis.converter.reactive;

/**
 * Factory interface for creating ReactiveXmlVersionTransformer instances.
 *
 * <p>Implementations can be discovered via {@link java.util.ServiceLoader}.
 * If no implementation is found, the default XSLT-based transformer is used.
 *
 * <p>To provide a custom implementation (e.g., SAX-based), create a class that
 * implements this interface and register it in:
 * {@code META-INF/services/io.openepcis.converter.reactive.ReactiveXmlVersionTransformerFactory}
 */
public interface ReactiveXmlVersionTransformerFactory {

  /**
   * Creates a new ReactiveXmlVersionTransformer instance.
   *
   * @return a new transformer instance
   */
  ReactiveXmlVersionTransformer newReactiveXmlVersionTransformer();
}
