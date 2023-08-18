package io.openepcis.convert;

import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import lombok.AccessLevel;
import lombok.Builder;

@Builder(builderClassName = "Builder")
public class Conversion {

    private final EPCISFormat fromMediaType;
    private final EPCISVersion fromVersion;
    private final EPCISFormat toMediaType;
    private final EPCISVersion toVersion;
    private final boolean generateGS1CompliantDocument;

    /**
     * @param fromMediaType                MediaType of the input EPCIS document i.e. application/xml or
     *                                     application/json
     * @param fromVersion                  Version of the provided input EPCIS document i.e. 1.2/2.0
     * @param toMediaType                  MediaType of the converted EPCIS document i.e. application/xml or
     *                                     application/json
     * @param toVersion                    Version to which provided document need to be converted to 1.2/2.0
     * @param generateGS1CompliantDocument generate GS1 compliant output only
     */
    private Conversion(final EPCISFormat fromMediaType,
                       final EPCISVersion fromVersion,
                       final EPCISFormat toMediaType,
                       final EPCISVersion toVersion,
                       boolean generateGS1CompliantDocument
    ) {
        // TODO verify setup from/to depending on what is missing (==null)
        this.fromMediaType = fromMediaType;
        this.fromVersion = fromVersion;
        this.toMediaType = toMediaType != null ? toMediaType : fromMediaType != null ? fromMediaType : EPCISFormat.JSON_LD;
        this.toVersion = toVersion != null ? toVersion : fromVersion != null ? fromVersion : EPCISVersion.VERSION_2_0_0;
        this.generateGS1CompliantDocument = generateGS1CompliantDocument;
    }

    public static final Conversion of(
            final EPCISFormat fromMediaType,
            final EPCISVersion fromVersion,
            final EPCISFormat toMediaType,
            final EPCISVersion toVersion,
            boolean generateGS1CompliantDocument
    ) {
        return new Conversion(fromMediaType, fromVersion, toMediaType, toVersion, generateGS1CompliantDocument);
    }

    public static final Conversion of(
            final EPCISFormat fromMediaType,
            final EPCISVersion fromVersion,
            final EPCISFormat toMediaType,
            final EPCISVersion toVersion
    ) {
        return of(fromMediaType, fromVersion, toMediaType, toVersion, true);
    }


    public EPCISFormat fromMediaType() {
        return fromMediaType;
    }

    public EPCISFormat toMediaType() {
        return toMediaType;
    }

    public EPCISVersion fromVersion() {
        return fromVersion;
    }

    public EPCISVersion toVersion() {
        return toVersion;
    }

    public boolean generateGS1CompliantDocument() {
        return generateGS1CompliantDocument;
    }

    public static Builder builder() {
        // setup with defaults
        return new Builder().generateGS1CompliantDocument(true);
    }

}
