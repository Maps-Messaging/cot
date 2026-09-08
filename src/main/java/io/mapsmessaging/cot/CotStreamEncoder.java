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

/** Encodes one event for a legacy TAK XML streaming connection. */
public final class CotStreamEncoder {

  private static final byte[] HEADER =
      "<?xml version='1.0' standalone='yes'?>\n".getBytes(StandardCharsets.US_ASCII);

  public byte[] encode(byte[] eventXml) {
    if (eventXml == null || eventXml.length == 0) {
      throw new IllegalArgumentException("eventXml cannot be empty");
    }
    byte[] encoded = new byte[HEADER.length + eventXml.length];
    System.arraycopy(HEADER, 0, encoded, 0, HEADER.length);
    System.arraycopy(eventXml, 0, encoded, HEADER.length, eventXml.length);
    return encoded;
  }
}
