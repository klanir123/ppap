# Vertrag Datenaustausch über EDC (Phase 4: UC-04, UC-06 Push, UC-08)

Stand: Phase 4. Ergänzt [approval-api.md](approval-api.md); Architektur: ADR-0001 (EDC-Port), ADR-0002 (Push/Pull). Verbindlich für REST ist die generierte `api/openapi.json`.

## Grundsätze

1. **Nur über EDC.** Jede Nachricht an einen Partner läuft: Katalog (DSP) → Vertragsverhandlung + Transfer (`POST /v3/edrs`) → EDR → `POST` an die **Data Plane des Partners** → Data Plane leitet an dessen Backend (`/internal/edc/notifications`). Keine direkten Aufrufe zwischen Backends.
2. **Transactional Outbox.** Domain Events des Moduls `approval` erzeugen in **derselben Transaktion** eine Outbox-Nachricht (Modul `exchange`). Ein Worker (`SELECT … FOR UPDATE SKIP LOCKED`) versendet asynchron; Retry mit exponentiellem Backoff (1 s … 15 min, max. 20 Versuche), danach `FAILED` (manueller Retry im Cockpit).
3. **Idempotenter Empfang.** Eingehende Nachrichten werden zuerst unverändert in der **Inbox** gespeichert (Payload + SHA-256), Unique `(sender_bpnl, message_id)`; Doppelte → `200` ohne Wirkung. Verarbeitung asynchron über die `Receive*UseCase`-Ports.
4. **Jeder Schritt ist sichtbar.** Pro Nachricht ein `ExchangeRecord` mit Einzelschritten (Katalog, Verhandlung, Transfer/EDR, Zustellung bzw. Empfang, Verarbeitung) inkl. EDC-IDs, Dauer, HTTP-Status, Fehler und Payload-Hash. Live-Updates per SSE.
5. **Phase 4 = Push.** Einreichungen werden vollständig als Notification übertragen (`PPAP_SUBMISSION`). Beziehungen im Modus `PUSH_NOTIFY_PULL_PAYLOAD` verhalten sich bis Phase 5 wie `PUSH` (Log-Hinweis). Dokumente werden nur als Metadaten (Name, Typ, Größe, SHA-256) übertragen; der Abruf per Pull folgt in Phase 5.
6. **Netz (lokal).** Backends hängen zusätzlich am Netz `dataspace`, um die öffentliche Data Plane von Partnern zu erreichen (so wie im echten Betrieb). Eingehend ist das Backend nur über die eigene Data Plane erreichbar; `/internal/edc/**` verlangt das Shared Secret, `/api/**` Tokens des eigenen Realms. Der Tractus-X-„Consumer Proxy“ wird bewusst nicht genutzt (laut EDC nur temporär vorhanden).

## Angebotene Assets je Teilnehmer (Provisionierung)

Deklarativ und idempotent (Reconciliation beim Start und bei Änderungen an Beziehungen/Partnern), deterministische IDs:

| Asset-ID | `dct:type` | DataAddress (HttpData) | Zweck |
|---|---|---|---|
| `ppap-notification-receive` | `cx-taxo:PpapNotificationAPI` (vorläufig, `TODO(EG)`), `cx-common:version` `0.1` | `baseUrl = http://<p>-app:8080/internal/edc/notifications`, `proxyMethod`, `proxyBody` = true, Shared Secret als Header (über Vault-Secret, nicht im Klartext in der EDC-DB, sofern vom EDC unterstützt) | Empfang aller Notifications |
| `ppap-public-keys` | `cx-taxo:PpapPublicKeys` (vorläufig) | `baseUrl = http://<p>-app:8080/internal/edc/jwks`, GET | Öffentlicher Signaturschlüssel (JWKS) des Teilnehmers für Partner |

- **Access Policy:** `BusinessPartnerGroup isAnyOf ["ppap-partners"]`; die Gruppe wird über `/v3/business-partner-groups` aus allen aktiven, Catena-X-fähigen Partnern mit Beziehung gepflegt (keine Neuanlage von Contract Definitions bei Partneränderungen).
- **Usage Policy:** `FrameworkAgreement eq "DataExchangeGovernance:1.0"` + `UsagePurpose isAnyOf [<konfigurierbar, Default "cx.quality.base:1" bis ein eigener Zweck abgestimmt ist>]` (CX-0152; `TODO(EG)`). Policies kommen aus der Konfiguration (`ppap.edc.policy.*`), nicht aus Code-Konstanten; in Phase 6 je Beziehung pflegbar.
- **Contract Definitions:** je Asset eine (`ppap-notification-cd`, `ppap-public-keys-cd`).

## Nachricht (Umschlag) – `PpapNotification` v0.1

```json
{ "header": {                      // = urn:samm:io.catenax.shared.message_header:3.0.0#MessageHeaderAspect
    "messageId": "uuid", "context": "urn:samm:com.msg.ppap:0.1.0#PpapSubmission", "sentDateTime": "…Z",
    "senderBpn": "BPNL…", "receiverBpn": "BPNL…", "expectedResponseBy": null, "relatedMessageId": null, "version": "3.0.0" },
  "content": {
    "messageType": "PPAP_REQUEST | PPAP_REQUEST_WITHDRAWN | PPAP_STATUS_UPDATE | PPAP_SUBMISSION | PPAP_DECISION",
    "exchangeId": "uuid", "correlationId": "…",
    "payload": { … },                // PpapRequest | {reason} | PpapStatusUpdate | PpapSubmission | PpapDecision (v0.1)
    "payloadHash": "sha256:…",       // über RFC-8785-kanonisches payload
    "signature": "JWS (nur SUBMISSION, DECISION)" } }
```
Schema-Validierung beim Senden **und** Empfangen (Modul `semantics`). `PpapStatusUpdate` = `{ exchangeId, status: IN_PROGRESS|IN_REVIEW, revision, at, by }`.

## Ereignis → Nachricht → Wirkung beim Partner

| Ereignis (Sender) | Nachricht | Empfänger-Wirkung |
|---|---|---|
| Kunde sendet Anforderung (`CaseRequested`) | `PPAP_REQUEST` | Lieferant: neuer Vorgang `source=EXCHANGE`, Status REQUESTED, Teil über Teilezuordnung (Kundensachnummer) ermittelt. **Nicht zuordenbar** → Vorgang mit `clarification = { reason: PART_NOT_MAPPED, partnerPartNumber }` (Klärungsliste, UC-04 A1). Absender ohne Beziehung → Inbox `REJECTED_UNKNOWN_PARTNER`. |
| Kunde bricht ab (`CaseCancelled`) | `PPAP_REQUEST_WITHDRAWN` | Lieferant: CANCELLED (mit Grund) |
| Lieferant startet Bearbeitung / neue Revision | `PPAP_STATUS_UPDATE` (IN_PROGRESS) | Kunde: IN_PROGRESS |
| Lieferant reicht ein (`SubmissionSubmitted`) | `PPAP_SUBMISSION` | Kunde: Revision gespeichert (kanonisches Payload), Hash geprüft (`PAYLOAD_HASH_MISMATCH` → abgelehnt), Signatur geprüft gegen Partner-JWKS → VALID / INVALID (abgelehnt) / UNVERIFIED; Status SUBMITTED |
| Kunde startet Prüfung | `PPAP_STATUS_UPDATE` (IN_REVIEW) | Lieferant: IN_REVIEW |
| Kunde entscheidet (`DecisionMade`) | `PPAP_DECISION` | Lieferant: Entscheidung gespeichert, Signatur geprüft, Status APPROVED / CONDITIONALLY_APPROVED / REJECTED |

Remote-Übergänge laufen über die Zustandsmaschine (eigene Aktionen `RECEIVE_*`, Akteur `partner:<BPNL>`), unzulässige → Inbox `REJECTED_INVALID_STATE`.

**Partnerschlüssel:** Ist der `kid` einer Signatur unbekannt, holt der Empfänger das JWKS des Absenders über EDC (Asset `ppap-public-keys`, gleicher Ablauf wie oben, `GET`) und speichert es (`partner_key`: bpnl, kid, jwk, fetchedAt). Erst wenn auch danach unbekannt → `UNVERIFIED`.

**Herkunftsprüfung:** Der Absender (`header.senderBpn`) muss eine aktive Beziehung haben. Reicht die Data Plane die Identität des Konsumenten durch (z. B. Header `Edc-Bpn` – im Tractus-X-EDC-0.13-Quellcode zu verifizieren), muss sie mit `senderBpn` übereinstimmen (`REJECTED_SENDER_MISMATCH`); andernfalls dokumentieren und auf Policy (BPN-Gruppe) + Signatur stützen.

## REST

### Austausch je Vorgang – `GET /api/v1/approval-cases/{id}/exchange`
```json
[ { "id": "uuid", "messageId": "uuid", "direction": "OUTBOUND|INBOUND", "messageType": "PPAP_SUBMISSION", "partner": { "bpnl", "name" },
    "status": "PENDING|IN_PROGRESS|DELIVERED|FAILED|RECEIVED|PROCESSED|REJECTED", "attempts": 1, "nextAttemptAt": null,
    "payloadHash": "sha256:…", "correlationId": "…", "createdAt": "…", "completedAt": "…", "lastError": null,
    "steps": [ { "step": "CATALOG|NEGOTIATION|TRANSFER|EDR|DELIVERY|RECEIPT|VALIDATION|PROCESSING|KEY_FETCH",
                 "status": "OK|FAILED|SKIPPED", "at": "…", "durationMs": 312,
                 "details": { "assetId": "…", "offerId": "…", "negotiationId": "…", "agreementId": "…", "transferProcessId": "…", "httpStatus": 202, "cachedEdr": true } } ] } ]
```
Jede angemeldete Rolle mit Zugriff auf den Vorgang.

### Nachrichten (Cockpit-Grundlage, `CONNECTOR_ADMIN`)
- `GET /api/v1/exchange/messages?direction=&status=&partnerBpnl=&page=&size=` → Page von ExchangeRecords (ohne Payload).
- `GET /api/v1/exchange/messages/{id}` → inkl. Payload (JSON) für Diagnose.
- `POST /api/v1/exchange/messages/{id}/retry` → nur `FAILED` (ausgehend) bzw. `REJECTED`/fehlgeschlagene Verarbeitung (eingehend, erneute Verarbeitung).
- `GET /api/v1/exchange/status` → `{ edc: UP|DOWN, provisioning: { assets: [...], ok: true, lastRun }, outbox: { pending, failed }, inbox: { rejected } }`.

### Klärungsliste (UC-04 A1)
- Vorgang-DTO erhält `clarification: { reason: "PART_NOT_MAPPED", partnerPartNumber } | null`; Liste filterbar `GET /approval-cases?needsClarification=true`.
- `POST /api/v1/approval-cases/{id}/assign-part` `{ partId, saveMapping: true }` (SUPPLIER_QUALITY_MANAGER) → ordnet zu, legt optional die Teilezuordnung an, entfernt `clarification`.

### Live-Updates – `GET /api/v1/events` (`text/event-stream`, Bearer-Token)
Ereignisse: `case.changed { caseId, status }`, `exchange.changed { caseId, messageId, status }`, `heartbeat` (alle 25 s). Nur Ereignisse des eigenen Mandanten; Frontend nutzt `fetch`-basiertes SSE (Header `Authorization`) und invalidiert die passenden Queries.

### Intern (nur über die eigene Data Plane)
- `POST /internal/edc/notifications` – Header mit Shared Secret (`PPAP_EDC_CALLBACK_SECRET`), sonst `401`; Body = `PpapNotification`; Antworten `202` (angenommen), `200` (Duplikat), `400` (Schema), `401`.
- `GET /internal/edc/jwks` – öffentliches JWKS (gleich `/.well-known/jwks.json`), ebenfalls Shared Secret.

## Konfiguration (Env)
`PPAP_EDC_MANAGEMENT_URL`, `PPAP_EDC_API_KEY`, `PPAP_EDC_DSP_URL`, `PPAP_EDC_PARTICIPANT_ID` (DID), `PPAP_EDC_CALLBACK_BASE_URL` (`http://<p>-app:8080`), `PPAP_EDC_CALLBACK_SECRET`, `PPAP_EDC_POLICY_USAGE_PURPOSE`, `PPAP_EXCHANGE_WORKER_INTERVAL`, `PPAP_EXCHANGE_MAX_ATTEMPTS`. Partner-DSP-Endpoint und DID kommen aus den Stammdaten (Partner).

## Ergänzungen (Abstimmung Frontend, Phase 4)
1. Vorgang (Liste + Detail): `lastOutbound: { messageType, status, at } | null` – letzte ausgehende Nachricht für den Zustell-Badge.
2. `GET /approval-cases?needsClarification=true` filtert; `GET /approval-cases/stats` enthält `needsClarification` (Anzahl).
3. Nachrichten-DTOs (Vorgang und Admin) enthalten `caseId` und `caseNumber`.
4. `assign-part` beachtet `If-Match` und liefert die neue Version im `ETag`.
5. SSE: Ereignisname im Feld `event:` (`case.changed`, `exchange.changed`, `heartbeat`), JSON in `data:`.
