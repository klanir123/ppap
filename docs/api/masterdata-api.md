# API-Vertrag Stammdaten (UC-01, UC-02) – `/api/v1`

Stand: Phase 2. Verbindliche Quelle ist die generierte OpenAPI-Spezifikation `api/openapi.json`; dieses Dokument beschreibt Absicht, Konventionen und den Import-Ablauf für Frontend und ERP-/QM-Integratoren.

## Konventionen

| Thema | Regel |
|---|---|
| Authentifizierung | Bearer-JWT (Keycloak des Teilnehmers). UI: Authorization Code + PKCE; Systeme: Client Credentials (`ppap-integration`). |
| Rollen | **Lesen**: jede angemeldete Rolle. **Schreiben**: `MASTERDATA_MANAGER` oder `SYSTEM_INTEGRATION`. Partner zusätzlich `CONNECTOR_ADMIN` (DSP-Endpoint/DID). |
| Mandant | Implizit aus der Instanz (`ppap.tenant.bpnl`); jede Tabelle trägt `tenant_id`. Nie im Request. |
| IDs | Technische `id` (UUID) + fachlicher Schlüssel (Teil: `partNumber`, Partner: `bpnl`, Standort: `bpns`, Katalog: `code`). Upsert immer über den fachlichen Schlüssel. |
| Versionierung | Jede Ressource hat `version` (Long). Antworten liefern `ETag: "<version>"`. `PUT`/`DELETE` akzeptieren `If-Match`; bei Abweichung `412 Precondition Failed`. Ohne `If-Match` → last-write-wins (für Integrationen). |
| Listen | `GET ...?q=<Volltext>&active=true|false&page=0&size=25&sort=field,asc` → `Page<T>` `{ content, page, size, totalElements, totalPages }`. `size` max. 200. |
| Löschen | `DELETE` = **Deaktivieren** (`active=false`), physisch nur, wenn nie referenziert. Antwort `204`. |
| Fehler | RFC 9457 Problem Details mit `code`, `correlationId` und bei Validierung `errors: [{ field, code, message }]`. Meldungen sind englisch; das UI übersetzt über `code` (Liste siehe unten). |
| Audit | Jede schreibende Operation erzeugt einen Audit-Eintrag (append-only, Hash-Kette). |
| Zeit | ISO 8601 UTC (`createdAt`, `updatedAt`). |

### Fehlercodes

| HTTP | `code` | Bedeutung |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Feldvalidierung fehlgeschlagen (`errors[]` mit Feld-Codes wie `REQUIRED`, `INVALID_FORMAT`, `TOO_LONG`) |
| 400 | `MALFORMED_REQUEST` | Body nicht lesbar oder ungültiger Enum-Wert |
| 400 | `SELF_REFERENCE` | eigener Mandant als Partner/Beziehung |
| 401 / 403 | `UNAUTHORIZED` / `FORBIDDEN` | nicht angemeldet / Rolle fehlt |
| 404 | `NOT_FOUND` | Ressource existiert nicht (im eigenen Mandanten) |
| 409 | `DUPLICATE_KEY` | fachlicher Schlüssel existiert bereits |
| 409 | `REFERENCED` | physisches Löschen nicht möglich, Objekt wird referenziert |
| 412 | `VERSION_CONFLICT` | `If-Match` passt nicht zur aktuellen Version |
| 413 / 422 | `FILE_TOO_LARGE`, `TOO_MANY_ROWS`, `UNSUPPORTED_FILE_TYPE`, `EMPTY_FILE`, `UNREADABLE_FILE`, `IMPORT_FAILED` | Import-Fehler |

### Validierungsregeln (gemeinsam für UI, API, Import)

| Feld | Regel |
|---|---|
| `bpnl` | `^BPNL[0-9A-Z]{12}$` |
| `bpns` | `^BPNS[0-9A-Z]{12}$` |
| `partNumber`, `partnerPartNumber`, `drawingNumber` | 1–64 Zeichen, `^[A-Za-z0-9._\- /]+$` |
| `dspEndpoint` | absolute `http(s)`-URL, optional |
| `did` | `^did:[a-z0-9]+:.+$`, optional |
| `country` | ISO 3166-1 alpha-2 |
| Katalog-`code` | `^[A-Z0-9][A-Z0-9_\-]{1,31}$` |
| Pflichtfelder | siehe Ressourcen (✱) |

## Ressourcen

Hinweis: Fachliche Schlüssel mit `/` (z. B. Sachnummern) werden in Upsert-Pfaden URL-kodiert (`%2F`) übergeben.

### Teile – `/parts`
Eigene Teile des Mandanten (für den OEM = Kundensachnummern, für Lieferanten = eigene Sachnummern).

```json
{ "id": "uuid", "partNumber": "✱", "nameDe": "✱", "nameEn": "", "drawingNumber": "", "drawingRevision": "",
  "material": "", "catenaXId": "urn:uuid:… (optional)", "active": true, "version": 3,
  "createdAt": "…", "updatedAt": "…" }
```
`GET /parts`, `GET /parts/{id}`, `POST /parts` (201, `Location`), `PUT /parts/{id}`, `PUT /parts/by-number/{partNumber}` (**Upsert**, 200/201), `DELETE /parts/{id}`.
Filter zusätzlich: `partnerBpnl` (nur Teile mit Mapping zu diesem Partner).

### Partner – `/partners`
```json
{ "id": "uuid", "bpnl": "✱", "name": "✱", "roles": ["CUSTOMER","SUPPLIER"] , "dspEndpoint": "http://…/api/v1/dsp/2025-1",
  "did": "did:web:…", "contactEmail": "qs-ppap@…example", "active": true, "version": 1, "createdAt": "…", "updatedAt": "…",
  "exchangeReady": true }
```
`roles` = Rolle **des Partners** aus Sicht des eigenen Mandanten (mind. eine). `exchangeReady` (nur lesend) = `dspEndpoint` und `did` gesetzt und aktiv (AK-01-5). Der eigene Mandant darf nicht als Partner angelegt werden (`code: SELF_REFERENCE`).
`GET/POST /partners`, `GET/PUT/DELETE /partners/{id}`, `PUT /partners/by-bpnl/{bpnl}` (Upsert).

### Standorte – `/sites`
```json
{ "id": "uuid", "bpns": "✱", "name": "✱", "ownerBpnl": "✱ (eigener Mandant oder Partner)", "ownerName": "(lesend)",
  "street": "", "postalCode": "", "city": "", "country": "DE", "active": true, "version": 1 }
```
`GET/POST /sites` (Filter `ownerBpnl`), `GET/PUT/DELETE /sites/{id}`, `PUT /sites/by-bpns/{bpns}` (Upsert).

### Kunden-Lieferanten-Beziehungen – `/relations`
Immer aus Sicht des eigenen Mandanten: eine Beziehung zu genau einem Partner, in dem dieser `CUSTOMER` (wir liefern) oder `SUPPLIER` (wir beziehen) ist. Zu einem Partner kann es je Rolle höchstens eine Beziehung geben.
```json
{ "id": "uuid", "partnerBpnl": "✱", "partnerName": "(lesend)", "partnerRole": "CUSTOMER|SUPPLIER ✱",
  "exchangeMode": "PUSH | PULL | PUSH_NOTIFY_PULL_PAYLOAD (Default)", "policyProfile": "default (Phase 6)",
  "active": true, "version": 1, "mappingCount": 12 }
```
`GET/POST /relations` (Filter `partnerBpnl`, `partnerRole`), `GET/PUT/DELETE /relations/{id}`.

#### Teilezuordnungen – `/relations/{relationId}/part-mappings`
```json
{ "id": "uuid", "partId": "uuid ✱ (oder partNumber beim Anlegen)", "partNumber": "(lesend)", "partNameDe": "(lesend)",
  "partnerPartNumber": "✱", "ownSiteBpns": "", "partnerSiteBpns": "", "version": 1 }
```
Eindeutig: (`relation`, `partId`) und (`relation`, `partnerPartNumber`). `GET` (Page), `POST`, `PUT /{id}`, `DELETE /{id}` (physisch).
Lookup für UC-04: `GET /part-mappings/resolve?partnerBpnl=…&partnerRole=CUSTOMER&partnerPartNumber=…` → Mapping oder `404`.

### Vorlageelement-Katalog (UC-02)

#### Elemente – `/catalog/elements`
```json
{ "id": "uuid", "code": "✱ VDA-PPF-09", "nameDe": "✱", "nameEn": "✱", "descriptionDe": "", "descriptionEn": "",
  "referenceVda": "VDA 2, Kap. …", "referenceAiag": "AIAG PPAP #9", "evidenceType": "DOCUMENT|VALUES|DOCUMENT_AND_VALUES ✱",
  "valueSchema": { "JSON Schema (optional) für strukturierte Werte" }, "sortOrder": 90, "active": true, "version": 1 }
```
`GET` (Liste, nicht paginiert, sortiert nach `sortOrder`), `POST`, `PUT /{id}`, `PUT /by-code/{code}`, `DELETE /{id}` (Deaktivieren; aktive Stufen-Zuordnungen bleiben erhalten). `valueSchema` muss gültiges JSON Schema (Draft 2020-12) sein.

#### Vorlagestufen – `/catalog/levels`
```json
{ "id": "uuid", "code": "✱ VDA-3", "nameDe": "✱", "nameEn": "✱", "descriptionDe": "", "descriptionEn": "", "scheme": "VDA|AIAG|CUSTOM",
  "sortOrder": 30, "active": true, "version": 1,
  "requirements": { "VDA-PPF-01": "REQUIRED", "VDA-PPF-09": "ON_REQUEST" } }
```
`requirements`: Element-Code → `REQUIRED | ON_REQUEST | NOT_REQUIRED`; fehlender Code = `NOT_REQUIRED`. `PUT /catalog/levels/{id}` ersetzt die Matrix vollständig. Unbekannte Codes → `400`.

#### Vorlagegründe – `/catalog/reasons`
```json
{ "id": "uuid", "code": "✱ NEW_PART", "nameDe": "✱", "nameEn": "✱", "descriptionDe": "", "descriptionEn": "", "sortOrder": 10, "active": true, "version": 1 }
```

#### Katalog-Export/-Import – `/catalog/export`, `/catalog/import`
`GET /catalog/export` → `{ "elements": [...], "levels": [...], "reasons": [...] }` (gleiches Format wie `seed/common/catalog.json`).
`POST /catalog/import` (JSON gleichen Formats) → Upsert über `code`, Ergebnis wie Import-Bericht unten.

## Audit-Trail – `/audit-events`

`GET /audit-events?objectType=PART&objectId=<uuid>&page=0&size=25` (jede angemeldete Rolle) → `Page<AuditEventDto>`:
```json
{ "id": "uuid", "occurredAt": "…", "actor": "oem.masterdata | service-account-ppap-integration | system:seed",
  "action": "CREATED|UPDATED|DEACTIVATED|DELETED|IMPORTED", "objectType": "PART", "objectId": "uuid",
  "businessKey": "D10-04711-01", "details": { }, "prevHash": "…", "hash": "…" }
```
Einträge sind append-only (DB-Trigger) und je Objekt per SHA-256 verkettet (`hash = SHA-256(kanonisches JSON des Eintrags + prevHash)`), sodass nachträgliche Änderungen erkennbar sind (UC-11).

## Import CSV/XLSX (UC-01 A1)

Zwei Schritte, zustandslos (die Datei wird bei „Ausführen“ erneut gesendet):

1. `POST /imports/{entity}/preview` (multipart: `file`) – `entity` ∈ `parts | partners | sites | relations | part-mappings`
   ```json
   { "entity": "parts", "fileName": "teile.xlsx", "sheet": "Teile", "delimiter": ";", "columns": ["Sachnummer","Bezeichnung",…],
     "suggestedMapping": { "Sachnummer": "partNumber", "Bezeichnung": "nameDe", "Unbekannt": null },
     "targetFields": [{ "name": "partNumber", "required": true, "labelKey": "masterdata.parts.partNumber" }, …],
     "rows": [{ "rowNumber": 2, "values": { "partNumber": "…", … }, "status": "OK|WARNING|ERROR", "action": "CREATE|UPDATE|UNCHANGED",
                "messages": [{ "field": "bpnl", "code": "INVALID_FORMAT", "message": "…" }] }],
     "summary": { "total": 48, "ok": 45, "warnings": 2, "errors": 1, "create": 40, "update": 5, "unchanged": 0 } }
   ```
   Vorschau zeigt max. 500 Zeilen vollständig; `summary` zählt alle. Die Validierung nutzt die `suggestedMapping` bzw. ein mitgesendetes `mapping` (optionaler Multipart-Teil `mapping`, JSON `{ Spalte: Feld|null }`).
2. `POST /imports/{entity}/execute` (multipart: `file`, `mapping`, optional `skipErrors=true`) →
   ```json
   { "entity": "parts", "created": 40, "updated": 5, "unchanged": 0, "failed": 1,
     "failures": [{ "rowNumber": 17, "messages": [ … ] }] }
   ```
   Upsert über fachlichen Schlüssel, idempotent (zweimal ausführen → `unchanged`). Ohne `skipErrors` wird bei Fehlern nichts importiert (`422`, gleicher Bericht). Fehlerbericht als CSV: `POST /imports/{entity}/error-report` (gleiche Parameter) → `text/csv`.

**Formate:** CSV (UTF-8, optional BOM; Trennzeichen `;` oder `,` automatisch erkannt; erste Zeile = Spaltenköpfe) und XLSX (erstes Blatt bzw. Blatt mit Namen der Entität). Max. 10 MB / 10.000 Zeilen. **Auto-Mapping:** Spaltenköpfe werden (case-insensitiv, ohne Leer-/Sonderzeichen) gegen Feldnamen und deutsche/englische Synonyme abgeglichen (z. B. `Sachnummer`, `Teilenummer`, `Part Number` → `partNumber`; `BPN`, `BPNL` → `bpnl`). Die Synonymliste ist Konfiguration (`masterdata-import-synonyms.yaml`), nicht Code.

**Felder je Entität** (= Spaltennamen der Seed-Dateien):

| Entität | Felder (✱ Pflicht) | Schlüssel |
|---|---|---|
| `parts` | partNumber✱, nameDe✱, nameEn, drawingNumber, drawingRevision, material, catenaXId | partNumber |
| `partners` | bpnl✱, name✱, roles✱ (`CUSTOMER`, `SUPPLIER` oder `CUSTOMER\|SUPPLIER`), dspEndpoint, did, contactEmail | bpnl |
| `sites` | bpns✱, name✱, ownerBpnl✱, street, postalCode, city, country | bpns |
| `relations` | partnerBpnl✱, partnerRole✱, exchangeMode | partnerBpnl + partnerRole |
| `part-mappings` | partnerBpnl✱, partnerRole✱, partNumber✱, partnerPartNumber✱, ownSiteBpns, partnerSiteBpns | Beziehung + partNumber |

## Seed-Daten

```
seed/
  common/catalog.json              Elementkatalog, Stufen, Gründe (alle Teilnehmer)
  <teilnehmer>/partners.csv        ; getrennt, UTF-8, Spalten wie oben
  <teilnehmer>/sites.csv
  <teilnehmer>/parts.csv
  <teilnehmer>/relations.csv
  <teilnehmer>/part-mappings.csv
```
Laden beim Start, wenn `PPAP_SEED_ENABLED=true` und `PPAP_SEED_PATH` gesetzt ist – **über dieselben Import-Services** (Upsert, idempotent; kein Spezialcode). Reihenfolge: Katalog → Partner → Standorte → Teile → Beziehungen → Teilezuordnungen. Ergebnis wird geloggt und als Audit-Ereignis (`actor = system:seed`) protokolliert. In Compose wird `seed/<teilnehmer>` und `seed/common` read-only in den Container gemountet; `make demo` setzt die DB zurück, beim Neustart wird erneut geseedet.
