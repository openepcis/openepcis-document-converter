package io.openepcis.converter.common;

import io.openepcis.constants.EPCISFormat;
import io.openepcis.model.epcis.EPCISEvent;
import io.openepcis.model.epcis.constants.CommonConstants;
import io.openepcis.model.epcis.format.CBVFormat;
import io.openepcis.model.epcis.format.EPCFormat;
import io.openepcis.model.epcis.format.FormatPreference;
import io.openepcis.repository.model.EPCISEventES;
import io.openepcis.repository.util.EventConvertor;
import jakarta.ws.rs.core.MediaType;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public class GS1FormatSupport {

  public interface RequestHeaderFacade {
    String getHeader(String name);
  }

  public static final RequestHeaderFacade createRequestFacade(final Function<String, String> function) {
    return new RequestHeaderFacade() {
      @Override
      public String getHeader(String name) {
        return function.apply(name);
      }
    };
  }

  public static final FormatPreference getFormatPreference(RequestHeaderFacade requestHeaderFacade) {
    Optional<String> epcFormat =
        Optional.ofNullable(requestHeaderFacade.getHeader(CommonConstants.GS1_EPC_FORMAT));
    Optional<String> cbvFormat =
        Optional.ofNullable(requestHeaderFacade.getHeader(CommonConstants.GS1_CBV_XML_FORMAT));
    return FormatPreference.getInstance(epcFormat, cbvFormat);
  }

  public static final BiFunction<Object, List<Object>, Object> createMapper(final FormatPreference formatPreference) {
    if (formatPreference.getEpcFormat() != EPCFormat.No_Preference
        || formatPreference.getCbvFormat() != CBVFormat.No_Preference) {
      return (o, context) -> {
          if (o != null && EPCISEvent.class.isAssignableFrom(o.getClass())) {
              EPCISEventES esEvent =
                      EventConvertor.getESRepresentation((EPCISEvent) o, new HashMap<>(), context);
              return esEvent.getCoreModel(formatPreference, context);
          }
          return o;
      };
    }
    // default function - return same
    return (o, context) -> o;
  }

  public static boolean isValidMediaType(MediaType mediaType) {
    String type = mediaType.getType();
    String subtype = mediaType.getSubtype();

    return (MediaType.APPLICATION_XML_TYPE.getType().equals(type) && MediaType.APPLICATION_XML_TYPE.getSubtype().equals(subtype))
            || (MediaType.APPLICATION_JSON_TYPE.getType().equals(type) && MediaType.APPLICATION_JSON_TYPE.getSubtype().equals(subtype))
            || ("application".equals(type) && "ld+json".equals(subtype));
  }

  public static EPCISFormat getEPCISFormat(MediaType mediaType) {
    String type = mediaType.getType();
    String subtype = mediaType.getSubtype();

    if (MediaType.APPLICATION_XML_TYPE.getType().equals(type) && MediaType.APPLICATION_XML_TYPE.getSubtype().equals(subtype)) {
      return EPCISFormat.XML;
    } else {
      return EPCISFormat.JSON_LD;
    }
  }
}
