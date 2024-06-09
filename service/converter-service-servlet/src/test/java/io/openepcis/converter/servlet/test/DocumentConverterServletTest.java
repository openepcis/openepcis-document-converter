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
package io.openepcis.converter.servlet.test;

import io.openepcis.converter.service.restassured.AbstractDocumentConverterTest;
import io.openepcis.converter.servlet.DocumentConverterServlet;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

import java.net.URL;

@QuarkusTest
public class DocumentConverterServletTest extends AbstractDocumentConverterTest {


    @TestHTTPEndpoint(DocumentConverterServlet.class)
    @TestHTTPResource
    URL url;

    @Override
    public String url() {
        // need to remove trailing slash being introduced by wildcard url pattern
        return url.toString().substring(0, url.toString().length()-1);
    }

}
