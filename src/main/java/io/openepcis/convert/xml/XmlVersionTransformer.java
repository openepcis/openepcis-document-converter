package io.openepcis.convert.xml;

import io.openepcis.convert.EpcisVersion;

import java.io.IOException;
import java.io.InputStream;

public interface XmlVersionTransformer {
    InputStream xmlConverter(
            InputStream inputStream, EpcisVersion fromVersion, EpcisVersion toVersion)
            throws UnsupportedOperationException, IOException;
}
