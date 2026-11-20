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

## Korrekturen und Klarstellungen Phase 6 (Backend, verifiziert)
1. *[Korrektur Phase 6]* **Kindvorgang anlegen:** `POST /approval-cases/{parentId}/sub-requests` → `201` mit dem Vorgangs-Detail (Body wie `POST /approval-cases`). Technische Clients mit `SYSTEM_INTEGRATION` dürfen ebenfalls (wie bei allen übrigen Aktionen der Zustandsmaschine). Der Elternvorgang protokolliert die neue Aktion `REQUEST_SUB_SUPPLIER` (Kommentar = Vorgangsnummer des Kindes, keine Statusänderung) und bietet sie in `allowedActions` nur Benutzern mit beiden Rollen an. Falsche Mandantenrolle des Elternvorgangs → `409 WRONG_CASE_ROLE`, falscher Status → `409 INVALID_STATE_TRANSITION`.
2. *[Korrektur Phase 6]* **Referenzen:** `PUT …/sub-supplier-references` liefert die Entwurfs-Revision (`SubmissionDto` mit `subSupplierReferences`, ETag). Ungültige Kind-IDs (fremd, nicht entschieden, abgelehnt) → `422 SUB_SUPPLIER_REFERENCE_INVALID` mit `errors[].field = childCaseIds[i]`; doppelte IDs werden zusammengefasst. Die Referenzdaten stammen aus der letzten Entscheidung des Kindes; das DTO enthält zusätzlich `childCaseId` (nur beim Lieferanten, beim Kunden `null`). Eine neue Revision übernimmt die Referenzen der vorigen.
3. *[Ergänzung Phase 6]* **Vollständigkeit:** `completeness.hints[]` (gleiche Struktur wie `issues`, `elementCode = null`) mit `SUB_SUPPLIER_PENDING` je nicht entschiedenem Kind (Status `DRAFT`…`IN_REVIEW`); Hinweise ändern `complete` nie.
4. *[Ergänzung Phase 6]* **Keine Unterlieferanten-Dokumente (AK-09-2) – Herkunft, nicht Inhalt:** Ein von einem Partner *empfangener* Dokument-Datensatz (Dokumenttransfer, Uploader `partner:<BPNL>`) kann weder an eine eigene Revision noch als Anforderungs-Anhang gehängt werden (`422 SUB_SUPPLIER_DOCUMENT_NOT_SHAREABLE`) und wird nie ausgeliefert; ausgeliefert wird je Datensatz nur ein eigener Upload, der an eine für diesen Partner eingereichte Revision gehängt ist. Ein eigener Upload mit identischen Bytes (gleiches Speicherobjekt, eigener Datensatz) ist erlaubt (bewusste Benutzerhandlung). Live geprüft: Tier-1 hängt eine byte-gleiche eigene Kopie an, der OEM erhält sie, auf dem empfangenen Tier-2-Datensatz gibt es kein `SERVED_TO_PARTNER`.
5. *[Klarstellung Phase 6]* **Kaskade im Vorgang:** Liste `ApprovalCaseDto.parentCaseId`; Detail `parentCase`/`childCases` wie oben. `stats.blockingChildren` zählt offene Vorgänge mit mindestens einem nicht entschiedenen Kind.
6. *[Korrektur Phase 6]* **Connector-Übersicht:** `outbox`/`inbox` als Objekte (`{pending, failed}`, `{rejected}`, wie `/exchange/status`), `edc.version` ist `null` (die Management API liefert keine Version), `counts` ist `null` bei nicht erreichbarem EDC, einzelne Werte `-1` bei fehlgeschlagener Abfrage; `provisioning.usagePolicies` = Usage-Policy-ID je angebotenem Asset. Listen `negotiations`/`transfers`: 25 je Seite, Partner aus der DID (`did:web:<host>:<BPNL>`) + Stammdaten. Katalogfehler → `422 CATALOG_REQUEST_FAILED` (EDC-Meldung in `detail`), Partner ohne DSP-Endpunkt/DID → `422 PARTNER_NOT_EXCHANGE_READY`.
7. *[Ergänzung Phase 6]* **`connector.log`** enthält zusätzlich `status`, `direction`, `partnerBpnl`, `caseId`, `messageId`; `level` = `INFO` (OK), `DEBUG` (SKIPPED), `ERROR` (FAILED).
8. *[Korrektur Phase 6]* **Policy-Profile:** Das Profil `default` wird von der Flyway-Migration V9 je Mandant angelegt (Werte: Gruppe `ppap-partners`, Membership, `DataExchangeGovernance:1.0`, UsagePurpose aus `PPAP_EDC_POLICY_USAGE_PURPOSE` zum Zeitpunkt der Migration) – danach nur noch per API gepflegt; es kann nicht gelöscht, deaktiviert oder umbenannt werden (`409 DEFAULT_PROFILE_PROTECTED`). Lesen: alle angemeldeten Rollen (Auswahl im Beziehungsformular); Schreiben: `CONNECTOR_ADMIN`. Validierung: `UsagePurpose`/`FrameworkAgreement` im Format `<name>:<version>`, mindestens ein Zweck, Zusatz-Constraints mit ODRL-Operator, nicht `FrameworkAgreement`/`UsagePurpose`. Ein von Beziehungen verwendetes Profil wird beim Löschen deaktiviert und kann seinen Code nicht ändern (`409 REFERENCED`); unbekannte/inaktive Profile in der Beziehung → `400 UNKNOWN_REFERENCE` (`policyProfile`). Wirkung auf die Angebote: siehe ADR-0001 „Umsetzung (Phase 6)“ (partnerspezifische Assets immer mit `BusinessPartnerNumber`, Membership als Access-Constraint, live verifiziert: Profiländerung beim Tier-1 nach < 1 s im Katalog des OEM sichtbar). Vorschau: `{ profileId, code, version, relations, policies: [{ role: USAGE|ACCESS_PARTNER|ACCESS_SHARED, policyId, policy }] }` (`ACCESS_SHARED` nur für `default`, Partner-BPNL als Platzhalter `<BPNL>`).
9. *[Korrektur Phase 6]* **Kennzahlen:** `averageLeadTimeDays` = Anforderung → letzte Entscheidung der Vorgänge im Status `APPROVED`/`CONDITIONALLY_APPROVED` mit Entscheidung in den letzten 365 Tagen; `exchangeHealth`: `FAILING` bei fehlgeschlagenen oder abgelehnten Nachrichten der letzten 24 h, `DEGRADED` bei Wiederholungen oder seit > 5 min ausstehenden Nachrichten, sonst `OK`.
10. *[Korrektur Phase 6]* **Audit-Export:** Body = RFC-8785-kanonisches JSON (exakt die signierten Bytes) mit `format = msg-ppap-audit-export/1`, `tenant`, `auditChainValid`; `X-Signature` = detached JWS (ES256, `b64:false`, Mandantenschlüssel, prüfbar mit `/.well-known/jwks.json`), zusätzlich `X-Payload-Hash`. `auditEvents` = Hash-Kette des Objekts `APPROVAL_CASE/<id>` (inkl. `SERVED_TO_PARTNER` für Submodels).
11. *[Korrektur Phase 6]* **`SERVED_TO_PARTNER`:** Submodel → Objekt `APPROVAL_CASE` (Details: `exchangeId`, `revision`, `payloadHash`), Dokument → `DOCUMENT` (`sha256`, `size`), Public Keys → Objekttyp `PUBLIC_KEYS`, Objekt-ID = eigene BPNL (`kids`); Akteur `partner:<Edc-Bpn>`, `agreementId` aus `Edc-Contract-Agreement-Id`.
12. *[Korrektur Phase 6]* **Nutzung:** Monat einer Revision = `submittedAt` (UTC, Teil des signierten Payloads) auf **beiden** Seiten – so ist der ereignisbasierte Zähler exakt aus den Rohdaten reproduzierbar (beim Kunden zählt die Revision beim Eingang, im Monat ihrer Einreichung). `POST /usage/recompute` → `{ recomputedAt, events, consistent, differences: [{ month, metric, before, after }] }`; CSV mit Kopfzeile `month;approvalCases;revisions;<Metriken>`. Zählung asynchron nach Commit (Spring Modulith Event Publication Registry), idempotent über `usage_event (tenant, exchangeId, revision, metric)`.
13. *[Ergänzung Phase 6]* **Topologie:** Partner zusätzlich mit `did` und `relations[]` (alle Beziehungen; `relation` = Kunden-Beziehung zuerst); Connector-Status 15 s gecacht. `GET /dataspace/recent` liefert `{ id, at, direction, messageType, partner, status, caseId, caseNumber, completedAt, steps: [{ step, status, at, durationMs }] }`.
14. *[Klarstellung Phase 6]* **Signaturprüfung:** `POST …/submissions/{revision}/verify-signature` prüft erneut und holt bei `UNVERIFIED` vorher die Partnerschlüssel per EDC; nach jedem erfolgreichen Schlüsselabruf werden `UNVERIFIED`-Revisionen des Partners asynchron (nach Commit) neu geprüft (Timeline-Aktion `VERIFY_SIGNATURE`, keine Statusänderung). Entscheidungen werden nicht neu bewertet (Tabelle `ap_decision` ist vollständig unveränderlich).
15. *[Klarstellung Phase 6]* **assign-part:** `replaceExistingMapping = true` ersetzt die Kundensachnummer der bestehenden Zuordnung dieses Teils in der Beziehung (Audit `UPDATED` am Objekt `PART_MAPPING`); ohne die Option → `409 DUPLICATE_KEY`.

## Ergänzungen (Abstimmung Frontend, Phase 6)
1. Audit-Objekttyp `POLICY_PROFILE` (Anlegen, Ändern, Deaktivieren).
2. Schreibantworten für Policy-Profile und Beziehungen (bei Änderung von `policyProfile` oder `exchangeMode`) enthalten `reconciliation: { status: APPLIED|PENDING|FAILED, policyIds, contractDefinitionIds, error? }` (synchron mit kurzem Timeout, sonst `PENDING` und asynchron fertig).
3. Die `id` in `GET /dataspace/recent` und die `messageId` in den SSE-Ereignissen `connector.log` / `exchange.changed` sind identisch (messageId des ExchangeRecords).
