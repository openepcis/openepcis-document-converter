package io.openepcis.convert;

import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;

import java.util.Optional;

public class Conversion {


    private EPCISFormat fromMediaType;
    private EPCISVersion fromVersion;
    private EPCISFormat toMediaType;
    private EPCISVersion toVersion;
    private Boolean generateGS1CompliantDocument = null;


    private Conversion() {
    }

     static Conversion of(final EPCISFormat fromMediaType,
                                      final EPCISVersion fromVersion,
                                      final EPCISFormat toMediaType,
                                      final EPCISVersion toVersion,
                                      final Boolean generateGS1CompliantDocument
    ) {
        final Conversion conversion = new Conversion();
        conversion.fromMediaType = fromMediaType;
        conversion.fromVersion = fromVersion;
        conversion.toMediaType = toMediaType;
        conversion.toVersion = toVersion;
        conversion.generateGS1CompliantDocument= generateGS1CompliantDocument;
        return conversion;
    }
    public static Conversion of(final EPCISFormat fromMediaType,
                                      final EPCISVersion fromVersion,
                                      final EPCISFormat toMediaType,
                                      final EPCISVersion toVersion
    ) {
        return of(fromMediaType, fromVersion, toMediaType, toVersion, true);
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

    public static StartStage builder() {
        return new Stages();
    }

    public interface StartStage  {
        FromMediaTypeStage fromMediaType(EPCISFormat fromMediaType);
        StartStage generateGS1CompliantDocument(Boolean generateGS1CompliantDocument);
        BuildStage toVersion(EPCISVersion toVersion);
    }

    public interface FromMediaTypeStage  {

        FromVersionStage fromVersion(EPCISVersion fromVersion);

        ToMediaTypeStage toMediaType(EPCISFormat toMediaType);

        BuildStage toVersion(EPCISVersion toVersion);

    }

    public interface FromVersionStage  {

        ToMediaTypeStage toMediaType(EPCISFormat toMediaType);

        BuildStage toVersion(EPCISVersion toVersion);

    }

    public interface ToMediaTypeStage  {

        BuildStage toVersion(EPCISVersion toVersion);

    }


    public interface BuildStage {
        Conversion build();
    }

    private static class Stages implements StartStage, FromMediaTypeStage, FromVersionStage, ToMediaTypeStage, BuildStage {

        private EPCISFormat fromMediaType;
        private EPCISVersion fromVersion;
        private EPCISFormat toMediaType;
        private EPCISVersion toVersion;
        private Boolean generateGS1CompliantDocument = null;

        @Override
        public FromMediaTypeStage fromMediaType(EPCISFormat fromMediaType) {
            this.fromMediaType = fromMediaType;
            return this;
        }

        @Override
        public StartStage generateGS1CompliantDocument(final Boolean generateGS1CompliantDocument) {
            this.generateGS1CompliantDocument = Boolean.valueOf(generateGS1CompliantDocument);
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
                    generateGS1CompliantDocument
            );
        }
    }
}
