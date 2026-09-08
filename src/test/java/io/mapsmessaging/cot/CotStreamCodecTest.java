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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CotStreamCodecTest {

  @Test
  void decodesFragmentedAdjacentEvents() throws Exception {
    String first = "<event uid=\"one\"><detail><remarks>café</remarks></detail></event>";
    String second = "<event uid=\"two\"><point lat=\"1\"/></event>";
    byte[] stream = ("<?xml version='1.0' standalone='yes'?>\n" + first
        + "<?xml version='1.0' standalone='yes'?>\n" + second).getBytes(StandardCharsets.UTF_8);
    CotStreamDecoder decoder = new CotStreamDecoder(1024);
    List<byte[]> frames = new ArrayList<>();
    for (byte value : stream) {
      frames.addAll(decoder.accept(new byte[]{value}));
    }
    assertEquals(List.of(first, second), frames.stream()
        .map(value -> new String(value, StandardCharsets.UTF_8)).toList());
  }

  @Test
  void rejectsOversizedEventAndRecovers() throws Exception {
    CotStreamDecoder decoder = new CotStreamDecoder(256);
    assertThrows(IOException.class,
        () -> decoder.accept(("<event>" + "x".repeat(260)).getBytes(StandardCharsets.UTF_8)));
    assertEquals(1, decoder.accept("<event uid=\"ok\"></event>".getBytes(StandardCharsets.UTF_8)).size());
  }

  @Test
  void encoderAddsHeaderWithoutTrailingDelimiter() {
    byte[] event = "<event></event>".getBytes(StandardCharsets.UTF_8);
    byte[] encoded = new CotStreamEncoder().encode(event);
    String text = new String(encoded, StandardCharsets.UTF_8);
    assertEquals("<?xml version='1.0' standalone='yes'?>\n<event></event>", text);
    assertFalse(text.endsWith("\n"));
  }
}
