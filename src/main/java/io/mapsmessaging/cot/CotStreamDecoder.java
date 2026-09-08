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

import static io.mapsmessaging.cot.CotLogMessages.COT_STREAM_EVENT_OVERSIZED;
import static io.mapsmessaging.cot.CotLogMessages.COT_STREAM_RESYNCHRONISED;

import io.mapsmessaging.logging.Logger;
import io.mapsmessaging.logging.LoggerFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Incrementally extracts legacy XML CoT events from a byte stream. */
public final class CotStreamDecoder {

  private static final byte[] START = "<event".getBytes(StandardCharsets.US_ASCII);
  private static final Logger LOGGER = LoggerFactory.getLogger(CotStreamDecoder.class);

  private final int maximumEventSize;
  private final ByteArrayOutputStream eventBuffer = new ByteArrayOutputStream();
  private final StringBuilder tagName = new StringBuilder();
  private final StringBuilder declarationPrefix = new StringBuilder();
  private final Deque<String> elementStack = new ArrayDeque<>();

  private State state = State.SEARCHING;
  private int startMatch;
  private int terminatorMatch;
  private int tagStartOffset;
  private boolean closingTag;
  private boolean selfClosing;
  private byte quote;

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
    List<byte[]> frames = new ArrayList<>();
    for (byte value : input) {
      if (state == State.SEARCHING) {
        searchForEvent(value);
      } else if (state == State.START_BOUNDARY) {
        acceptStartBoundary(value, frames);
      } else {
        append(value);
        processEventByte(value, frames);
      }
    }
    return List.copyOf(frames);
  }

  private void searchForEvent(byte value) {
    if (value == START[startMatch]) {
      startMatch++;
      if (startMatch == START.length) {
        state = State.START_BOUNDARY;
      }
      return;
    }
    startMatch = value == START[0] ? 1 : 0;
  }

  private void acceptStartBoundary(byte value, List<byte[]> frames) throws IOException {
    if (!isNameBoundary(value)) {
      state = State.SEARCHING;
      startMatch = value == START[0] ? 1 : 0;
      return;
    }
    eventBuffer.reset();
    eventBuffer.writeBytes(START);
    append(value);
    elementStack.clear();
    tagName.setLength(0);
    tagName.append("event");
    tagStartOffset = 0;
    closingTag = false;
    selfClosing = false;
    quote = 0;
    state = State.TAG;
    processTagByte(value, frames);
  }

  private void processEventByte(byte value, List<byte[]> frames) {
    switch (state) {
      case TEXT -> {
        if (value == '<') {
          tagStartOffset = eventBuffer.size() - 1;
          state = State.AFTER_LESS_THAN;
        }
      }
      case AFTER_LESS_THAN -> processAfterLessThan(value);
      case TAG_NAME -> processTagNameByte(value, frames);
      case TAG -> processTagByte(value, frames);
      case DECLARATION_PREFIX -> processDeclarationPrefix(value);
      case COMMENT -> processTerminator(value, "-->", State.TEXT);
      case CDATA -> processTerminator(value, "]]>", State.TEXT);
      case PROCESSING_INSTRUCTION -> processTerminator(value, "?>", State.TEXT);
      case DECLARATION -> processDeclarationByte(value);
      default -> throw new IllegalStateException("Unexpected CoT decoder state " + state);
    }
  }

  private void processAfterLessThan(byte value) {
    tagName.setLength(0);
    closingTag = false;
    selfClosing = false;
    quote = 0;
    if (value == '/') {
      closingTag = true;
      state = State.TAG_NAME;
    } else if (value == '?') {
      terminatorMatch = 0;
      state = State.PROCESSING_INSTRUCTION;
    } else if (value == '!') {
      declarationPrefix.setLength(0);
      state = State.DECLARATION_PREFIX;
    } else {
      tagName.append((char) (value & 0xff));
      state = State.TAG_NAME;
    }
  }

  private void processTagNameByte(byte value, List<byte[]> frames) {
    if (!isNameBoundary(value)) {
      tagName.append((char) (value & 0xff));
      return;
    }
    state = State.TAG;
    processTagByte(value, frames);
  }

  private void processTagByte(byte value, List<byte[]> frames) {
    if (quote != 0) {
      if (value == quote) {
        quote = 0;
      }
      return;
    }
    if (value == '\'' || value == '"') {
      quote = value;
    } else if (value == '/') {
      selfClosing = true;
    } else if (value == '>') {
      completeTag(frames);
    } else if (!isWhitespace(value)) {
      selfClosing = false;
    }
  }

  private void completeTag(List<byte[]> frames) {
    String name = tagName.toString();
    if (!closingTag && "event".equals(name) && isUnclosedRootOnly()) {
      resynchroniseAtCurrentTag();
      return;
    }
    if (closingTag) {
      completeClosingTag(name, frames);
    } else if (selfClosing) {
      if (elementStack.isEmpty() && "event".equals(name)) {
        completeFrame(frames);
      } else {
        state = State.TEXT;
      }
    } else {
      elementStack.push(name);
      state = State.TEXT;
    }
  }

  private void completeClosingTag(String name, List<byte[]> frames) {
    if ("event".equals(name) && isRootClosingTag()) {
      completeFrame(frames);
      return;
    }
    if (!elementStack.isEmpty() && name.equals(elementStack.peek())) {
      elementStack.pop();
    }
    state = State.TEXT;
  }

  private boolean isUnclosedRootOnly() {
    return elementStack.size() == 1 && "event".equals(elementStack.peek());
  }

  private boolean isRootClosingTag() {
    if (!elementStack.contains("event")) {
      return true;
    }
    int eventCount = 0;
    for (String element : elementStack) {
      if ("event".equals(element)) {
        eventCount++;
      }
    }
    return eventCount == 1;
  }

  private void resynchroniseAtCurrentTag() {
    byte[] data = eventBuffer.toByteArray();
    LOGGER.log(COT_STREAM_RESYNCHRONISED, tagStartOffset);
    eventBuffer.reset();
    eventBuffer.write(data, tagStartOffset, data.length - tagStartOffset);
    elementStack.clear();
    elementStack.push("event");
    state = State.TEXT;
  }

  private void completeFrame(List<byte[]> frames) {
    frames.add(eventBuffer.toByteArray());
    reset();
  }

  private void processDeclarationPrefix(byte value) {
    declarationPrefix.append((char) (value & 0xff));
    String prefix = declarationPrefix.toString();
    if ("--".equals(prefix)) {
      terminatorMatch = 0;
      state = State.COMMENT;
    } else if ("[CDATA[".equals(prefix)) {
      terminatorMatch = 0;
      state = State.CDATA;
    } else if (!"--".startsWith(prefix) && !"[CDATA[".startsWith(prefix)) {
      quote = 0;
      state = State.DECLARATION;
      processDeclarationByte(value);
    }
  }

  private void processDeclarationByte(byte value) {
    if (quote != 0) {
      if (value == quote) {
        quote = 0;
      }
    } else if (value == '\'' || value == '"') {
      quote = value;
    } else if (value == '>') {
      state = State.TEXT;
    }
  }

  private void processTerminator(byte value, String terminator, State completedState) {
    if (value == terminator.charAt(terminatorMatch)) {
      terminatorMatch++;
      if (terminatorMatch == terminator.length()) {
        terminatorMatch = 0;
        state = completedState;
      }
    } else {
      terminatorMatch = value == terminator.charAt(0) ? 1 : 0;
    }
  }

  private void append(byte value) throws IOException {
    if (eventBuffer.size() >= maximumEventSize) {
      LOGGER.log(COT_STREAM_EVENT_OVERSIZED, eventBuffer.size() + 1, maximumEventSize);
      reset();
      throw new IOException("CoT event exceeds " + maximumEventSize + " bytes");
    }
    eventBuffer.write(value);
  }

  private void reset() {
    eventBuffer.reset();
    tagName.setLength(0);
    declarationPrefix.setLength(0);
    elementStack.clear();
    state = State.SEARCHING;
    startMatch = 0;
    terminatorMatch = 0;
    tagStartOffset = 0;
    closingTag = false;
    selfClosing = false;
    quote = 0;
  }

  private static boolean isNameBoundary(byte value) {
    return isWhitespace(value) || value == '>' || value == '/';
  }

  private static boolean isWhitespace(byte value) {
    return value == ' ' || value == '\t' || value == '\r' || value == '\n';
  }

  private enum State {
    SEARCHING,
    START_BOUNDARY,
    TEXT,
    AFTER_LESS_THAN,
    TAG_NAME,
    TAG,
    DECLARATION_PREFIX,
    COMMENT,
    CDATA,
    PROCESSING_INSTRUCTION,
    DECLARATION
  }
}
