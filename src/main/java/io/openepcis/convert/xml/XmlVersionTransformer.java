package io.openepcis.convert.xml;

import io.openepcis.convert.EpcisVersion;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public interface XmlVersionTransformer {
    public InputStream xmlConverter(
            InputStream inputStream, EpcisVersion fromVersion, EpcisVersion toVersion)
            throws UnsupportedOperationException, IOException;

    static XmlVersionTransformer newInstance() {
        return newInstance(Executors.newWorkStealingPool());
    }
    static XmlVersionTransformer newInstance(Class<? extends XmlVersionTransformerFactory> factory) {
        return newInstance(Executors.newWorkStealingPool(), factory);
    }
    static XmlVersionTransformer newInstance(final ExecutorService executorService) {
        final Optional<XmlVersionTransformerFactory> optionalFactory =  ServiceLoader.load(XmlVersionTransformerFactory.class).findFirst();
        if (optionalFactory.isPresent()) {
            return optionalFactory.get().newXmlVersionTransformer(executorService);
        }
        return new DefaultXmlVersionTransformer(executorService);
    }

    static XmlVersionTransformer newInstance(final ExecutorService executorService, Class<? extends XmlVersionTransformerFactory> factory) throws IllegalArgumentException {
        final Optional<ServiceLoader.Provider<XmlVersionTransformerFactory>> optionalFactory =  ServiceLoader.load(XmlVersionTransformerFactory.class).stream().filter(f -> f.type().equals(factory)).findFirst();
        if (optionalFactory.isPresent()) {
            return optionalFactory.get().get().newXmlVersionTransformer(executorService);
        }
        throw new IllegalArgumentException("no XmlVersionTransformerFactory found for class "+factory.getName());
    }
}
