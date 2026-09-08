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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Incrementally extracts legacy XML CoT events from a byte stream. */
public final class CotStreamDecoder {

  private static final byte[] START = "<event".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] END = "</event>".getBytes(StandardCharsets.US_ASCII);

  private final int maximumEventSize;
  private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

  public CotStreamDecoder(int maximumEventSize) {
    if (maximumEventSize < 256) {
      throw new IllegalArgumentException("maximumEventSize must be at least 256 bytes");
    }
    this.maximumEventSize = maximumEventSize;
  }

  public synchronized List<byte[]> accept(byte[] input) throws IOException {
    if (input == null || input.length == 0) {
      return List.of();
    }
    buffer.write(input);
    byte[] data = buffer.toByteArray();
    List<byte[]> frames = new ArrayList<>();
    int consumed = 0;
    while (true) {
      int start = indexOf(data, START, consumed);
      if (start < 0) {
        retainPossibleStart(data);
        return frames;
      }
      int end = indexOf(data, END, start + START.length);
      if (end < 0) {
        if (data.length - start > maximumEventSize) {
          buffer.reset();
          throw new IOException("CoT event exceeds " + maximumEventSize + " bytes");
        }
        replaceBuffer(data, start, data.length);
        return frames;
      }
      int frameEnd = end + END.length;
      if (frameEnd - start > maximumEventSize) {
        buffer.reset();
        throw new IOException("CoT event exceeds " + maximumEventSize + " bytes");
      }
      frames.add(Arrays.copyOfRange(data, start, frameEnd));
      consumed = frameEnd;
      if (consumed == data.length) {
        buffer.reset();
        return frames;
      }
    }
  }

  private void retainPossibleStart(byte[] data) {
    int keep = 0;
    for (int length = Math.min(START.length - 1, data.length); length > 0; length--) {
      if (matches(data, data.length - length, START, length)) {
        keep = length;
        break;
      }
    }
    replaceBuffer(data, data.length - keep, data.length);
  }

  private void replaceBuffer(byte[] data, int start, int end) {
    buffer.reset();
    buffer.write(data, start, end - start);
  }

  private static int indexOf(byte[] data, byte[] target, int from) {
    for (int index = from; index <= data.length - target.length; index++) {
      if (matches(data, index, target, target.length)) {
        return index;
      }
    }
    return -1;
  }

  private static boolean matches(byte[] data, int offset, byte[] target, int length) {
    for (int index = 0; index < length; index++) {
      if (data[offset + index] != target[index]) {
        return false;
      }
    }
    return true;
  }
}
