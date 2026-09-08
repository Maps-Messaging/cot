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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

class CotParserTest {

  @Test
  void parsesEnvelopePointAndPreservesDetail() throws Exception {
    CotEvent event = new CotParser().parse(CotTestEvents.validEvent());
    assertEquals("2.0", event.version());
    assertEquals("uav-1", event.uid());
    assertEquals("a-f-A-M-F-U", event.type());
    assertEquals("m-g", event.how());
    assertEquals(OffsetDateTime.parse("2026-09-08T10:00:00Z"), event.time());
    assertEquals(OffsetDateTime.parse("2026-09-08T10:00:00Z"), event.start());
    assertEquals(OffsetDateTime.parse("2026-09-08T10:01:00Z"), event.stale());
    assertEquals(new BigDecimal("47.1"), event.point().latitude());
    assertEquals(new BigDecimal("8.2"), event.point().longitude());
    assertEquals(new BigDecimal("500"), event.point().heightAboveEllipsoid());
    assertEquals(new BigDecimal("4"), event.point().circularError());
    assertEquals(new BigDecimal("6"), event.point().linearError());
    assertEquals("unrestricted", event.optionalAttributes().get("access"));
    assertNotNull(event.detail().getElementsByTagName("vendor-extension").item(0));
  }

  @Test
  void preservesNamespacedDetailExtensions() throws Exception {
    byte[] xml = validEventWithDetail("<v:extension xmlns:v=\"urn:maps:test\" value=\"retained\"/>");
    CotEvent event = new CotParser().parse(xml);
    Element extension = (Element) event.detail().getFirstChild();
    assertEquals("urn:maps:test", extension.getNamespaceURI());
    assertEquals("extension", extension.getLocalName());
    assertEquals("v", extension.getPrefix());
  }

  @Test
  void supportsEventsWithoutOptionalDetail() throws Exception {
    byte[] xml = bytes("""
        <event version="2.0" uid="uav-1" type="a-f-A" how="m-g"
            time="2026-09-08T10:00:00Z" start="2026-09-08T10:00:00Z"
            stale="2026-09-08T10:01:00Z">
          <point lat="47.1" lon="8.2" hae="1" ce="1" le="1"/>
        </event>""");
    CotEvent event = new CotParser().parse(xml);
    assertNull(event.detail());
    assertTrue(event.optionalAttributes().isEmpty());
  }

  @Test
  void copiesAllSupportedOptionalAttributesAndMakesThemImmutable() throws Exception {
    byte[] xml = bytes("""
        <event version="2.0" uid="uav-1" type="a-f-A" how="m-g"
            time="2026-09-08T10:00:00Z" start="2026-09-08T10:00:00Z"
            stale="2026-09-08T10:01:00Z" access="restricted" qos="1-r-c" opex="e-test">
          <point lat="47.1" lon="8.2" hae="1" ce="1" le="1"/>
        </event>""");
    Map<String, String> attributes = new CotParser().parse(xml).optionalAttributes();
    assertEquals(Map.of("access", "restricted", "qos", "1-r-c", "opex", "e-test"), attributes);
    assertThrows(UnsupportedOperationException.class, () -> attributes.put("access", "changed"));
  }

  @Test
  void rejectsNullAndEmptyInput() {
    CotParser parser = new CotParser();
    assertThrows(IOException.class, () -> parser.parse(null));
    assertThrows(IOException.class, () -> parser.parse(new byte[0]));
  }

  @Test
  void rejectsWrongRootWhenValidationIsDisabled() {
    assertThrows(IOException.class, () -> new CotParser(false).parse(bytes("<message/>")));
  }

  @Test
  void rejectsMissingRequiredAttributesWhenValidationIsDisabled() {
    byte[] xml = bytes("<event><point lat=\"1\" lon=\"2\" hae=\"3\" ce=\"4\" le=\"5\"/></event>");
    assertThrows(IOException.class, () -> new CotParser(false).parse(xml));
  }

  @Test
  void rejectsInvalidDateWhenValidationIsDisabled() {
    byte[] xml = bytes("""
        <event version="2.0" uid="uav-1" type="a-f-A" how="m-g"
            time="broken" start="2026-09-08T10:00:00Z" stale="2026-09-08T10:01:00Z">
          <point lat="47.1" lon="8.2" hae="1" ce="1" le="1"/>
        </event>""");
    assertThrows(IOException.class, () -> new CotParser(false).parse(xml));
  }

  @Test
  void rejectsInvalidDecimalWhenValidationIsDisabled() {
    byte[] xml = bytes("""
        <event version="2.0" uid="uav-1" type="a-f-A" how="m-g"
            time="2026-09-08T10:00:00Z" start="2026-09-08T10:00:00Z"
            stale="2026-09-08T10:01:00Z">
          <point lat="broken" lon="8.2" hae="1" ce="1" le="1"/>
        </event>""");
    assertThrows(IOException.class, () -> new CotParser(false).parse(xml));
  }

  @Test
  void rejectsDoctypeWhenSchemaValidationIsDisabled() {
    byte[] xml = """
        <!DOCTYPE event [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
        <event version="2.0" uid="uav-1" type="a-f-A" how="m-g"
            time="2026-09-08T10:00:00Z" start="2026-09-08T10:00:00Z"
            stale="2026-09-08T10:01:00Z">
          <point lat="47.1" lon="8.2" hae="1" ce="1" le="1"/>
          <detail><remarks>&xxe;</remarks></detail>
        </event>""".getBytes(StandardCharsets.UTF_8);
    assertThrows(IOException.class, () -> new CotParser(false).parse(xml));
  }

  @Test
  void doesNotExpandXInclude() throws Exception {
    byte[] xml = validEventWithDetail("<xi:include xmlns:xi=\"http://www.w3.org/2001/XInclude\" href=\"file:///etc/passwd\" parse=\"text\"/>");
    CotEvent event = new CotParser(false).parse(xml);
    Element include = (Element) event.detail().getFirstChild();
    assertEquals("include", include.getLocalName());
    assertNull(include.getFirstChild());
  }

  private static byte[] validEventWithDetail(String detail) {
    return bytes("""
        <event version="2.0" uid="uav-1" type="a-f-A" how="m-g"
            time="2026-09-08T10:00:00Z" start="2026-09-08T10:00:00Z"
            stale="2026-09-08T10:01:00Z">
          <point lat="47.1" lon="8.2" hae="1" ce="1" le="1"/>
          <detail>%s</detail>
        </event>""".formatted(detail));
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
