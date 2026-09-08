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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/** Secure parser for the CoT 2.0 base envelope. */
public final class CotParser {

  private final boolean validate;
  private final CotValidator validator = new CotValidator();

  public CotParser() {
    this(true);
  }

  public CotParser(boolean validate) {
    this.validate = validate;
  }

  public CotEvent parse(byte[] xml) throws IOException {
    if (xml == null || xml.length == 0) {
      throw new IOException("CoT XML cannot be empty");
    }
    try {
      Document document = newDocumentBuilderFactory().newDocumentBuilder()
          .parse(new ByteArrayInputStream(xml));
      if (validate) {
        validator.validate(document);
      }
      Element event = document.getDocumentElement();
      if (event == null || !"event".equals(event.getTagName())) {
        throw new IOException("CoT document root must be event");
      }
      Element point = directChild(event, "point");
      if (point == null) {
        throw new IOException("CoT event is missing point");
      }
      Map<String, String> optional = new LinkedHashMap<>();
      copyAttribute(event, optional, "access");
      copyAttribute(event, optional, "qos");
      copyAttribute(event, optional, "opex");
      return new CotEvent(
          requiredAttribute(event, "version"),
          requiredAttribute(event, "uid"),
          requiredAttribute(event, "type"),
          requiredAttribute(event, "how"),
          dateTime(event, "time"),
          dateTime(event, "start"),
          dateTime(event, "stale"),
          parsePoint(point),
          directChild(event, "detail"),
          optional);
    } catch (ParserConfigurationException | SAXException e) {
      throw new IOException("Unable to parse CoT XML", e);
    }
  }

  private CotPoint parsePoint(Element point) throws IOException {
    return new CotPoint(
        decimal(point, "lat"),
        decimal(point, "lon"),
        decimal(point, "hae"),
        decimal(point, "ce"),
        decimal(point, "le"));
  }

  private DocumentBuilderFactory newDocumentBuilderFactory() throws ParserConfigurationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    return factory;
  }

  private OffsetDateTime dateTime(Element element, String name) throws IOException {
    String value = requiredAttribute(element, name);
    try {
      return OffsetDateTime.parse(value);
    } catch (DateTimeParseException e) {
      throw new IOException("Invalid CoT date-time attribute " + name, e);
    }
  }

  private BigDecimal decimal(Element element, String name) throws IOException {
    String value = requiredAttribute(element, name);
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException e) {
      throw new IOException("Invalid CoT decimal attribute " + name, e);
    }
  }

  private String requiredAttribute(Element element, String name) throws IOException {
    String value = element.getAttribute(name);
    if (value.isBlank()) {
      throw new IOException("Missing CoT attribute " + name);
    }
    return value;
  }

  private void copyAttribute(Element element, Map<String, String> target, String name) {
    if (element.hasAttribute(name)) {
      target.put(name, element.getAttribute(name));
    }
  }

  private Element directChild(Element parent, String name) {
    for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof Element element && name.equals(element.getTagName())) {
        return element;
      }
    }
    return null;
  }
}
