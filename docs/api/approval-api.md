# API-Vertrag Freigabevorgang (UC-03, UC-05, UC-07) – `/api/v1`

Stand: Phase 3 („Freigabevorgang lokal“). Verbindlich ist die generierte `api/openapi.json`; dieses Dokument beschreibt Absicht, Abläufe und Entscheidungen. Konventionen (Auth, ETag/If-Match, Page, Problem Details, Audit) wie in [masterdata-api.md](masterdata-api.md).

## Grundsatzentscheidungen

1. **Ein Vorgang je Seite.** Kunde und Lieferant führen jeweils einen eigenen `ApprovalCase` (`ownRole = CUSTOMER | SUPPLIER`). Verknüpft sind beide über die vom Kunden erzeugte **`exchangeId`** (UUID), die in Phase 4 in allen Nachrichten mitläuft. Die Vorgangsnummer (`caseNumber`, z. B. `PPF-2026-00017`) ist mandantenlokal.
2. **Phase 3 = lokal, ohne Mocks im Datenpfad.** „Senden“ (Kunde) und „Einreichen“ (Lieferant) ändern den Status und veröffentlichen Domain Events (`CaseRequested`, `SubmissionSubmitted`, `DecisionMade`); die Zustellung über EDC (Outbox) folgt in Phase 4. Eingehende Nachrichten werden später über die Application-Ports `ReceiveRequestUseCase`, `ReceiveSubmissionUseCase`, `ReceiveDecisionUseCase` verarbeitet – in Phase 3 nur aus Tests aufgerufen.
3. **Manuell erfasste Anforderungen.** Ein Lieferant kann eine Anforderung eines Kunden, der nicht über Catena-X angebunden ist (Portal, E-Mail), manuell erfassen (`source = MANUAL`). Das ist ein echter Anwendungsfall kleiner Zulieferer und ermöglicht UC-05 lokal.
4. **Signiert wird das Austauschformat.** Einreichung und Entscheidung werden vom Modul `semantics` in das Austauschformat v0.1 (`PpapSubmission`, `PpapDecision`) überführt, gegen JSON Schema validiert, nach RFC 8785 kanonisiert, per SHA-256 gehasht und als **JWS (ES256, detached)** mit dem Mandantenschlüssel signiert (ADR-0008). Gespeichert wird das kanonische Payload selbst (unveränderlich). Phase 4 überträgt genau dieses Payload; die JSON Schemas werden dann aus SAMM generiert (gleiche Struktur).
5. **Snapshots.** Die Anforderung friert Elementdefinitionen (Name DE/EN, Nachweistyp, Werte-Schema), Grund und Stufe zum Zeitpunkt des Sendens ein; Katalogänderungen wirken nicht auf laufende Vorgänge (UC-02).

## Zustandsmaschine

`DRAFT → REQUESTED → IN_PROGRESS → SUBMITTED → IN_REVIEW → APPROVED | CONDITIONALLY_APPROVED | REJECTED`; `REJECTED | CONDITIONALLY_APPROVED → IN_PROGRESS` (neue Revision); `DRAFT | REQUESTED → CANCELLED`. UI-Bezeichnungen (DE): Entwurf, Angefordert, In Bearbeitung, Eingereicht, In Prüfung, Freigegeben, Bedingt freigegeben, Abgelehnt, Abgebrochen.

| Übergang | Aktion (Endpoint) | Rolle des Mandanten im Vorgang | Benutzerrolle | Guard |
|---|---|---|---|---|
| – → DRAFT | `POST /approval-cases` | CUSTOMER | CUSTOMER_QUALITY_ENGINEER, SYSTEM_INTEGRATION | Beziehung + Teilezuordnung existieren |
| – → REQUESTED | `POST /approval-cases/manual` | SUPPLIER | SUPPLIER_QUALITY_MANAGER, SYSTEM_INTEGRATION | Beziehung (Partner = Kunde) existiert |
| DRAFT → REQUESTED | `POST /{id}/send` | CUSTOMER | CUSTOMER_QUALITY_ENGINEER, SYSTEM_INTEGRATION | ≥ 1 Element, Termin in der Zukunft, Partner `exchangeReady` (`PARTNER_NOT_EXCHANGE_READY`) |
| DRAFT/REQUESTED → CANCELLED | `POST /{id}/cancel` (Begründung) | CUSTOMER (bzw. SUPPLIER bei MANUAL) | wie oben | – |
| REQUESTED → IN_PROGRESS | `POST /{id}/start-work` | SUPPLIER | SUPPLIER_QUALITY_MANAGER, SYSTEM_INTEGRATION | legt Entwurfs-Revision an |
| IN_PROGRESS → SUBMITTED | `POST /{id}/submissions/draft/submit` | SUPPLIER | SUPPLIER_QUALITY_MANAGER, SYSTEM_INTEGRATION | Vollständigkeit (`INCOMPLETE_SUBMISSION` + Liste) |
| SUBMITTED → IN_REVIEW | `POST /{id}/start-review` | CUSTOMER | CUSTOMER_QUALITY_ENGINEER | Revision empfangen, Hash/Signatur gültig |
| IN_REVIEW → Entscheidung | `POST /{id}/decision` | CUSTOMER | CUSTOMER_QUALITY_ENGINEER (SYSTEM_INTEGRATION nur wenn `ppap.decision.allow-technical-clients=true`) | Pflichtbegründungen, optional frische Anmeldung |
| REJECTED/COND. → IN_PROGRESS | `POST /{id}/submissions` (neue Revision) | SUPPLIER | SUPPLIER_QUALITY_MANAGER, SYSTEM_INTEGRATION | Entwurf wird aus letzter Revision vorbelegt |

Unzulässige Übergänge → `409 INVALID_STATE_TRANSITION` (mit `currentStatus`, `action`). Falsche Mandantenrolle → `409 WRONG_CASE_ROLE`. Fehlende Benutzerrolle → `403 FORBIDDEN`.

## Ressourcen

### Vorgang – `/approval-cases`
```json
{ "id": "uuid", "caseNumber": "PPF-2026-00017", "exchangeId": "uuid", "ownRole": "CUSTOMER|SUPPLIER", "source": "LOCAL|MANUAL|EXCHANGE|LEGACY_IMPORT",
  "status": "IN_PROGRESS", "partner": { "bpnl": "…", "name": "…" }, "relationId": "uuid",
  "part": { "id": "uuid", "partNumber": "eigene Sachnummer", "nameDe": "…", "nameEn": "…" }, "partnerPartNumber": "Sachnummer des Partners",
  "customerPartNumber": "(abgeleitet)", "supplierPartNumber": "(abgeleitet)",
  "reason": { "code": "NEW_PART", "nameDe": "…", "nameEn": "…" }, "level": { "code": "VDA-3", "nameDe": "…", "nameEn": "…" },
  "dueDate": "2026-12-15", "overdue": false, "remarks": "…", "currentRevision": 1, "requestedAt": "…", "submittedAt": null, "decidedAt": null,
  "lastDecision": "REJECTED|…|null", "allowedActions": ["START_WORK", "CANCEL", …], "version": 4, "createdAt": "…", "updatedAt": "…" }
```
- `GET /approval-cases?q=&status=IN_PROGRESS,SUBMITTED&ownRole=&partnerBpnl=&overdue=true&page=&size=&sort=dueDate,asc` → Page.
- `GET /approval-cases/{id}` → Detail inkl. Anforderung (`request`), Revisionsliste (`submissions[]` als Kurzform), letzte Entscheidung, `timeline[]` (Zustandswechsel: `{ at, actor, from, to, action, comment }`).
- `allowedActions` berechnet der Server aus Zustand, Mandantenrolle und Benutzerrollen – das UI blendet Aktionen danach ein.
- `GET /approval-cases/stats` → `{ open, overdue, inReview, approvedLast30Days, averageLeadTimeDays, approvalRate, byStatus: { STATUS: n }, byPartner: [{ bpnl, name, open, overdue }] }` (für Dashboard).

#### Anlegen (Kunde)
`POST /approval-cases` `{ partId, relationId? (sonst aus Teilezuordnung, Rolle SUPPLIER), reasonCode, levelCode, dueDate, remarks, elements?: [{ code, requirement, note }] }` → 201, Status DRAFT. Ohne `elements` werden die Elemente der Stufe mit `REQUIRED` vorbelegt (`ON_REQUEST` als optional angeboten). `PUT /approval-cases/{id}` und `PUT /{id}/request/elements` ändern den Entwurf (nur DRAFT).

#### Manuell erfassen (Lieferant)
`POST /approval-cases/manual` `{ partnerBpnl, partId, partnerPartNumber?, reasonCode, levelCode, dueDate, remarks, elements: [{ code, requirement, note }], externalReference? }` → 201, Status REQUESTED, `source = MANUAL`.

### Anforderung (eingebettet) – `request`
```json
{ "requestedAt": "…", "requestedBy": "oem.quality", "elements": [
    { "code": "PPF-11", "requirement": "REQUIRED|ON_REQUEST", "note": "Alle Merkmale der Zeichnung",
      "snapshot": { "nameDe": "…", "nameEn": "…", "evidenceType": "DOCUMENT_AND_VALUES", "valueSchema": { } } } ],
  "attachments": [ DocumentDto ] }
```
Anhänge: `POST /approval-cases/{id}/request/attachments` `{ documentId }`, `DELETE …/{documentId}` (nur DRAFT).

### Revisionen – `/approval-cases/{id}/submissions`
```json
{ "revision": 1, "status": "DRAFT|SUBMITTED", "submittedAt": null, "submittedBy": null,
  "elements": [ { "code": "PPF-11", "requirement": "REQUIRED", "selfAssessment": "OK|NOK|NA|null", "values": { }, "comment": "…",
                  "documents": [ DocumentDto ], "complete": true, "issues": [ { "code": "COMMENT_REQUIRED", "message": "…" } ] } ],
  "completeness": { "complete": false, "requiredTotal": 17, "requiredDone": 12, "issues": [ { "elementCode": "PPF-08", "code": "MISSING_SELF_ASSESSMENT" } ] },
  "payloadHash": "sha256:…(nur SUBMITTED)", "signature": { "alg": "ES256", "kid": "…", "jws": "…", "status": "VALID|INVALID|UNVERIFIED|NOT_SIGNED" },
  "decision": DecisionDto|null, "basedOnRevision": null }
```
- `GET …/submissions` (alle Revisionen, Kurzform), `GET …/submissions/{revision}`, `GET …/submissions/draft`.
- `PUT …/submissions/draft/elements/{code}` `{ selfAssessment, values, comment }` (If-Match über Vorgangs-`version`). Werte werden gegen das Werte-Schema validiert (`VALUE_SCHEMA_VIOLATION` mit JSON-Pointer im `field`).
- `POST …/submissions/draft/elements/{code}/documents` `{ documentId }`, `DELETE …/documents/{documentId}`.
- `POST …/submissions/draft/submit` `{ confirmation: true }` → Revision unveränderlich, `payloadHash` + Signatur, Status SUBMITTED.
- `GET …/submissions/{revision}/payload` → kanonisches Austausch-Payload (`application/json`) + Header `X-Payload-Hash`, `X-Signature` (JWS) – Nachweis/Export.
- `POST …/submissions` → neue Entwurfs-Revision (n+1) nach Ablehnung/Auflagen.

**Vollständigkeitsregeln (Issue-Codes):** jedes `REQUIRED`-Element braucht `selfAssessment` (`MISSING_SELF_ASSESSMENT`); `NOK`/`NA` brauchen `comment` (`COMMENT_REQUIRED`); `evidenceType` DOCUMENT/DOCUMENT_AND_VALUES und Selbstbeurteilung ≠ NA brauchen ≥ 1 Dokument (`DOCUMENT_REQUIRED`); VALUES/DOCUMENT_AND_VALUES mit Werte-Schema und ≠ NA brauchen gültige Werte (`VALUES_REQUIRED`, `VALUE_SCHEMA_VIOLATION`). `ON_REQUEST`-Elemente sind optional, werden aber geprüft, sobald befüllt.

### Excel-Formblatt (UC-05 A1)
- `GET /form-templates` → Vorlagen (Datenbankinhalt, Seed `seed/common/form-templates.json`).
- `POST /approval-cases/{id}/submissions/draft/excel-import/preview` (multipart `file`, optional `templateCode`) → `{ templateCode (erkannt über Markerzelle), header: { field: { value, cell } }, elements: [ { code, selfAssessment, comment, values?, cell, status: OK|WARNING|ERROR, messages } ], unknownCodes: [], warnings: [] }`.
- `POST …/excel-import/apply` (multipart `file`, `templateCode`, `selection`: JSON `{ elementCodes: [...], overwrite: bool }`) → aktualisierter Entwurf; die Originaldatei wird als Dokument gespeichert und an das Element `PPF-01` (Deckblatt) angehängt.

**Vorlagenformat** (Datenbank/Seed): `{ code, nameDe, nameEn, marker: { sheet, cell, value }, header: [ { field, sheet, cell } ], elementTable: { sheet, firstRow, lastRow?, columns: { code: "A", selfAssessment: "D", comment: "E" } }, selfAssessmentValues: { "i.O.": "OK", "n.i.O.": "NOK", "entfällt": "NA", … } }`.

### Prüfung und Entscheidung (Kunde)
- `PUT /approval-cases/{id}/review/elements/{code}` `{ result: ACCEPTED|NOT_ACCEPTED|ACCEPTED_WITH_CONDITION, comment }` (nur IN_REVIEW; Zwischenstand, nicht signiert).
- `POST /approval-cases/{id}/decision` `{ result: APPROVED|CONDITIONALLY_APPROVED|REJECTED, conditions: [ { text } ], conditionDueDate, comment, confirmation: true }`.
  - REJECTED: mind. ein Element `NOT_ACCEPTED` und Kommentar je solchem Element (`REJECTION_REASON_REQUIRED`).
  - CONDITIONALLY_APPROVED: ≥ 1 Auflage + Frist (`CONDITIONS_REQUIRED`).
  - APPROVED: kein Element `NOT_ACCEPTED` (`ELEMENTS_NOT_ACCEPTED`).
  - Frische Anmeldung (optional): `ppap.decision.max-auth-age-seconds` gesetzt → Token-Claim `auth_time` muss jünger sein, sonst `401 RECENT_AUTH_REQUIRED`; das UI meldet den Benutzer mit `max_age=0` erneut an und wiederholt die Aktion.
```json
DecisionDto = { "revision": 1, "result": "REJECTED", "conditions": [], "conditionDueDate": null, "comment": "…",
  "elementFindings": [ { "code": "PPF-11", "result": "NOT_ACCEPTED", "comment": "Merkmal 12 außerhalb Toleranz" } ],
  "decidedAt": "…", "decidedBy": "oem.quality", "payloadHash": "sha256:…", "signature": { … } }
```
- `GET …/submissions/{revision}/decision/payload` → kanonisches `PpapDecision` + JWS.

### Dokumente – `/documents`
- `POST /documents` (multipart `file`) → `DocumentDto { id, fileName, mediaType, size, sha256, uploadedAt, uploadedBy }`. Max. `PPAP_UPLOAD_MAX_SIZE` (Default 50 MB), Whitelist (PDF, PNG, JPEG, WEBP, XLSX, DOCX, CSV, TXT, ZIP) mit **Inhaltsprüfung** (Magic Bytes, nicht nur Endung/Content-Type) → `UNSUPPORTED_FILE_TYPE`, `FILE_TOO_LARGE`. Speicherung inhaltsadressiert `<tenant>/<sha256>` (gleiche Datei = gleiches Objekt), Port `MalwareScanner` (Default no-op, `TODO`).
- `GET /documents/{id}` (Metadaten), `GET /documents/{id}/content?disposition=inline|attachment` mit `X-Content-Type-Options: nosniff`, `Content-Security-Policy: sandbox`, `Content-Disposition` (RFC 6266-konformer Dateiname).
- Dokumente sind mandantenlokal; Freigabe an Partner erst in Phase 5 (Pull über EDC).

### Signaturschlüssel – öffentlich
- `GET /.well-known/jwks.json` → öffentlicher Mandantenschlüssel (EC P-256, `kid`). Später zusätzlich im DID-Dokument.
- Schlüsselquelle (Port `SigningKeyProvider`): OpenBao/Vault KV `secret/ppap-signing-key` (PEM, PKCS#8) – Default in Compose – oder Datei `PPAP_SIGNING_KEY_FILE` (Tests/Entwicklung).

## Seed: Vorgänge und Formblätter

- `seed/common/form-templates.json` – Formblatt-Vorlage(n); `seed/forms/*.xlsx` – leeres und ausgefülltes Beispiel-Formblatt.
- `seed/<teilnehmer>/approval-cases.json` – Vorgänge je Teilnehmer, **spiegelgleich** zwischen Kunde und Lieferant (gleiche `exchangeId`), erzeugt von `seed/generate_cases.py`:
  - aktive Vorgänge in frühen Status (REQUESTED, IN_PROGRESS mit teilbefülltem Entwurf, DRAFT beim Kunden),
  - abgeschlossene **Altvorgänge** (`source = LEGACY_IMPORT`, z. B. aus einem früheren Portal migriert): Status APPROVED/REJECTED/CONDITIONALLY_APPROVED mit Revision und Entscheidung, **ohne Signatur** (`signature.status = NOT_SIGNED`, im UI als „Altdaten“ gekennzeichnet). So zeigen Dashboard und Listen realistische Historie, ohne Signaturen zu fälschen.
  - Nachweisdokumente als Dateinamen aus `seed/documents/` (Beispiel-PDFs), werden beim Laden über den Dokumenten-Service hochgeladen.
- Eingereichte, signierte Revisionen entstehen ausschließlich live (Phase 3 lokal beim Lieferanten, ab Phase 4 über EDC beim Kunden).

### Seed-Format `approval-cases.json`

```json
{ "cases": [ {
    "exchangeId": "uuid (auf beiden Seiten gleich)", "caseNumber": "PPF-2026-00001", "ownRole": "CUSTOMER|SUPPLIER",
    "source": "LOCAL|MANUAL|EXCHANGE|LEGACY_IMPORT", "status": "REQUESTED",
    "partnerBpnl": "…", "partNumber": "eigene Sachnummer", "partnerPartNumber": "…",
    "reasonCode": "NEW_PART", "levelCode": "VDA-3", "dueDate": "+21d", "remarks": "…",
    "requestedAt": "-5d", "requestedBy": "oem.quality",
    "elements": [ { "code": "PPF-01", "requirement": "REQUIRED", "note": "" } ],
    "timeline": [ { "at": "-5d", "actor": "oem.quality", "from": "DRAFT", "to": "REQUESTED", "action": "SEND", "comment": "" } ],
    "submissions": [ { "revision": 1, "status": "DRAFT|SUBMITTED", "submittedAt": "-2d", "submittedBy": "tier1.quality",
        "elements": [ { "code": "PPF-11", "selfAssessment": "OK", "values": [ … ], "comment": "", "documents": ["messprotokoll-d10-04711.pdf"] } ],
        "decision": { "result": "APPROVED", "conditions": [ { "text": "…" } ], "conditionDueDate": "+30d", "comment": "",
                      "elementFindings": [ { "code": "PPF-11", "result": "ACCEPTED", "comment": "" } ], "decidedAt": "-1d", "decidedBy": "oem.quality" } } ]
} ] }
```
- Zeitangaben `±Nd` (Tage) werden beim Laden relativ zum aktuellen Datum aufgelöst; absolute ISO-Daten sind ebenfalls erlaubt.
- Snapshots der Elemente, Gründe und Stufen werden beim Laden aus dem (bereits geladenen) Katalog gebildet.
- `documents` verweisen auf Dateien in `PPAP_SEED_DOCUMENTS_PATH` (Compose: `seed/documents`), die über den Dokumenten-Service hochgeladen werden.
- Idempotent über (`tenant`, `exchangeId`, `ownRole`): vorhandene Vorgänge werden übersprungen.
- `SUBMITTED`-Revisionen im Seed sind nur bei `source = LEGACY_IMPORT` erlaubt (ohne Signatur); sonst wird der Vorgang abgelehnt (Seed-Fehler).
