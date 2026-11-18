# ADR-0003 Semantisches Modell: vorläufige SAMM-Modelle + austauschbare Mapping-Schicht

## Kontext
Für PPAP/PPF existiert noch kein Catena-X-Standard; die Expert Group „Product Approval“ (Kickoff 02/2026) erarbeitet ihn auf Basis VDA 231-300/-301 mit Ziel-Release 27.03. Es ist offen, ob Aspektmodelle der Expert Group Engineering wiederverwendet werden. Unser Produkt muss heute strukturierte, validierbare Payloads austauschen und morgen auf den Standard wechseln können, ohne die Domäne umzubauen.

## Entscheidung
1. **Eigene SAMM-Modelle** (SAMM 2.x, Turtle) im Namespace `urn:samm:com.msg.ppap:0.1.0#` unter `semantics/models/`:
   - `PpapRequest` – Anforderung (Vorgang, Teil, Kunde, Lieferant, Grund, Stufe, geforderte Elemente inkl. Snapshot, Termin, Anhänge-Referenzen)
   - `PpapSubmission` – Einreichung (Revision, Elemente mit Selbstbeurteilung/Werten/Kommentar, Dokument-Metadaten inkl. SHA-256, Unterlieferanten-Referenzen, Signatur-Referenz)
   - `PpapDecision` – Entscheidung (Ergebnis, Auflagen + Frist, Begründungen je Element, Bezug auf Submission-Hash, JWS)
   - `PpapNotification` – Umschlag (Header: messageId, correlationId, senderBpn, recipientBpn, timestamp, messageType, contentModel-URN; Content: eines der obigen oder `SubmissionAvailable`/`StatusUpdate`). Struktur analog Catena-X-Notifications (Header/Content).
2. **Wiederverwendung** (Recherche 11/2026, siehe tractusx-versions.md):
   - Umschlag: `urn:samm:io.catenax.shared.message_header:3.0.0#MessageHeaderAspect` als Header von `PpapNotification` (identisch zu Industry-Core-Notifications).
   - Teile-Identifikation: Begriffe aus `urn:samm:io.catenax.industry_core.part_type:2.0.0#PartType` (manufacturerPartId, nameAtManufacturer, customerPartId) bzw. `specificAssetIds` des Digital Twin.
   - Dokumente: Prüfung, ob `urn:samm:io.catenax.binary_exchange:1.0.0#BinaryExchange` (Dateimetadaten + DSP-Zugriff) direkt für Nachweisdokumente verwendet werden kann (Phase 5).
   - **Prüf- und Messergebnisse:** Die öffentlich verfügbaren JSON-Schemas von **VDA 231-301** (MIT-Lizenz) werden als optionales Werte-Schema für die Elemente „Prüfergebnisse Maß/Werkstoff/Funktion“ eingebunden; **VDA 231-300** (Werkstoff-/Oberflächensemantik) für Werkstoffangaben. So nutzen wir die Grundlage der Expert Group bereits jetzt, statt eigene Strukturen zu erfinden.
   - `TODO(EG)`: Engineering-Modelle (`DigitalEngineeringMasterData`, `Requirement`) bei Bedarf referenzieren.
3. **Generierung im Build:** `esmf-aspect-model-maven-plugin` (ESMF SDK 2.16.x) erzeugt JSON-Schemas (+ Beispiel-Payloads, HTML-Doku) aus den `.ttl`-Dateien. Die Schemas sind Build-Artefakt und werden für **jede** ein- und ausgehende Payload mit `networknt json-schema-validator` geprüft.
4. **Mapping-Schicht** im Modul `semantics`: Port `ExchangeModelMapper<Domain, Payload>` mit versionierter Implementierung (`v0_1`). Die Domäne kennt nur eigene Typen; Austauschformate (generierte DTOs oder `JsonNode`) bleiben im Modul `semantics`. Eingehende Nachrichten tragen die Modell-URN; der passende Mapper wird per URN gewählt → mehrere Versionen parallel (Übergangsphase zum Standard).
5. **Kanonisierung:** Payload-Hashes über RFC 8785 (JSON Canonicalization Scheme).

## Alternativen
- *Nur JSON Schema ohne SAMM*: einfacher, aber nicht Catena-X-konform und schlecht in die Expert Group einbringbar.
- *Warten auf Standard*: blockiert das Produkt.
- *Interne Domäne = Austauschformat*: schnell, aber jede Standardänderung trifft die Domäne.

## Konsequenzen
+ Austausch heute validierbar, Modelle als Diskussionsbeitrag für die Expert Group nutzbar.
+ Standardwechsel = neuer Mapper + neue Schemas, Domäne bleibt.
− Pflegeaufwand für SAMM-Modelle; ESMF-Plugin im Build (Java-basiert, unkritisch).
