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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CotParserTest {

  @Test
  void parsesEnvelopePointAndPreservesDetail() throws Exception {
    CotEvent event = new CotParser().parse(CotTestEvents.validEvent());
    assertEquals("uav-1", event.uid());
    assertEquals(new BigDecimal("47.1"), event.point().latitude());
    assertEquals("unrestricted", event.optionalAttributes().get("access"));
    assertNotNull(event.detail().getElementsByTagName("vendor-extension").item(0));
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
}
