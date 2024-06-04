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
package io.openepcis.convert.exception;

import java.io.Serial;

/**
 * Class that is used to throw any Exception that may occur during the execution of the XML-JSON or
 * JSON-XML conversion of the EPCIS events. During the execution if any error occurs then respective
 * information will be passed onto the methods of this class and this information will be show to
 * user and execution will be stopped.
 */
public class FormatConverterException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public FormatConverterException() {
    super();
  }

  public FormatConverterException(String message, Throwable cause) {
    super(message, cause);
  }

  public FormatConverterException(String message) {
    super(message);
  }

  public FormatConverterException(Throwable cause) {
    super(cause);
  }

}
