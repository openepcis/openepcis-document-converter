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
package io.openepcis.convert.collector;

import io.openepcis.convert.exception.FormatConverterException;
import io.openepcis.convert.validator.EPCISEventValidator;
import java.util.Map;

/**
 * Class to delegate the incoming request to appropriate class to perform either XSD/Schema
 * validation or create final XML/JSON file after conversion
 */
public class EventHandler<R> implements EPCISEventValidator, EPCISEventCollector<R> {

  private EPCISEventValidator validator;
  private final EPCISEventCollector<R> collector;

  public EventHandler(EPCISEventValidator validator, EPCISEventCollector<R> collector) {
    if (validator == null && collector == null) {
      throw new FormatConverterException(
          "Invalid EventHandler, Both EventCollector and EventValidator cannot be Null");
    }
    this.validator = validator;
    this.collector = collector;
  }

  public EventHandler(EPCISEventCollector<R> collector) {
    this.collector = collector;
  }

  @Override
  public void validate(Object event) {
    if (validator != null) {
      validator.validate(event);
    }
  }

  @Override
  public void collect(Object event) {
    if (collector != null) {
      collector.collect(event);
    }
  }

  @Override
  public R get() {
    return collector != null ? collector.get() : null;
  }

  public void handler(Object event) {
    if (validator != null) {
      validator.validate(event);
    }

    if (collector != null) {
      collector.collect(event);
    }
  }

  @Override
  public void start(Map<String, String> context) {
    // Call the Start method if user has provided the Collector object ie do not call Start method
    // for only validation
    if (collector != null) {
      collector.start(context);
    }
  }

  @Override
  public void end() {
    // Call the End method if user has provided the Collector object ie do not call End method for
    // only validation
    if (collector != null) {
      collector.end();
    }
  }

  @Override
  public void collectSingleEvent(Object event) {
    if (collector != null) {
      collector.collectSingleEvent(event);
    }
  }

  @Override
  public void startSingleEvent(Map<String, String> context) {
    // Call the Start method if user has provided the Collector object ie do not call Start method
    // for only validation
    if (collector != null) {
      collector.startSingleEvent(context);
    }
  }

  @Override
  public void endSingleEvent() {
    // Call the End method if user has provided the Collector object ie do not call End method for
    // only validation
    if (collector != null) {
      collector.endSingleEvent();
    }
  }
}
