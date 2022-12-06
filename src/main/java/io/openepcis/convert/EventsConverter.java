/*
 * Copyright 2022 benelog GmbH & Co. KG
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
package io.openepcis.convert;

import io.openepcis.convert.collector.EPCISEventCollector;
import io.openepcis.convert.collector.EventHandler;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.io.InputStream;
import javax.xml.stream.XMLStreamException;

/**
 * Interface that will be implemented by the classes for converting the EPCIS events from one format
 * to another format
 *
 * <p>inputStream : Stream of EPCIS event inputs in XML/JSON format that needs to be converted.
 * handler : Tasks that need to be performed after completing the conversion of each event i.e to
 * Validate against XSD/JSON-Schema or creation of the final XML/JSON file based on the user
 * provided OutputStream type
 */
public interface EventsConverter {
  void convert(
      InputStream inputStream,
      EventHandler<? extends EPCISEventCollector> handler,
      JAXBContext jaxbContext)
      throws IOException, XMLStreamException, JAXBException;

  void convert(InputStream inputStream, EventHandler<? extends EPCISEventCollector> handler)
      throws IOException, XMLStreamException, JAXBException;
}
