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
package io.openepcis.epc.converter;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.vertx.web.Route;
import io.vertx.ext.web.RoutingContext;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import jakarta.xml.bind.JAXBException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;

import javax.xml.namespace.QName;

@ApplicationPath("/")
@RegisterForReflection(targets = {QName.class, JAXBException.class})
@Slf4j
public class RESTApplication extends Application {

  @Route(methods = Route.HttpMethod.GET, path = "/")
  @Operation(hidden = true)
  void index(RoutingContext rc) {
    rc.redirect("/q/swagger-ui/index.html");
  }


}
