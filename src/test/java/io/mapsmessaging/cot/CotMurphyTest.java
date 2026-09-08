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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class CotMurphyTest {

  @Test
  void randomPacketBoundariesDoNotChangeTheEventStream() throws Exception {
    List<byte[]> expected = new ArrayList<>();
    ByteArrayOutputStream wire = new ByteArrayOutputStream();
    for (int index = 0; index < 200; index++) {
      byte[] event = event(index, "<detail><remarks>event " + index + " — café</remarks></detail>");
      expected.add(event);
      wire.writeBytes(bytes("<?xml version='1.0'?>\n"));
      wire.writeBytes(event);
    }

    byte[] stream = wire.toByteArray();
    Random random = new Random(4817);
    CotStreamDecoder decoder = new CotStreamDecoder(2048);
    List<byte[]> actual = new ArrayList<>();
    int offset = 0;
    while (offset < stream.length) {
      int length = Math.min(1 + random.nextInt(47), stream.length - offset);
      actual.addAll(decoder.accept(slice(stream, offset, offset + length)));
      offset += length;
    }

    assertEquals(expected.size(), actual.size());
    for (int index = 0; index < expected.size(); index++) {
      assertArrayEquals(expected.get(index), actual.get(index), "event " + index);
    }
  }

  @Test
  void corruptCompleteEventsAreIsolatedFromFollowingValidEvents() throws Exception {
    List<String> corruptEvents = List.of(
        "<event><unclosed></event>",
        "<event><point></detail></event>",
        "<event uid=\"bad\"><![CDATA[random </event> bytes]]><broken></event>",
        "<event uid=\"bad\"><detail><!-- broken -- --></detail></event>");

    for (int index = 0; index < corruptEvents.size(); index++) {
      byte[] valid = event(index, "");
      CotStreamDecoder decoder = new CotStreamDecoder(2048);
      List<byte[]> frames = decoder.accept(bytes(corruptEvents.get(index) + text(valid)));
      assertEquals(2, frames.size(), "corruption case " + index);
      assertThrows(IOException.class, () -> new CotParser().parse(frames.get(0)), "corruption case " + index);
      assertEquals("murphy-" + index, new CotParser().parse(frames.get(1)).uid());
    }
  }

  @Test
  void truncatedRootIsDiscardedWhenTheNextRootArrives() throws Exception {
    CotStreamDecoder decoder = new CotStreamDecoder(2048);
    decoder.accept(bytes("<event uid=\"lost\"><point/>"));
    byte[] valid = event(1, "");
    List<byte[]> frames = decoder.accept(valid);
    assertEquals(1, frames.size());
    assertArrayEquals(valid, frames.getFirst());
  }

  @Test
  void repeatedGarbageAndPartialStartMarkersDoNotAccumulate() throws Exception {
    CotStreamDecoder decoder = new CotStreamDecoder(256);
    for (int index = 0; index < 10_000; index++) {
      decoder.accept(bytes(index % 2 == 0 ? "garbage<eve" : "ntually>"));
    }
    assertEquals(1, decoder.accept(event(2, "")).size());
  }

  @Test
  void repeatedMalformedDocumentsDoNotPoisonTheParser() throws Exception {
    CotParser parser = new CotParser();
    for (int index = 0; index < 100; index++) {
      assertThrows(IOException.class, () -> parser.parse(bytes("<event><point></event>")));
    }
    assertEquals("uav-1", parser.parse(CotTestEvents.validEvent()).uid());
  }

  @Test
  void sharedSchemaSupportsConcurrentValidation() throws Exception {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Callable<String>> validations = new ArrayList<>();
      for (int index = 0; index < 100; index++) {
        validations.add(() -> new CotParser().parse(CotTestEvents.validEvent()).uid());
      }
      for (var result : executor.invokeAll(validations)) {
        assertEquals("uav-1", result.get());
      }
    }
  }

  private static byte[] event(int index, String detail) {
    return bytes("""
        <event version="2.0" uid="murphy-%d" type="a-f-A" how="m-g"
            time="2026-09-08T10:00:00Z" start="2026-09-08T10:00:00Z"
            stale="2026-09-08T10:01:00Z">
          <point lat="47.1" lon="8.2" hae="1" ce="1" le="1"/>%s
        </event>""".formatted(index, detail));
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
