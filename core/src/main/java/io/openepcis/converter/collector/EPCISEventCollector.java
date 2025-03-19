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
package io.openepcis.converter.collector;

import java.util.Map;

/**
 * Interface to collect each of the converted events into the desired container based on User
 * provided OutputStream/List Type
 */
public interface EPCISEventCollector<R> extends AutoCloseable {

  // Method to store the event
  void collect(Object event);

  // Method to return the List of events
  R get();

  // collector lifecycle hook
  default void start(Map<String, String> context) {}

  // collector lifecycle hook
  default void end() {}

  // Method to collect single event
  void collectSingleEvent(Object event);

  // Method to create wrapper object for single event
  default void startSingleEvent(Map<String, String> context) {}

  // Method to close all wrapper object for single event
  default void endSingleEvent() {}

  default void setIsEPCISDocument(boolean isEPCISDocument) {}

  default void setSubscriptionID(String subscriptionID) {}

  default void setQueryName(String queryName) {}

  boolean isEPCISDocument();
}
