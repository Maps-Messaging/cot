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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CotStreamCodecTest {

  @Test
  void decodesAtEveryTwoPacketBoundary() throws Exception {
    byte[] event = event("split", "<detail><remarks>café</remarks></detail>");
    for (int split = 0; split <= event.length; split++) {
      CotStreamDecoder decoder = new CotStreamDecoder(1024);
      List<byte[]> frames = new ArrayList<>();
      frames.addAll(decoder.accept(slice(event, 0, split)));
      frames.addAll(decoder.accept(slice(event, split, event.length)));
      assertEquals(1, frames.size(), "split at byte " + split);
      assertArrayEquals(event, frames.getFirst(), "split at byte " + split);
    }
  }

  @Test
  void decodesOneByteAtATime() throws Exception {
    byte[] event = event("fragmented", "<point lat=\"1\"/>");
    CotStreamDecoder decoder = new CotStreamDecoder(1024);
    List<byte[]> frames = new ArrayList<>();
    for (byte value : event) {
      frames.addAll(decoder.accept(new byte[]{value}));
    }
    assertEquals(1, frames.size());
    assertArrayEquals(event, frames.getFirst());
  }

  @Test
  void decodesMultipleAdjacentEventsWithXmlDeclarations() throws Exception {
    byte[] first = event("one", "<detail/>");
    byte[] second = event("two", "<point/>");
    byte[] stream = bytes("<?xml version='1.0' standalone='yes'?>\n"
        + text(first) + "<?xml version='1.0'?>\r\n" + text(second));
    List<byte[]> frames = new CotStreamDecoder(1024).accept(stream);
    assertEquals(2, frames.size());
    assertArrayEquals(first, frames.get(0));
    assertArrayEquals(second, frames.get(1));
  }

  @Test
  void ignoresGarbageAndFalseEventPrefixes() throws Exception {
    byte[] expected = event("valid", "");
    byte[] input = bytes("noise<eve<eventual value='wrong'>junk</eventual>more" + text(expected));
    List<byte[]> frames = new CotStreamDecoder(1024).accept(input);
    assertEquals(1, frames.size());
    assertArrayEquals(expected, frames.getFirst());
  }

  @Test
  void ignoresEndMarkersInsideCommentsCdataAndProcessingInstructions() throws Exception {
    byte[] event = event("lexical", "<detail><!-- </event> --><remarks><![CDATA[</event>]]></remarks><?test value='</event>'?></detail>");
    List<byte[]> frames = new CotStreamDecoder(2048).accept(event);
    assertEquals(1, frames.size());
    assertArrayEquals(event, frames.getFirst());
  }

  @Test
  void ignoresTagTerminatorsInsideQuotedAttributes() throws Exception {
    byte[] event = event("quoted", "<detail><extension value=\"> </event> />\"/></detail>");
    List<byte[]> frames = new CotStreamDecoder(1024).accept(event);
    assertEquals(1, frames.size());
    assertArrayEquals(event, frames.getFirst());
  }

  @Test
  void supportsNestedEventElementsInDetail() throws Exception {
    byte[] event = event("outer", "<detail><event uid=\"inner\"><point/></event></detail>");
    List<byte[]> frames = new CotStreamDecoder(1024).accept(event);
    assertEquals(1, frames.size());
    assertArrayEquals(event, frames.getFirst());
  }

  @Test
  void isolatesMalformedCompleteEventFromFollowingEvent() throws Exception {
    byte[] malformed = bytes("<event uid=\"bad\"><broken></event>");
    byte[] valid = event("good", "");
    List<byte[]> frames = new CotStreamDecoder(1024).accept(bytes(text(malformed) + text(valid)));
    assertEquals(2, frames.size());
    assertArrayEquals(malformed, frames.get(0));
    assertArrayEquals(valid, frames.get(1));
  }

  @Test
  void resynchronisesWhenAnIncompleteEventIsFollowedByANewRoot() throws Exception {
    byte[] valid = event("recovered", "<point/>");
    byte[] input = bytes("<event uid=\"broken\"><point/>" + text(valid));
    List<byte[]> frames = new CotStreamDecoder(1024).accept(input);
    assertEquals(1, frames.size());
    assertArrayEquals(valid, frames.getFirst());
  }

  @Test
  void acceptsAnEventExactlyAtTheConfiguredLimit() throws Exception {
    byte[] event = bytes("<event>" + " ".repeat(241) + "</event>");
    assertEquals(256, event.length);
    assertEquals(1, new CotStreamDecoder(256).accept(event).size());
  }

  @Test
  void rejectsOversizedEventAndRecoversOnNextPacket() throws Exception {
    CotStreamDecoder decoder = new CotStreamDecoder(256);
    assertThrows(IOException.class, () -> decoder.accept(bytes("<event>" + "x".repeat(250))));
    assertEquals(1, decoder.accept(event("ok", "")).size());
  }

  @Test
  void ignoresNullEmptyAndLargePreambleWithoutRetainingIt() throws Exception {
    CotStreamDecoder decoder = new CotStreamDecoder(256);
    assertTrue(decoder.accept(null).isEmpty());
    assertTrue(decoder.accept(new byte[0]).isEmpty());
    assertTrue(decoder.accept(bytes("x".repeat(4096))).isEmpty());
    assertEquals(1, decoder.accept(event("after-preamble", "")).size());
  }

  @Test
  void rejectsInvalidMaximumSize() {
    assertThrows(IllegalArgumentException.class, () -> new CotStreamDecoder(255));
  }

  @Test
  void returnedFrameListIsImmutable() throws Exception {
    List<byte[]> frames = new CotStreamDecoder(1024).accept(event("immutable", ""));
    assertThrows(UnsupportedOperationException.class, () -> frames.add(new byte[0]));
  }

  @Test
  void encoderAddsHeaderWithoutTrailingDelimiter() {
    byte[] event = bytes("<event></event>");
    byte[] encoded = new CotStreamEncoder().encode(event);
    String text = text(encoded);
    assertEquals("<?xml version='1.0' standalone='yes'?>\n<event></event>", text);
    assertFalse(text.endsWith("\n"));
  }

  @Test
  void encoderPreservesTheEventBytes() {
    byte[] event = event("encoded", "<detail><remarks>café</remarks></detail>");
    byte[] encoded = new CotStreamEncoder().encode(event);
    assertTrue(text(encoded).endsWith(text(event)));
  }

  @Test
  void encoderRejectsNullAndEmptyEvents() {
    CotStreamEncoder encoder = new CotStreamEncoder();
    assertThrows(IllegalArgumentException.class, () -> encoder.encode(null));
    assertThrows(IllegalArgumentException.class, () -> encoder.encode(new byte[0]));
  }

  private static byte[] event(String uid, String content) {
    return bytes("<event uid=\"" + uid + "\">" + content + "</event>");
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static String text(byte[] value) {
    return new String(value, StandardCharsets.UTF_8);
  }

  private static byte[] slice(byte[] value, int start, int end) {
    byte[] result = new byte[end - start];
    System.arraycopy(value, start, result, 0, result.length);
    return result;
  }
}
