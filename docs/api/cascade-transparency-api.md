# Vertrag Kaskade und Transparenz (Phase 6: UC-09 bis UC-12)

Stand: Phase 6. Ergänzt [approval-api.md](approval-api.md), [exchange-api.md](exchange-api.md), [pull-api.md](pull-api.md). REST verbindlich über `api/openapi.json`.

## 1. Kaskadierte Freigabe (UC-09)

**Kindvorgang anlegen.** Aus einem Vorgang, in dem der eigene Mandant **Lieferant** ist (Status REQUESTED oder IN_PROGRESS), legt der Qualitätsverantwortliche eine Unterlieferanten-Anforderung an:
`POST /api/v1/approval-cases/{parentId}/sub-requests` `{ partId (eigenes Unterteil mit Lieferanten-Zuordnung), relationId?, reasonCode, levelCode, dueDate, remarks, elements? }` → neuer Vorgang mit `ownRole = CUSTOMER`, `parentCaseId = parentId`, Status DRAFT (Senden wie gewohnt). Ein Kindvorgang verweist auf genau einen Elternvorgang, ein Elternvorgang kann mehrere Kinder haben (mehrere Unterteile). Benutzerrolle: `SUPPLIER_QUALITY_MANAGER` **und** `CUSTOMER_QUALITY_ENGINEER` (Tier-1-Benutzer haben beide; sonst 403).

**Referenz in der eigenen Einreichung.** Im Entwurf des Elternvorgangs:
- `GET /api/v1/approval-cases/{id}/sub-supplier-candidates` → entschiedene Kindvorgänge (`APPROVED` / `CONDITIONALLY_APPROVED`; `REJECTED` nur zur Information, nicht referenzierbar).
- `PUT /api/v1/approval-cases/{id}/submissions/draft/sub-supplier-references` `{ childCaseIds: [...] }` (If-Match) → speichert die Referenzen im Entwurf.
- Vollständigkeit: Hat der Elternvorgang Kindvorgänge, die noch nicht entschieden sind, meldet die Vollständigkeitsprüfung `SUB_SUPPLIER_PENDING` (Hinweis, **kein** Blocker – der Lieferant entscheidet, ob er ohne Unterlieferanten-Freigabe einreicht; `TODO(EG)`).

**Inhalt einer Referenz** (Teil von `PpapSubmission.subSupplierReferences`, SAMM-Modell erweitern; **keine** Dokumente, keine Prüfergebnisse):
```json
{ "subSupplierBpnl": "BPNL…", "subSupplierName": "Demo-Tier2 KG", "exchangeId": "uuid des Kindvorgangs", "caseNumber": "PPF-2026-00012",
  "partNumber": "Unterteil (Sachnummer des Tier-1)", "partName": "…", "supplierPartNumber": "Sachnummer beim Tier-2",
  "revision": 1, "decisionResult": "APPROVED|CONDITIONALLY_APPROVED", "decidedAt": "…", "decisionHash": "sha256:…", "conditionDueDate": null }
```
`decisionHash` = `payloadHash` der (vom Tier-2-Kunden = Tier-1 selbst signierten) Entscheidung; der OEM kann die Integrität der Aussage nur gegenüber Tier-1 prüfen (Signatur der Einreichung). Unterlieferanten-BPNL im Klartext (ASSUMPTIONS A-18, `TODO(EG)` Pseudonymisierung).

**Anzeige beim Kunden (OEM):** Prüfansicht und Revisionen zeigen die Referenzen (Unterlieferant, Unterteil, Ergebnis, Datum, Hash) mit dem Hinweis, dass Unterlieferanten-Dokumente nicht weitergegeben werden.
**Anzeige beim Tier-1:** Vorgangsdetail zeigt Eltern-/Kindbeziehung (Link in beide Richtungen, Status der Kinder); Dashboard „blockierende Kindvorgänge“ (Elternvorgang offen, Kind nicht entschieden).
Felder im Vorgang: `parentCase: { id, caseNumber, partner } | null`, `childCases: [{ id, caseNumber, partner, partNumber, status, lastDecisionResult }]`.

## 2. Connector-Cockpit (UC-10) – Rolle `CONNECTOR_ADMIN`

Lesend über den Port `DataspaceConnector` (Management API des eigenen EDC), keine Schreibzugriffe außer über Provisionierung/Policies:
- `GET /api/v1/connector/overview` → `{ edc: { status, version?, participantId, dspEndpoint }, dtr: { status, url } | null, provisioning: { ok, lastRun, error }, outbox: { pending, failed }, inbox: { rejected }, counts: { assets, policies, contractDefinitions, negotiations, transfers } }`.
- `GET /api/v1/connector/assets`, `/policies`, `/contract-definitions` → Listen (id, Typ/Eigenschaften, verwaltet vom Connector ja/nein, Policy-Inhalt als JSON).
- `GET /api/v1/connector/negotiations?state=&page=`, `GET /api/v1/connector/transfers?state=&page=` → Listen mit Partner (DID/BPNL, Name aus Stammdaten), Asset, Zustand, Zeit.
- `POST /api/v1/connector/catalog` `{ partnerBpnl }` → **live** per DSP abgerufener Katalog des Partners (Datasets mit Asset-ID, `dct:type`, Angebots-Policies) + Dauer; Fehler mit EDC-Meldung.
- Live-Log: SSE-Ereignis `connector.log { at, level, correlationId, messageType?, step?, text }` für alle Austauschschritte (aus `ExchangeRecord`-Schritten abgeleitet, nicht aus Server-Logs).
- Bestehend: Nachrichten, Retry, Twins.

## 3. Policy-Profile je Beziehung (UC-10, E5-04)

`PolicyProfile` (Modul `masterdata`, Daten statt Code; Seed: `default`):
```json
{ "id": "uuid", "code": "default", "name": "Standard (Catena-X)", "description": "…",
  "access": { "type": "BPN_GROUP | BPN", "membershipRequired": true },
  "usage": { "frameworkAgreement": "DataExchangeGovernance:1.0", "usagePurposes": ["cx.quality.base:1"], "additionalConstraints": [] },
  "active": true, "version": 1 }
```
- CRUD `/api/v1/policy-profiles` (`CONNECTOR_ADMIN`), Validierung (UsagePurpose-Format, FrameworkAgreement nicht leer).
- Beziehung `policyProfile` = Code (bisher Freitext „default“) mit Fremdschlüssel; Änderung an Profil oder Zuordnung löst Reconciliation aus: Partnerspezifische Assets (`ppap-submission-<BPNL>`, `ppap-documents-<BPNL>`) bekommen die Usage Policy des Profils dieser Beziehung; gemeinsame Assets (Notification, Public Keys, DTR) die des Profils `default`. Policy-IDs mit Inhalts-Hash (wie Phase 4), Contract Definitions werden umgehängt.
- `GET /api/v1/policy-profiles/{id}/preview` → die daraus erzeugten ODRL-Policies (JSON-LD) zur Anzeige.

## 4. Dashboard und Audit (UC-11)

- `GET /api/v1/approval-cases/stats` ergänzt: `averageLeadTimeDays` (Anforderung → letzte Entscheidung, abgeschlossene Vorgänge der letzten 12 Monate), `approvalRate` (Anteil APPROVED + CONDITIONALLY_APPROVED an entschiedenen Revisionen), `blockingChildren` (Anzahl Elternvorgänge mit offenen Kindern), `byPartner[].exchangeHealth: OK|DEGRADED|FAILING` (letzte 24 h: fehlgeschlagene/ausstehende Nachrichten), `byPartner[].lastExchangeAt`.
- `GET /api/v1/activity?limit=30` → Aktivitätsfeed aus Timeline-Einträgen aller Vorgänge (`{ at, caseId, caseNumber, action, actor, actorDisplay, partner }`), `actorDisplay` = Benutzername bzw. **Partnername** statt `partner:BPNL…` (gilt auch für die Vorgangs-Timeline: neues Feld `actorDisplay`).
- **Audit-Export** je Vorgang: `GET /api/v1/approval-cases/{id}/audit-export` → JSON `{ case, timeline, submissions (Hash, Signatur), decisions, exchangeRecords (mit Schritten), auditEvents (mit Hash-Kette), exportedAt, exportedBy }`, signiert als JWS (Mandantenschlüssel, Header `X-Signature`), Dateiname `audit-<caseNumber>.json`. PDF folgt später.
- **Bediente Abrufe** (Submodel, Dokument, Public Keys) werden als Audit-Ereignis `SERVED_TO_PARTNER` am Vorgang/Dokument protokolliert (Backlog Phase 5).

## 5. Nutzung messen (UC-12)

- Tabelle `usage_counter` (tenant, month `YYYY-MM`, metric, count), ereignisbasiert fortgeschrieben (`SubmissionSubmitted` beim Lieferanten → `APPROVAL_CASES_SUBMITTED` bei Revision 1, `REVISIONS_SUBMITTED` bei Revision > 1; eingegangene Revision beim Kunden → `APPROVAL_CASES_RECEIVED` / `REVISIONS_RECEIVED`), idempotent je (exchangeId, revision, metric).
- **Reproduzierbar:** `POST /api/v1/usage/recompute` (`CONNECTOR_ADMIN`) berechnet die Zähler aus den Rohdaten (Revisionen) neu; Test: Ereignis-Zähler == Neuberechnung.
- `GET /api/v1/usage?from=YYYY-MM&to=YYYY-MM` → `[{ month, approvalCases (= submitted + received Revision 1), revisions, details: {metric: count} }]`; `GET /api/v1/usage/export.csv`. Rolle `CONNECTOR_ADMIN`. Keine Abrechnungslogik. Altvorgänge (`LEGACY_IMPORT`) zählen nicht.

## 6. Datenraum-Ansicht (Demo-Modus, E5-06)

- Route `/dataspace` in jeder Instanz: **Perspektive des eigenen Teilnehmers** – eigener Connector in der Mitte, Partner mit ihren Connectors (aus Stammdaten, Catena-X-fähig) außen; Kanten = Beziehungen (Rolle Kunde/Lieferant); Core-Services (Identität) angedeutet.
- **Animiert aus echten Daten:** Jedes `exchange.changed`-Ereignis (SSE) und die letzten Austauschnachrichten (`GET /exchange/messages` bzw. ein schlanker `GET /api/v1/dataspace/recent?limit=50` für alle Rollen) erzeugen eine Animation entlang der Kante (Richtung, Nachrichtentyp farbcodiert: Anforderung, Statusmeldung, PPF-Paket/Bereitstellung, Abruf, Entscheidung), mit Schritt-Tooltip (Katalog → Vertrag → EDR → Zustellung).
- Vollbild-/Präsentationsmodus (große Schrift, ohne Navigation), Wiedergabe der letzten 15 Minuten im Zeitraffer („Replay“).
- Für Messen wird die Ansicht des **Tier-1** genutzt: Er ist Kunde des Tier-2 und Lieferant des OEM und sieht daher die gesamte Kette. Keine mandantenübergreifende Sammelansicht (Datensouveränität).
- `GET /api/v1/dataspace/topology` → `{ self: { bpnl, name, roleLabel, connector: { participantId, status } }, partners: [{ bpnl, name, roles, exchangeReady, relation: { partnerRole, exchangeMode, policyProfile } }] }` (alle angemeldeten Rollen).

## 7. Weitere Backlog-Punkte in dieser Phase
- Zuordnen aus der Klärungsliste: Option `replaceExistingMapping` (ersetzt die bestehende Teilezuordnung dieses Teils in der Beziehung; Audit).
- Erneute Signaturprüfung von `UNVERIFIED`-Revisionen, sobald der Partnerschlüssel abgerufen werden konnte (bei jedem erfolgreichen Schlüsselabruf und per `POST /api/v1/approval-cases/{id}/submissions/{revision}/verify-signature`).
