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

## Umsetzung Phase 5 – Pull über DTR (2026-11-19)
- **Modus je Beziehung beim Lieferanten**, wirksam ab der nächsten Einreichung, je Revision gespeichert (`submissions[].exchangeMode`, beidseitig: Lieferant = wie bereitgestellt, Kunde = wie empfangen).
- **Twin-Modell wie entschieden:** eine Shell je Vorgang (`urn:uuid:<exchangeId>`), `specificAssetIds` `customerPartId`, `manufacturerPartId`, `manufacturerId`, `ppapExchangeId` mit `externalSubjectId` = Kunden-BPNL; je per Pull bereitgestellter Revision ein Submodel-Deskriptor `PpapSubmission` (ID = name-basierte UUID aus exchangeId + Revision), Endpoint = eigene Data Plane + `subprotocolBody` `id=ppap-submission-<Kunden-BPNL>;dspEndpoint=…`. Im Modus PUSH wird kein Twin registriert (kleine Zulieferer ohne DTR).
- **Registrierung als lokaler Outbox-Schritt** `TWIN_REGISTRATION` (gleiche Transaktion wie die Einreichung, gleiche Reihenfolge-Garantie je exchangeId wie Notifications, Retry/Backoff): die Notification `PPAP_SUBMISSION_AVAILABLE` wird erst nach erfolgreicher Registrierung zugestellt. Erweiterungspunkt `exchange.api.OutboxTaskHandler`, implementiert im Modul `twin` (keine Zyklen: `twin → exchange.api`).
- **Pull-Job beim Kunden im Modul `exchange`** (nicht `twin`): er orchestriert EDC (Katalog nach `dct:type`, EDR-Cache), die Inbox-Prüfungen (Hash, Partner-JWKS, Signatur) und den Dokumenttransfer; das AAS-Format (Deskriptor bauen/lesen, `subprotocolBody`) liegt als reine Funktionen im Modul `semantics` (`AasDescriptors`, `DigitalTwinIds`). `twin` = eigene DTR (Client mit Keycloak-Client-Credentials, Registrierung, `GET /api/v1/twins/{exchangeId}`).
- **Abruf** `PULL_SUBMISSION`/`PULL_DOCUMENT` sind eingehende Datensätze in `ex_message`, abgearbeitet wie die Outbox (SKIP LOCKED, Lease, Backoff); Schritte mit `target` (`DTR`, `SUBMODEL`, `DOCUMENT`, `NOTIFICATION`, `PUBLIC_KEYS`), damit wiederholte Katalog-/Verhandlungsschritte verschiedener Assets nicht als Retry erscheinen.
- **Polling (PULL):** je offenem eigenen Vorgang (REQUESTED/IN_PROGRESS) Lookup über `ppapExchangeId` → Shell → Submodel-Deskriptor der nächsten Revision(en); DTR-Angebot je Partner gecacht (auch „kein DTR“, 10 min), daher kostet ein PUSH-Lieferant nur eine Katalogabfrage je Cache-Periode. Der Kunde muss den Modus des Lieferanten nicht kennen; Notification und Poll führen zum selben idempotenten Job (deterministische Message-ID).
- **Dokumente in allen Modi** per Pull über `ppap-documents-<eigene BPNL>` beim Partner, `GET /<sha256>`, gestreamt, SHA-256 vor dem Speichern geprüft (`DOCUMENT_HASH_MISMATCH`), Uploadlimit gilt; Auslöser: nach Verarbeitung einer empfangenen Einreichung (Nachweise) bzw. Anforderung (Anhänge).
- **Herkunftsprüfung beim Bereitstellen:** Submodel/Dokument nur, wenn der von der Data Plane gesetzte `Edc-Bpn` Partner des Vorgangs ist (Revision eingereicht bzw. Dokument Nachweis einer eingereichten Revision / Anhang einer gesendeten Anforderung); jeder ausgelieferte Abruf wird protokolliert (Zugriffsnachweis im Log; `TODO`: als Audit-Ereignis).

## Messwerte Phase 5
Messung am 2026-11-19 lokal (Docker Compose, ein Host, Tractus-X EDC 0.13.0, DTR 0.13.0, Wallet-Stub 0.1.0) mit `PullModesLiveTest`: OEM (Kunde) ⇄ Tier-1 (Lieferant), je Modus ein Durchlauf mit **kalten EDRs** (alle EDRs beider EDCs gelöscht) und einer mit **warmen EDRs**. Anforderung mit 1 PDF-Anhang, Einreichung mit 1 PDF-Nachweis (wenige KB). Poll-Intervall für PULL: 10 s (Standard 60 s).

| Modus | EDR | Senden → Anforderung + Anhang beim Lieferanten | Einreichen → Kunde SUBMITTED | Einreichen → Nachweis beim Kunden geprüft |
|---|---|---|---|---|
| PUSH | kalt | 13,6 s | 5,4 s¹ | 11,5 s |
| PUSH | warm | 0,26 s | 0,27 s | 0,30 s |
| PUSH_NOTIFY_PULL_PAYLOAD | kalt | 12,1 s | 11,9 s¹ ² | 19,9 s |
| PUSH_NOTIFY_PULL_PAYLOAD | warm | 0,26 s | 0,58 s | 0,60 s |
| PULL | kalt | 12,9 s | 17,7 s¹ ³ | 23,7 s |
| PULL | warm | 0,22 s | 7,8 s ³ | 7,8 s |

¹ enthält das Warten auf die vorausgehende Statusmeldung IN_PROGRESS, deren Verhandlung (≈ 5 s) noch lief (Zustellung je exchangeId in Reihenfolge). ² Der DTR-EDR war bereits durch den Poller des Kunden vorhanden (er pollt alle offenen Vorgänge, auch bei PUSH-Lieferanten); gemessen wurde die kalte Verhandlung des Submodel-Assets. ³ dominiert vom Poll-Intervall (Mittel ½ Intervall).

**Einzelschritte (ExchangeRecord, Millisekunden):** Katalog 34–250, **Vertragsverhandlung 4 100–6 200**, Transfer/EDR 260–2 050, EDR-Abruf aus dem Cache 0–13; DTR-Lookup 28–43, Shell-Deskriptor 35–44, Submodel-Abruf 17–23, Signaturprüfung 2–4, Verarbeitung 34–43; Dokumentabruf 43–350 (Dateigröße wenige KB); `TWIN_REGISTRATION` in der eigenen DTR 61–131; Zustellung einer Notification 19–55.

**EDC-Interaktionen je Einreichung (Lieferant → Kunde, ohne Statusmeldungen):**

| Modus | kalt: Verhandlungen (je Katalog + Negotiation + Transfer + EDR) | Data-Plane-Aufrufe | zusätzlich |
|---|---|---|---|
| PUSH | 1 (Notification-Asset) + 1 je Dokument-Asset | 1 POST + 1 GET je Dokument | – |
| PUSH_NOTIFY_PULL_PAYLOAD | 3 (Notification, DTR, Submodel) + 1 Dokument-Asset | 1 POST + 4 GET (Lookup, Shell, Submodel, Signatur) + 1 GET je Dokument | 1 DTR-Registrierung (PUT + GET, ohne EDC) |
| PULL | 2 (DTR, Submodel) + 1 Dokument-Asset | 2 GET je Poll und offenem Vorgang + 4 GET (Abruf) + 1 GET je Dokument | Polling-Last ∝ offene Vorgänge / Intervall |

Warm (EDR wiederverwendet) entfallen alle Verhandlungen; je Asset bleibt eine EDR-Abfrage an der eigenen Management API (`/v3/edrs/request` + `dataaddress?auto_refresh=true`). Dokumente werden je Partner über **ein** Asset (`ppap-documents-<BPNL>`, Pfad = SHA-256) bereitgestellt, also eine Verhandlung je Partnerbeziehung, nicht je Dokument.

**Bewertung für die Expert Group:**
1. **Die Kosten liegen fast vollständig in der ersten Vertragsverhandlung je Asset (4–6 s), nicht im Muster.** Mit wiederverwendeten EDRs ist Notify + Pull (≈ 0,6 s Ende-zu-Ende) praktisch so schnell wie Push (≈ 0,3 s); die DTR-Schritte kosten zusammen < 0,1 s.
2. **Notify + Pull** kostet kalt zwei zusätzliche Verhandlungen (DTR, Submodel) und vier Data-Plane-Aufrufe mehr, liefert dafür Datensouveränität (Abruf je Policy, Zugriffsnachweis beim Anbieter), Wiederholbarkeit (der Kunde kann jederzeit erneut abrufen) und skaliert für Dokumente. Empfehlung bleibt **`PUSH_NOTIFY_PULL_PAYLOAD` als Standard**.
3. **Reines PULL** ist für Freigaben nur als Ausweichlösung geeignet: Latenz = Poll-Intervall, Last wächst mit der Zahl offener Vorgänge × Partner, und jeder Poll verursacht Data-Plane-Verkehr auch ohne Änderung. Sinnvoll für Kunden ohne erreichbaren Notification-Endpunkt.
4. **Dokumente immer per Pull** (alle Modi) hat sich bewährt: ein Asset je Partner, Inhaltsadressierung über SHA-256 (in der signierten Einreichung) macht Integrität prüfbar, ohne dass Dokumente die Notification-Größe beeinflussen.
5. Für einen Standard (`TODO(EG)`): (a) Asset-Typen `cx-taxo:PpapNotificationAPI`, `cx-taxo:PpapDocuments` festlegen; (b) Ort der detached Signatur im Pull festlegen – die EDC-0.13-Data-Plane reicht keine Antwort-Header durch, daher bei uns Geschwister-Ressource `…/signature`; Alternative: signierter Umschlag als Submodel-Inhalt; (c) Shell je Vorgang vs. Freigabe-Submodel am Industry-Core-Twin des Teils (bei uns je Vorgang, da ein Teil viele Vorgänge/Revisionen hat); (d) Polling-Intervall/Abbruchkriterien für PULL normieren.
