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
package io.openepcis.converter;

import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import java.util.Optional;
import java.util.function.Consumer;

public class Conversion {

  private EPCISFormat fromMediaType;
  private EPCISVersion fromVersion;
  private EPCISFormat toMediaType;
  private EPCISVersion toVersion;
  private Boolean generateGS1CompliantDocument = null;
  private Consumer<Throwable> onFailure = null;

  public static final Conversion UNKNOWN = Conversion.of(null, null, null, null);

  private Conversion() {}

  static Conversion of(
      final EPCISFormat fromMediaType,
      final EPCISVersion fromVersion,
      final EPCISFormat toMediaType,
      final EPCISVersion toVersion,
      final Boolean generateGS1CompliantDocument,
      final Consumer<Throwable> onFailure) {
    final Conversion conversion = new Conversion();
    conversion.fromMediaType = fromMediaType;
    conversion.fromVersion = fromVersion;
    conversion.toMediaType = toMediaType;
    conversion.toVersion = toVersion;
    conversion.generateGS1CompliantDocument = generateGS1CompliantDocument;
    conversion.onFailure = onFailure;
    return conversion;
  }
  static Conversion of(
          final EPCISFormat fromMediaType,
          final EPCISVersion fromVersion,
          final EPCISFormat toMediaType,
          final EPCISVersion toVersion,
          final Boolean generateGS1CompliantDocument) {
    final Conversion conversion = new Conversion();
    conversion.fromMediaType = fromMediaType;
    conversion.fromVersion = fromVersion;
    conversion.toMediaType = toMediaType;
    conversion.toVersion = toVersion;
    conversion.generateGS1CompliantDocument = generateGS1CompliantDocument;
    return of(fromMediaType, fromVersion, toMediaType, toVersion, true, null);
  }

  public static Conversion of(
      final EPCISFormat fromMediaType,
      final EPCISVersion fromVersion,
      final EPCISFormat toMediaType,
      final EPCISVersion toVersion) {
    return of(fromMediaType, fromVersion, toMediaType, toVersion, true);
  }
  public static Conversion of(
          final EPCISFormat fromMediaType,
          final EPCISVersion fromVersion,
          final EPCISFormat toMediaType,
          final EPCISVersion toVersion,
          final Consumer<Throwable> onFailure) {
    return of(fromMediaType, fromVersion, toMediaType, toVersion, true, onFailure);
  }

  public EPCISFormat fromMediaType() {
    return fromMediaType;
  }

  public EPCISVersion fromVersion() {
    return fromVersion;
  }

  public EPCISFormat toMediaType() {
    return toMediaType;
  }

  public EPCISVersion toVersion() {
    return toVersion;
  }

  public Optional<Boolean> generateGS1CompliantDocument() {
    return Optional.ofNullable(generateGS1CompliantDocument);
  }

  public Optional<Consumer<Throwable>> onFailure() {
    return Optional.ofNullable(onFailure);
  }
  public Conversion onFailure(Consumer<Throwable> consumer) {
    this.onFailure = consumer; return this;
  }
  public static StartStage builder() {
    return new Stages();
  }

  public void fail(Throwable throwable) {
    if (onFailure != null) onFailure.accept(throwable);
  }

  public interface StartStage {
    FromMediaTypeStage fromMediaType(EPCISFormat fromMediaType);

    StartStage generateGS1CompliantDocument(Boolean generateGS1CompliantDocument);
    StartStage onFailure(Consumer<Throwable> consumer);
  }

  public interface FromMediaTypeStage {

    FromVersionStage fromVersion(EPCISVersion fromVersion);

    ToMediaTypeStage toMediaType(EPCISFormat toMediaType);

    BuildStage toVersion(EPCISVersion toVersion);
  }

  public interface FromVersionStage {

    ToMediaTypeStage toMediaType(EPCISFormat toMediaType);

    BuildStage toVersion(EPCISVersion toVersion);
  }

  public interface ToMediaTypeStage {

    BuildStage toVersion(EPCISVersion toVersion);
  }

  public interface BuildStage {
    Conversion build();
  }

  private static class Stages
      implements StartStage, FromMediaTypeStage, FromVersionStage, ToMediaTypeStage, BuildStage {

    private EPCISFormat fromMediaType;
    private EPCISVersion fromVersion;
    private EPCISFormat toMediaType;
    private EPCISVersion toVersion;
    private Boolean generateGS1CompliantDocument = null;
    private Consumer<Throwable> onFailure = null;

    @Override
    public FromMediaTypeStage fromMediaType(EPCISFormat fromMediaType) {
      this.fromMediaType = fromMediaType;
      return this;
    }

    @Override
    public StartStage generateGS1CompliantDocument(final Boolean generateGS1CompliantDocument) {
      this.generateGS1CompliantDocument = generateGS1CompliantDocument;
      return this;
    }

    @Override
    public StartStage onFailure(final Consumer<Throwable> consumer) {
      this.onFailure = consumer;
      return this;
    }

    @Override
    public FromVersionStage fromVersion(EPCISVersion fromVersion) {
      this.fromVersion = fromVersion;
      return this;
    }

    @Override
    public ToMediaTypeStage toMediaType(EPCISFormat toMediaType) {
      this.toMediaType = toMediaType;
      return this;
    }

    @Override
    public BuildStage toVersion(EPCISVersion toVersion) {
      if (toMediaType == null) {
        toMediaType = fromMediaType;
      }
      this.toVersion = toVersion;
      return this;
    }

    @Override
    public Conversion build() {
      return Conversion.of(
          this.fromMediaType,
          this.fromVersion,
          this.toMediaType,
          toVersion != null ? toVersion : this.fromVersion,
          generateGS1CompliantDocument,
          onFailure
      );
    }
  }
}
