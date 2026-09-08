/*
 *
 * Copyright [ 2024 - 2026 ] MapsMessaging B.V.
 *
 * Licensed under the Apache License, Version 2.0 with the Commons Clause
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     https://commonsclause.com/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.mapsmessaging.cot;

import static io.mapsmessaging.cot.CotLogMessages.COT_VALIDATION_FAILED;

import io.mapsmessaging.logging.Logger;
import io.mapsmessaging.logging.LoggerFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/** Validates XML against the bundled CoT 2.0 base schema. */
public final class CotValidator {

  private static final Logger LOGGER = LoggerFactory.getLogger(CotValidator.class);

  public void validate(byte[] xml) throws IOException {
    if (xml == null || xml.length == 0) {
      throw new IOException("CoT XML cannot be empty");
    }
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      factory.setNamespaceAware(true);
      DocumentBuilder builder = factory.newDocumentBuilder();
      builder.setErrorHandler(CotSaxErrorHandler.INSTANCE);
      validate(builder.parse(new ByteArrayInputStream(xml)), xml.length);
    } catch (ParserConfigurationException | SAXException e) {
      LOGGER.log(COT_VALIDATION_FAILED, e, xml.length, e.getMessage());
      throw new IOException("Invalid CoT 2.0 event", e);
    }
  }

  void validate(Node document, int byteLength) throws IOException {
    try {
      var validator = CotSchema.getInstance().newValidator();
      validator.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      validator.validate(new DOMSource(document));
    } catch (SAXException e) {
      LOGGER.log(COT_VALIDATION_FAILED, e, byteLength, e.getMessage());
      throw new IOException("Invalid CoT 2.0 event", e);
    }
  }
}
