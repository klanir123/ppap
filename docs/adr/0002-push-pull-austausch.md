# ADR-0002 Austauschmuster: Push (Notification) und Pull (Digital Twin)

## Kontext
Zwischen Kunde und Lieferant werden kleine Steuernachrichten (Anforderung, Status, Entscheidung) und größere Inhalte (PPF-Paket mit Elementen, Nachweisdokumente) ausgetauscht. Catena-X kennt zwei etablierte Muster: **Notifications** (Industry Core, Quality: Asset je Empfangs-API, POST über Data Plane) und **Pull über Digital Twin Registry** (Digital Twin KIT: DTR finden → Shell suchen → Submodel abrufen). Ergebnis soll in die Expert Group eingebracht werden können.

## Entscheidung
Beide Muster werden implementiert, **je Kunden-Lieferanten-Beziehung** konfigurierbar (`exchangeMode`):

| Modus | Anforderung / Status / Entscheidung | PPF-Paket (Submission) | Dokumente |
|---|---|---|---|
| `PUSH` | Notification | Notification (vollständiges JSON) | Pull (EDC-Asset je Dokument bzw. Vorgang) |
| `PULL` | Notification (Anforderung, Entscheidung) | Pull über DTR (Kunde pollt per `specificAssetIds`) | Pull über DTR |
| **`PUSH_NOTIFY_PULL_PAYLOAD`** (Standard) | Notification | Notification `SUBMISSION_AVAILABLE` → Pull über DTR | Pull über DTR |

Dokumente werden in **keinem** Modus in Notifications eingebettet (Größe, Policies, Binärdaten).

### Assets je Teilnehmer
- `ppap-notification-receive` (`dct:type` `cx-taxo:PpapNotificationAPI` – vorläufig, `TODO(EG)`), `HttpData` → `http://<backend>/internal/edc/notifications`, `proxyBody=true`, `proxyMethod=true`, `proxyPath=false`, Shared Secret. Access Policy: Membership + BPN ∈ Partnergruppe „ppap-partners“ (wird aus den Beziehungen gepflegt). Usage Policy: FrameworkAgreement + UsagePurpose `msg.ppap.productApproval:1`.
- Lieferant zusätzlich: `ppap-dtr` (`cx-taxo:DigitalTwinRegistry`) und je Kunde ein Submodel-/Dokument-Asset `ppap-submission-<customerBpnl>` (`cx-taxo:Submodel`) mit `proxyPath=true` → Backend-Endpunkt `/internal/edc/submodels/...`, der zusätzlich den vom Data Plane weitergereichten Consumer prüft. Access Policy: **nur diese BPNL**.

### Herkunftsprüfung beim Empfang
Der Empfänger vertraut nicht nur dem Header `senderBpn`: Die Data Plane wird so konfiguriert, dass sie Agreement-/Consumer-Informationen weiterreicht (z. B. `Edc-Bpn`- bzw. Contract-Header, sofern in 0.13 verfügbar – Spike Phase 4); ansonsten Prüfung über die Asset-Access-Policy (nur Partner der Gruppe können überhaupt zustellen) + JWS-Signatur bei Submission/Decision. `TODO(EG)`

### Abwägung

| Kriterium | Push (Notification mit Payload) | Pull (DTR) |
|---|---|---|
| Latenz/Einfachheit | sofort, 1 Asset, wenig Infrastruktur | Polling oder Notification nötig, DTR nötig |
| Datenhoheit | Daten liegen nach Versand beim Empfänger | Daten bleiben beim Anbieter, Abruf je Policy, Abruf protokolliert |
| Große Inhalte | ungeeignet (Proxy-Limits, Timeouts) | geeignet (Streaming, einzelne Dokumente) |
| Catena-X-Konformität | Industry-Core-Notification-Muster | Digital Twin KIT, Wiederverwendung von Twins (Teil ↔ Freigabe) |
| Kosten kleiner Zulieferer | gering | DTR je Lieferant (Betrieb!) – gehostet sinnvoll |
| Nachvollziehbarkeit | Zustellquittung | Zugriffsnachweis je Abruf |

**Empfehlung (Standard):** Notification als Auslöser + Paket per Pull – vereint Reaktionszeit mit Datensouveränität und skaliert für Dokumente. Reiner PUSH als Option für kleine Zulieferer ohne eigene DTR (z. B. bei Hosting).

### Twin-Modell (Pull)
- Eine **Shell je Freigabevorgang** (nicht je Teil), `globalAssetId` = `urn:uuid:<ApprovalCase-ID>`; `specificAssetIds`: `customerPartId`, `manufacturerPartId`, `manufacturerId` (Lieferanten-BPNL), `ppapCaseId` (Kunden-Vorgangs-ID); `externalSubjectId` = Kunden-BPNL (Sichtbarkeit).
- Submodel-Deskriptoren: `PpapSubmission` (je Revision, semanticId `urn:samm:com.msg.ppap:0.1.0#PpapSubmission`) und `BinaryExchange`-artige Dokumentenliste (`urn:samm:io.catenax.binary_exchange:1.0.0`, Prüfung in Phase 5).
- Alternative „Shell je Teil mit Freigabe-Submodel“ wird für die Expert Group dokumentiert (Wiederverwendung des Industry-Core-Twins), im Prototyp aber nicht umgesetzt, da ein Teil viele Vorgänge/Revisionen haben kann. `TODO(EG)`

## Konsequenzen
+ Beide Muster demonstrierbar und messbar (Phase 5 liefert Kennzahlen: EDC-Schritte, Latenz).
− Mehr Implementierungsaufwand (DTR-Client, Submodel-Endpunkte, EDR-Handling für zwei Pfade).

## Umsetzung Phase 4 – Herkunftsprüfung (verifiziert)
Die Provider-Data-Plane von Tractus-X EDC 0.13 setzt beim Aufruf des Backends die Header **`Edc-Bpn`** (BPNL des Konsumenten aus dem Contract Agreement, `TxDataFlowPropertiesProvider` → `DataPlaneTokenRefreshServiceImpl`) und `Edc-Contract-Agreement-Id`. Live verifiziert (OEM → Tier-1: `Edc-Bpn = BPNL00000000OEM1`). Der Empfänger speichert `Edc-Bpn` in der Inbox und weist Nachrichten mit abweichendem `header.senderBpn` als `REJECTED_SENDER_MISMATCH` ab. Zusätzlich: aktive Beziehung zum Absender (Access Policy `BusinessPartnerGroup ppap-partners`) und JWS-Signatur bei Submission/Decision.
