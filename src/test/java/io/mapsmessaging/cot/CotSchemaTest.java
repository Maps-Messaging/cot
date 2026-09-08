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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CotSchemaTest {

  @Test
  void loadsBundledSchemaAndValidatesExtensions() throws Exception {
    assertNotNull(CotSchema.getInstance());
    new CotValidator().validate(CotTestEvents.validEvent());
  }

  @Test
  void rejectsEventMissingMandatoryPointAttributes() {
    byte[] invalid = """
        <event version="2.0" uid="uav-1" type="a-f-A" how="m-g"
            time="2026-09-08T10:00:00Z" start="2026-09-08T10:00:00Z"
            stale="2026-09-08T10:01:00Z">
          <point lat="47.1" lon="8.2"/>
        </event>""".getBytes(StandardCharsets.UTF_8);
    assertThrows(IOException.class, () -> new CotValidator().validate(invalid));
  }

  @Test
  void rejectsNullAndEmptyDocuments() {
    CotValidator validator = new CotValidator();
    assertThrows(IOException.class, () -> validator.validate(null));
    assertThrows(IOException.class, () -> validator.validate(new byte[0]));
  }

  @ParameterizedTest(name = "rejects {0}")
  @MethodSource("invalidEvents")
  void rejectsSchemaViolations(String description, String xml) {
    assertThrows(IOException.class, () -> new CotValidator().validate(bytes(xml)));
  }

  @ParameterizedTest(name = "accepts boundary point {0}, {1}")
  @MethodSource("coordinateBoundaries")
  void acceptsCoordinateBoundaries(String latitude, String longitude) throws Exception {
    new CotValidator().validate(bytes(event("2.0", "m-g", latitude, longitude)));
  }

  private static Stream<Arguments> invalidEvents() {
    return Stream.of(
        Arguments.of("latitude below minimum", event("2.0", "m-g", "-90.0001", "0")),
        Arguments.of("latitude above maximum", event("2.0", "m-g", "90.0001", "0")),
        Arguments.of("longitude below minimum", event("2.0", "m-g", "0", "-180.0001")),
        Arguments.of("longitude above maximum", event("2.0", "m-g", "0", "180.0001")),
        Arguments.of("version below 2", event("1.0", "m-g", "0", "0")),
        Arguments.of("invalid how", event("2.0", "-", "0", "0")),
        Arguments.of("invalid timestamp", event("2.0", "m-g", "0", "0").replace("2026-09-08T10:00:00Z", "invalid")),
        Arguments.of("unexpected root attribute", event("2.0", "m-g", "0", "0").replace("uid=\"uav-1\"", "uid=\"uav-1\" unexpected=\"true\"")));
  }

  private static Stream<Arguments> coordinateBoundaries() {
    return Stream.of(
        Arguments.of("-90", "-180"),
        Arguments.of("90", "180"),
        Arguments.of("0", "0"));
  }

  private static String event(String version, String how, String latitude, String longitude) {
    return """
        <event version="%s" uid="uav-1" type="a-f-A" how="%s"
            time="2026-09-08T10:00:00Z" start="2026-09-08T10:00:00Z"
            stale="2026-09-08T10:01:00Z">
          <point lat="%s" lon="%s" hae="1" ce="1" le="1"/>
        </event>""".formatted(version, how, latitude, longitude);
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
