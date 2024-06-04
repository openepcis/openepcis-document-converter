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
package io.openepcis.convert.collector;

import java.util.List;

/**
 * Class that implements the EPCISEventsCollector to collect the converted Events into the List if
 * user has provided List as a type in the Handler
 */
public class EventListCollector implements EPCISEventCollector<List<String>> {

  private final List<String> events;

  public EventListCollector(List<String> events) {
    this.events = events;
  }

  @Override
  public void collect(Object event) {
    // Add the events to List
    events.add(event.toString());
  }

  @Override
  public List<String> get() {
    return events;
  }

  @Override
  public void collectSingleEvent(Object event) {
    events.add(event.toString());
  }

  @Override
  public boolean isEPCISDocument() {
    return false;
  }
}
