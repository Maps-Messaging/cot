# Cursor on Target (CoT)

Java 21 library for Cursor on Target 2.0 XML.

The library provides:

- the MITRE public-release CoT 2.0 base event schema;
- secure XSD loading and event validation;
- secure parsing of the CoT event envelope and point;
- preservation of application-specific `<detail>` extensions;
- incremental framing of CoT XML from TCP and TLS streams; and
- encoding for legacy TAK XML streaming connections.

It deliberately contains no socket, broker, session, or MapsMessaging server code.

## Maven

```xml
<dependency>
  <groupId>io.mapsmessaging</groupId>
  <artifactId>cot</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Basic use

```java
CotParser parser = new CotParser();
CotEvent event = parser.parse(xmlBytes);

CotStreamDecoder decoder = new CotStreamDecoder(1024 * 1024);
List<byte[]> events = decoder.accept(networkBytes);
```

Validation uses the bundled public-release base schema. The schema intentionally accepts
application-specific elements inside `<detail>` using lax processing.

## Schema provenance

`src/main/resources/io/mapsmessaging/cot/schema/Event.xsd` is the MITRE CoT 2.0 base event
schema distributed by the US Department of Defense ATAK-CIV project. Its original copyright
and public-release notice are retained in the file.

## License

Apache License 2.0 with the Commons Clause. See `LICENSE`.

The bundled MITRE public-release schema retains its original notices; see
`THIRD_PARTY_NOTICES.md`.
