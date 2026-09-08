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

import io.mapsmessaging.logging.Category;
import io.mapsmessaging.logging.LEVEL;
import io.mapsmessaging.logging.LogMessage;

/** Structured log events emitted by the CoT library. */
public enum CotLogMessages implements LogMessage {

  COT_SCHEMA_LOADING(LEVEL.DEBUG, CotCategory.SCHEMA, "Loading CoT schema resource '{}'"),
  COT_SCHEMA_LOADED(LEVEL.INFO, CotCategory.SCHEMA, "Loaded CoT schema resource '{}'"),
  COT_SCHEMA_LOAD_FAILED(LEVEL.ERROR, CotCategory.SCHEMA, "Failed to load CoT schema resource '{}': {}"),
  COT_VALIDATION_FAILED(LEVEL.WARN, CotCategory.PARSER, "Rejected CoT event during schema validation, bytes {}: {}"),
  COT_PARSE_FAILED(LEVEL.WARN, CotCategory.PARSER, "Rejected malformed CoT event, bytes {}: {}"),
  COT_STREAM_EVENT_OVERSIZED(LEVEL.WARN, CotCategory.STREAM, "Rejected CoT stream event at {} bytes; configured maximum is {} bytes"),
  COT_STREAM_RESYNCHRONISED(LEVEL.WARN, CotCategory.STREAM, "Resynchronised CoT stream after discarding {} bytes from an incomplete event");

  private final String message;
  private final LEVEL level;
  private final Category category;
  private final int parameterCount;

  CotLogMessages(LEVEL level, Category category, String message) {
    this.message = message;
    this.level = level;
    this.category = category;
    this.parameterCount = countParameters(message);
  }

  @Override
  public String getMessage() {
    return message;
  }

  @Override
  public LEVEL getLevel() {
    return level;
  }

  @Override
  public Category getCategory() {
    return category;
  }

  @Override
  public int getParameterCount() {
    return parameterCount;
  }

  private static int countParameters(String message) {
    int count = 0;
    int location = message.indexOf("{}");
    while (location != -1) {
      count++;
      location = message.indexOf("{}", location + 2);
    }
    return count;
  }

  public enum CotCategory implements Category {
    SCHEMA("Schema"),
    PARSER("Parser"),
    STREAM("Stream");

    private final String description;

    CotCategory(String description) {
      this.description = description;
    }

    @Override
    public String getDivision() {
      return "CoT";
    }

    @Override
    public String getDescription() {
      return description;
    }
  }
}
