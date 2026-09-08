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

import java.nio.charset.StandardCharsets;

final class CotTestEvents {

  static final String VALID_EVENT = """
      <event version="2.0" uid="uav-1" type="a-f-A-M-F-U" how="m-g"
          time="2026-09-08T10:00:00Z" start="2026-09-08T10:00:00Z"
          stale="2026-09-08T10:01:00Z" access="unrestricted">
        <point lat="47.1" lon="8.2" hae="500" ce="4" le="6"/>
        <detail><contact callsign="Falcon"/><vendor-extension value="retained"/></detail>
      </event>""";

  private CotTestEvents() {
  }

  static byte[] validEvent() {
    return VALID_EVENT.getBytes(StandardCharsets.UTF_8);
  }
}
