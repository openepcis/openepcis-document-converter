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

import io.openepcis.model.epcis.EPCISEvent;
import java.util.List;

/**
 * Functional interface for transforming EPCIS events during conversion.
 *
 * <p>This interface provides a type-safe way to transform events during the conversion
 * pipeline. Common use cases include:
 * <ul>
 *   <li>Enriching events with additional data (e.g., from master data lookups)</li>
 *   <li>Filtering events based on business rules (return null to filter out)</li>
 *   <li>Transforming event fields (e.g., normalizing timestamps, URIs)</li>
 *   <li>Adding or removing extensions</li>
 * </ul>
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * EPCISEventMapper mapper = (event, context) -> {
 *     // Enrich event with location name
 *     if (event instanceof ObjectEvent objectEvent) {
 *         String locationName = lookupLocationName(objectEvent.getReadPoint());
 *         // Add to extensions...
 *     }
 *     return event;
 * };
 *
 * ReactiveVersionTransformer transformer = ReactiveVersionTransformer.builder()
 *     .eventMapper(mapper)
 *     .build();
 * }</pre>
 *
 * <p><strong>Thread safety:</strong> Implementations must be thread-safe if the transformer
 * is used concurrently.
 *
 * @see ReactiveVersionTransformer.Builder#eventMapper(EPCISEventMapper)
 */
@FunctionalInterface
public interface EPCISEventMapper {

  /**
   * Transforms an EPCIS event.
   *
   * <p>The mapper receives the event and an optional context list. The context is
   * currently reserved for future use (e.g., passing parent document metadata)
   * and is typically empty.
   *
   * @param event the event to transform (ObjectEvent, AggregationEvent, TransactionEvent,
   *              TransformationEvent, or AssociationEvent)
   * @param context additional context (currently empty, reserved for future use)
   * @return the transformed event, or {@code null} to filter out the event
   */
  EPCISEvent apply(EPCISEvent event, List<Object> context);

  /**
   * Creates an identity mapper that returns events unchanged.
   *
   * @return an identity mapper
   */
  static EPCISEventMapper identity() {
    return (event, context) -> event;
  }

  /**
   * Creates a mapper that filters events based on a predicate.
   *
   * @param predicate the predicate to test events
   * @return a filtering mapper
   */
  static EPCISEventMapper filtering(java.util.function.Predicate<EPCISEvent> predicate) {
    return (event, context) -> predicate.test(event) ? event : null;
  }

  /**
   * Converts this typed mapper to a BiFunction for backward compatibility.
   *
   * @return a BiFunction representation of this mapper
   */
  default java.util.function.BiFunction<Object, List<Object>, Object> toBiFunction() {
    return (obj, ctx) -> {
      if (obj instanceof EPCISEvent event) {
        return apply(event, ctx);
      }
      return obj;
    };
  }

  /**
   * Creates an EPCISEventMapper from a BiFunction.
   *
   * @param biFunction the BiFunction to wrap
   * @return an EPCISEventMapper that delegates to the BiFunction
   */
  static EPCISEventMapper fromBiFunction(
      java.util.function.BiFunction<Object, List<Object>, Object> biFunction) {
    return (event, context) -> {
      Object result = biFunction.apply(event, context);
      return result instanceof EPCISEvent ? (EPCISEvent) result : null;
    };
  }
}
