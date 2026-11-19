# Semantische Modelle (SAMM) – `urn:samm:com.msg.ppap:0.1.0#`

Aspektmodelle des PPAP/PPF-Austauschformats v0.1 des msg CX PPAP Connectors (ADR-0003, ADR-0006). Sie sind die **Quelle** der JSON Schemas, gegen die das Modul `semantics` jede ein- und ausgehende Nutzlast prüft, und zugleich ein **Diskussionsbeitrag für die Catena-X Expert Group „Product Approval“** (Basis VDA Band 2 / VDA 231-300/-301). Alle Stellen, die von der Expert Group zu entscheiden sind, tragen `TODO(EG)`.

## Modelle

| Aspekt | Zweck | Signiert |
|---|---|---|
| `PpapRequest` | Anforderung des Kunden: Teil (Kunden-/Lieferantensachnummer), Grund, Stufe, Termin, geforderte Elemente inkl. Definitions-Snapshot, Anhänge (Metadaten) | – |
| `PpapRequestWithdrawn` | Rücknahme einer gesendeten Anforderung (Grund) | – |
| `PpapStatusUpdate` | Statuswechsel beim Absender: `IN_PROGRESS` (Lieferant) / `IN_REVIEW` (Kunde) | – |
| `PpapSubmission` | Einreichung einer Revision: Selbstbeurteilung, Werte, Kommentar und Dokument-Metadaten (SHA-256) je Element, Unterlieferanten-Referenzen | JWS |
| `PpapDecision` | Entscheidung mit Auflagen/Frist, Befund je Element, Bezug auf den Hash der Einreichung | JWS |
| `PpapNotification` | Umschlag aller Nachrichten über die EDC Data Plane; Header in der Struktur von `urn:samm:io.catenax.shared.message_header:3.0.0#MessageHeaderAspect` | – |

Signatur: JWS ES256, *detached* mit unkodierter Nutzlast (RFC 7797) über das RFC-8785-kanonische JSON (ADR-0008). Hash: `sha256:<hex>` über dieselben Bytes.

## Build

`esmf-aspect-model-maven-plugin` (ESMF SDK 2.16.2) im normalen Maven-Build:
- `validate` – SAMM-Validierung (Build bricht bei Modellfehlern ab),
- `generateJsonSchema` → `target/classes/samm/com.msg.ppap/0.1.0/<Aspekt>.schema.json` (Teil des Artefakts `ppap-semantics-models`, über die App-Jar ins Docker-Image – kein Netz zur Laufzeit/im Image-Build nötig; der Maven-Build braucht Maven Central wie alle anderen Plugins),
- `generateDocumentation` → `target/esmf/docs/<Aspekt>_{de,en}.html`,
- `generateJsonPayload` → `target/esmf/payloads/<Aspekt>.json` (Beispielnutzlasten aus `samm:exampleValue`).

Das Modul `semantics` lädt die generierten Schemas (JSON Schema draft 4) und validiert **geschlossen** (`additionalProperties: false` wird beim Laden ergänzt, weil SAMM das nicht ausdrückt). `TODO(EG)`: geschlossene Validierung im Standard festlegen.

## Durch SAMM erzwungene Abweichungen von der ersten Fassung (v0.1, handgeschriebene Schemas)

- **Strukturierte Werte** (`ElementEvidence.values`) und **Werte-Schemas** (`ElementSnapshot.valueSchema`) sind JSON-*Text* (RFC-8785-kanonisch serialisiert): SAMM kennt keinen Typ für beliebige JSON-Strukturen. Validiert wird der Inhalt gegen das Werte-Schema des Elements. `TODO(EG)`: für Prüf- und Messergebnisse die VDA-231-301-Strukturen als eigene Entities modellieren (dann typisiert statt Text).
- **Nutzlast im Umschlag** (`Content.payload`) ist der kanonische JSON-Text der Nutzlast (SAMM kennt kein „eines von mehreren Aspekten“). Vorteil: es sind exakt die gehashten und signierten Bytes.
- **Optionale Header-Felder** (`expectedResponseBy`, `relatedMessageId`) werden weggelassen statt `null`.

## Offene Punkte für die Expert Group (`TODO(EG)`)

- Header: den gemeinsamen Message-Header (`io.catenax.shared.message_header:3.0.0`) importieren statt der lokalen Strukturkopie; Namespace/Version des Standards (`io.catenax.product_approval`?).
- UsagePurpose der Policies (derzeit `cx.quality.base:1`, individuell z. B. `msg.ppap.productApproval:1`) und `cx-taxo`-Typen der Assets (`PpapNotificationAPI`, `PpapPublicKeys`).
- Wiederverwendung `industry_core.part_type` (Teile-Identifikation) und `binary_exchange` (Dokumente, Pull in Phase 5).
- Elementkatalog (VDA Band 2 vs. AIAG PPAP) als gemeinsame Code-Liste statt Freitext-Codes; fachliche Negativ-Quittung.
