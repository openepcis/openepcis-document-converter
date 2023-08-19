package io.openepcis.convert;

import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import lombok.Getter;

@Getter
public class Conversion {

    private EPCISFormat fromMediaType;
    private EPCISVersion fromVersion;
    private EPCISFormat toMediaType;
    private EPCISVersion toVersion;
    private boolean generateGS1CompliantDocument;

    private Conversion() {
    }

    public static FromMediaTypeStage newBuilder() {
        return new Stages();
    }

    public interface FromMediaTypeStage {
        ToVersionStage fromMediaType(EPCISFormat fromMediaType);
    }

    public interface ToVersionStage {
        ToVersionStage fromVersion(EPCISVersion fromVersion);
        GS1ComplianceStage toVersion(EPCISVersion toVersion);
        ToVersionStage toMediaType(EPCISFormat toMediaType);
    }

    public interface GS1ComplianceStage {
        BuildStage generateGS1CompliantDocument(boolean generateGS1CompliantDocument);
    }
    public interface BuildStage {
        Conversion build();
    }

    private static class Stages implements FromMediaTypeStage, ToVersionStage, BuildStage, GS1ComplianceStage {

        private EPCISFormat fromMediaType;
        private EPCISVersion fromVersion;
        private EPCISFormat toMediaType;
        private EPCISVersion toVersion;
        private boolean generateGS1CompliantDocument;

        @Override
        public ToVersionStage fromMediaType(EPCISFormat fromMediaType) {
            this.fromMediaType = fromMediaType;
            return this;
        }

        @Override
        public ToVersionStage fromVersion(EPCISVersion fromVersion) {
            this.fromVersion = fromVersion;
            return this;
        }

        @Override
        public ToVersionStage toMediaType(EPCISFormat toMediaType) {
            this.toMediaType = toMediaType;
            return this;
        }

        @Override
        public GS1ComplianceStage toVersion(EPCISVersion toVersion) {
            this.toVersion = toVersion;
            return this;
        }

        @Override
        public BuildStage generateGS1CompliantDocument(boolean generateGS1CompliantDocument) {
            this.generateGS1CompliantDocument = generateGS1CompliantDocument;
            return this;
        }

        @Override
        public Conversion build() {
            Conversion conversion = new Conversion();
            conversion.fromMediaType = this.fromMediaType;
            conversion.fromVersion = this.fromVersion;
            conversion.toMediaType = toMediaType != null ? toMediaType : this.fromMediaType != null ? this.fromMediaType : EPCISFormat.JSON_LD;
            conversion.toVersion = toVersion != null ? toVersion : this.fromVersion != null ? this.fromVersion : EPCISVersion.VERSION_2_0_0;
            conversion.generateGS1CompliantDocument = this.generateGS1CompliantDocument;

            return conversion;
        }
    }
}
