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
package io.openepcis.quarkus.converter.runtime;

import io.openepcis.converter.VersionTransformer;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class OpenEPCISConverterHealthCheck implements HealthCheck {

    private final VersionTransformer versionTransformer;

    public OpenEPCISConverterHealthCheck(final VersionTransformer versionTransformer) {
        this.versionTransformer = versionTransformer;
    }

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.named("OpenEPCIS Document Converter health check").up();
        builder.up().withData("xmlVersionTransformer", versionTransformer.getXmlVersionTransformer().getClass().getName() );
        builder.up().withData("xmlToJsonConverter", versionTransformer.getXmlToJsonConverter().getClass().getName() );
        return builder.build();
    }
}
