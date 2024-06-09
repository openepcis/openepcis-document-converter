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
