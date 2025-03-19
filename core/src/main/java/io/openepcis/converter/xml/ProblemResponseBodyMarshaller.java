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
package io.openepcis.converter.xml;

import io.openepcis.model.rest.ProblemResponseBody;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProblemResponseBodyMarshaller {

  private static Marshaller marshaller;

  static {
    try {
      JAXBContext context = JAXBContext.newInstance(ProblemResponseBody.class);
      marshaller = context.createMarshaller();
      marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
    } catch (JAXBException e) {
      log.error(e.getMessage(), e);
    }
  }

  public static Marshaller getMarshaller() {
    return marshaller;
  }

  public static void setMarshaller(Marshaller marshaller) {
    ProblemResponseBodyMarshaller.marshaller = marshaller;
  }
}
