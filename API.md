# CoT Java API

This document describes the public API of `io.mapsmessaging:cot`. The library targets
Java 21 and handles the CoT 2.0 base XML envelope. It does not open sockets or manage
connections.

## Dependency

```xml
<dependency>
  <groupId>io.mapsmessaging</groupId>
  <artifactId>cot</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Snapshots are published in the MapsMessaging snapshot repository:

```xml
<repository>
  <id>maps_snapshots</id>
  <url>https://repository.mapsmessaging.io/repository/maps_snapshots/</url>
  <snapshots>
    <enabled>true</enabled>
  </snapshots>
</repository>
```

## Receiving a TCP or TLS stream

Create one decoder per ordered connection. Pass each buffer to the same decoder in wire
order, then parse each completed frame.

```java
CotStreamDecoder decoder = new CotStreamDecoder(1024 * 1024);
CotParser parser = new CotParser();

void onBytes(byte[] bytes) throws IOException {
  for (byte[] frame : decoder.accept(bytes)) {
    CotEvent event = parser.parse(frame);
    process(event);
  }
}
```

`accept` supports a partial event, one complete event, or several adjacent events in one
call. It retains an incomplete event for the next call and returns an immutable list of
complete XML frames. Bytes before `<event` are discarded, including an XML declaration.
The opening element must be the unprefixed, lower-case CoT `<event>` element.

The decoder understands element nesting, quoted attributes, comments, CDATA and processing
instructions, so text resembling `</event>` in those locations does not terminate a frame.
If a second root `<event>` follows an incomplete root, the decoder discards the incomplete
root and resynchronises on the new event. A malformed event that is nevertheless completely
framed is returned and is rejected later by `CotParser` or `CotValidator`.

`maximumEventSize` is a per-event byte limit and must be at least 256. Exceeding the limit
resets the decoder and throws `IOException`. The caller should record or count the failure
and continue using the same decoder with subsequent network data. Because failure aborts
the current `accept` call, use reasonably sized network buffers if retaining earlier frames
from the same call is operationally important.

`CotStreamDecoder.accept` is synchronized. A decoder still represents one ordered stream;
do not share one decoder between connections.

## Parsing an event

`CotParser` parses the envelope into typed Java values:

```java
CotParser parser = new CotParser();       // XSD validation enabled
CotEvent event = parser.parse(eventXml);

String uid = event.uid();
String cotType = event.type();
BigDecimal latitude = event.point().latitude();
Element detail = event.detail();
```

The default constructor validates against the bundled CoT 2.0 XSD before mapping fields.
`new CotParser(false)` disables XSD validation but retains well-formedness, required-field,
date-time and decimal checks. Disabling validation is intended only when validation has
already occurred at a trusted boundary.

`parse` throws `IOException` for null or empty input, malformed XML, schema violations,
missing required fields, invalid timestamps and invalid decimal values. The parser disables
DOCTYPE declarations, external entities, external DTD and schema access, XInclude, and
entity expansion.

Each parse creates its own XML parser and validator, so a `CotParser` may be reused across
threads.

## Parsed model

### `CotEvent`

| Accessor | Type | Meaning |
| --- | --- | --- |
| `version()` | `String` | CoT schema version |
| `uid()` | `String` | Producer-defined unique identifier |
| `type()` | `String` | CoT type hierarchy |
| `how()` | `String` | Source or derivation method |
| `time()` | `OffsetDateTime` | Event generation time |
| `start()` | `OffsetDateTime` | Event applicability start |
| `stale()` | `OffsetDateTime` | Event expiry time |
| `point()` | `CotPoint` | WGS-84 position and error values |
| `detail()` | `Element` | Application-specific `<detail>`, or `null` |
| `optionalAttributes()` | `Map<String,String>` | Present `access`, `qos`, and `opex` attributes |

The optional-attribute map is immutable. The detail value is the DOM element from the
parsed document and preserves application-specific extensions accepted by the schema.

### `CotPoint`

| Accessor | Type | CoT attribute |
| --- | --- | --- |
| `latitude()` | `BigDecimal` | `lat` |
| `longitude()` | `BigDecimal` | `lon` |
| `heightAboveEllipsoid()` | `BigDecimal` | `hae` |
| `circularError()` | `BigDecimal` | `ce` |
| `linearError()` | `BigDecimal` | `le` |

`BigDecimal` is used to preserve the supplied numeric precision.

## Validation without mapping

Use `CotValidator` when a component needs only an accept/reject decision:

```java
CotValidator validator = new CotValidator();
validator.validate(eventXml); // returns normally when valid
```

`validate` throws `IOException` for empty, malformed, unsafe or schema-invalid input. A
validator instance may be reused across threads.

## Schema access

`CotSchema` exposes the bundled MITRE public-release CoT 2.0 base schema:

```java
Schema schema = CotSchema.getInstance();

try (InputStream xsd = CotSchema.openStream()) {
  // inspect or copy the bundled XSD
}
```

`getInstance()` lazily loads and caches the thread-safe `Schema`. Failure to load the
packaged schema raises `ExceptionInInitializerError`, because the installed library is not
usable without that resource. `openStream()` returns a new caller-owned stream and throws
`IOException` if the resource is absent.

## Encoding for a TAK XML connection

`CotStreamEncoder` adds the legacy XML declaration expected on a TAK streaming connection:

```java
CotStreamEncoder encoder = new CotStreamEncoder();
byte[] wireBytes = encoder.encode(eventXml);
socket.write(wireBytes);
```

The input must be a non-empty, already serialized `<event>` document. The encoder does not
parse or validate it. It throws `IllegalArgumentException` for null or empty input. Encoder
instances are stateless and thread-safe.

## Diagnostics

The library emits structured MapsMessaging log events through `CotLogMessages` for schema
load, validation and parsing rejection, oversized stream events, and resynchronisation.
Payload XML is never logged. Callers remain responsible for connection identity, peer
address, retry, disconnect and aggregate health metrics because those concerns are outside
this library.

## Recommended integration policy

- Set `maximumEventSize` from endpoint configuration; 1 MiB is a reasonable initial limit.
- Use one decoder per TCP/TLS connection and preserve byte order.
- Keep schema validation enabled at the untrusted network boundary.
- Treat a single rejected event as recoverable; count repeated rejection and apply the
  server's connection abuse or backoff policy.
- Do not log raw `<detail>` content because it may contain operational data.
