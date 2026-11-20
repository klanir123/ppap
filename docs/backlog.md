# Backlog (priorisiert)

Stand: Phase 0. Priorität: **M** = Must (Prototyp/Demo), **S** = Should (MVP), **C** = Could (später).
Storypunkte sind grobe Schätzungen (Fibonacci) zur Planung, nicht verbindlich.

## Epic E0 – Fundament (Phase 1) ✅ abgeschlossen 18.11.2026

Offen aus E0 (verschoben): E0-10 Korrelations-ID über EDC-Transfers hinweg → Phase 4 (im Backend vorhanden, Weitergabe an EDC folgt mit dem Austausch).

| ID | Story | Prio | SP |
|---|---|---|---|
| E0-01 | Maven-Multimodul-Projekt (Java 25, Spring Boot 4.x, Spring Modulith) mit Modul-Skelett `masterdata, approval, exchange, twin, semantics, documents, audit, identity, usage` + `platform` (Querschnitt) | M | 5 |
| E0-02 | ArchUnit/Modulith-Verifikationstest für Modulgrenzen und Hexagon-Regeln (domain kennt keine Adapter/Spring) | M | 3 |
| E0-03 | Docker Compose: Postgres, Keycloak, SeaweedFS, OpenBao/Vault, Wallet-Stub, BDRS, je Teilnehmer EDC CP/DP; Profil `tier2` | M | 13 |
| E0-04 | EDC-Bootstrap: Schlüssel/Secrets in Vault, DID/Credentials im Wallet-Stub, BPN-DID-Einträge im BDRS, Health-Wait-Skript | M | 8 |
| E0-05 | `Makefile` mit `up`, `down`, `demo` (Reset + Seed), `logs`, `test`; RAM-Messung dokumentieren | M | 3 |
| E0-06 | Keycloak-Realm-Import je Teilnehmer, Rollen, Demo-Benutzer, technischer Client | M | 3 |
| E0-07 | Frontend-Gerüst: Vite + React + TS, Router, Query, Tailwind/shadcn, i18n DE/EN, Light/Dark, OIDC-Login (PKCE), App-Shell mit Teilnehmer-Kontext (BPNL, Name, Rolle) | M | 8 |
| E0-08 | OpenAPI-Pipeline: springdoc → `openapi.json` als Build-Artefakt → orval-Client im Frontend; Drift-Check in CI | M | 3 |
| E0-09 | CI (GitHub Actions): Build, Unit/IT-Tests, Lint (Spotless, ESLint), OWASP Dependency-Check/Trivy, Lizenzprüfung | M | 5 |
| E0-10 | Strukturierte JSON-Logs, Korrelations-ID (`traceparent`/`X-Correlation-Id`) über Backend → EDC-Calls → Notifications | S | 3 |
| E0-11 | ~~Analyse Customs-Demonstrator~~ – erledigt in Phase 0, siehe `docs/analysis/customs-demonstrator.md` | – | 0 |

## Epic E1 – Stammdaten (Phase 2, UC-01/02) ✅ abgeschlossen 18.11.2026

E1-07 (Katalog-Export/-Import) ebenfalls umgesetzt. Zusätzlich: Audit-Modul mit Hash-Kette (Teil von E2-09) und Audit-Panel in der UI.

| ID | Story | Prio | SP |
|---|---|---|---|
| E1-01 | Domäne + Persistenz Teil, Partner, Standort, Beziehung, PartMapping (Flyway) | M | 5 |
| E1-02 | REST-API `/api/v1` CRUD + Upsert (idempotent, ETag) | M | 5 |
| E1-03 | UI Stammdaten: Tabellen mit Suche/Filter, Inline-Bearbeitung, Detail-Drawer | M | 8 |
| E1-04 | CSV/XLSX-Import mit Vorschau, Spaltenmapping, Validierungsbericht | M | 8 |
| E1-05 | Elementkatalog, Vorlagestufen (Matrix), Vorlagegründe – Domäne, API, UI | M | 8 |
| E1-06 | Seed-Mechanismus (`seed/<teilnehmer>/*.csv|json`), 30–50 Teile je Teilnehmer, fiktive Daten | M | 5 |
| E1-07 | Katalog-Export/Import JSON | C | 2 |

## Epic E2 – Freigabevorgang lokal (Phase 3, UC-03/05/07) ✅ abgeschlossen 19.11.2026

Zusätzlich umgesetzt: manuelle Erfassung von Anforderungen nicht angebundener Kunden, Dashboard-Kennzahlen (`/approval-cases/stats`), Playwright-Specs gegen die laufende Umgebung (`make e2e`, nächtlich in CI).
Offen: Vier-Augen-Freigabe (L-06), Werte-Spalten im Excel-Formblatt, echter Malware-Scanner, UI-Hinweis „Zustellung folgt mit Phase 4“ bei Phase 4 ersetzen.

| ID | Story | Prio | SP |
|---|---|---|---|
| E2-01 | Zustandsmaschine `ApprovalCase` (explizit, vollständig getestet inkl. verbotener Übergänge) | M | 5 |
| E2-02 | Wizard „Vorlage anfordern“ | M | 8 |
| E2-03 | Dokumentenmodul: Upload (S3), SHA-256, Typ-/Größenprüfung, Vorschau-Endpunkt | M | 5 |
| E2-04 | Wizard „PPF-Paket erstellen“ inkl. Vollständigkeitsprüfung | M | 8 |
| E2-05 | Excel-Formblatt-Import (Template-Definition als Daten, Feld-Mapping-Vorschau) | M | 8 |
| E2-06 | Einreichung: unveränderliche Revision, kanonisches JSON, Hash, JWS | M | 5 |
| E2-07 | Prüfansicht + Entscheidung + digitaler Bestätigungsdialog + JWS (`DecisionSigner`-Port) | M | 8 |
| E2-08 | Vorgangsliste (Tabelle + Kanban), Vorgangsdetail mit Timeline | M | 8 |
| E2-09 | Audit-Modul (append-only, Hash-Kette) | M | 3 |

## Epic E3 – EDC Push (Phase 4, UC-04/06/08) ✅ abgeschlossen 19.11.2026

Zusätzlich: Partnerschlüssel über EDC, Klärungsliste (UC-04 A1), Live-Updates (SSE), erstes Connector-Cockpit (Status, Nachrichten, Retry), Playwright-Specs 06 (Austausch) und 07 (Klärung).
Offen/Backlog: Option „bestehende Teilezuordnung ersetzen“ beim Zuordnen; Partnername statt `partner:BPNL…` in der Timeline; erneute Signaturprüfung für UNVERIFIED nach späterem Schlüsselabruf; Catena-X-Message-Header als Import statt lokaler Kopie (`TODO(EG)`).

| ID | Story | Prio | SP |
|---|---|---|---|
| E3-01 | Port `DataspaceConnector` + Adapter Tractus-X EDC Management API (Assets, Policies, Contract Definitions, Catalog, Negotiation, Transfer/EDR) | M | 8 |
| E3-02 | Provisionierung beim Start: Notification-Receive-Asset, Policies je Beziehung (idempotent, Reconciliation) | M | 5 |
| E3-03 | Transactional Outbox + Scheduler, Retry/Backoff, Dead-Letter | M | 5 |
| E3-04 | Sender: Katalog → Negotiation → EDR (Cache) → POST über Partner-Data-Plane | M | 8 |
| E3-05 | Empfänger: Inbox, Idempotenz, Schema-Validierung, Herkunftsprüfung | M | 5 |
| E3-06 | SAMM-Modelle v0.1.0 + JSON-Schema-Generierung + Mapper (`semantics`) | M | 8 |
| E3-07 | `ExchangeRecord` + Tab „Datenaustausch“ (live per SSE) | M | 5 |
| E3-08 | Integrationstest OEM↔Tier-1 über echte EDCs (Testcontainers/Compose) | M | 8 |

## Epic E4 – Pull über DTR (Phase 5) ✅ abgeschlossen 19.11.2026

Zusätzlich: Dokumenttransfer in beide Richtungen (Nachweise, Anhänge), Digital-Twin-Panel, Moduswahl mit Erklärung, Messwerte in ADR-0002, Spec 08 (Moduswechsel).
Offen/Backlog: bediente Abrufe als Audit-Ereignis statt nur Log; doppelte DTR-Abfrage im Pull-Modus (Poller + Job); blockierte Benachrichtigung bei dauerhaft nicht erreichbarer DTR; Test mit großen Dateien; Ablageort der Signatur bei Pull (`TODO(EG)`).

| ID | Story | Prio | SP |
|---|---|---|---|
| E4-01 | DTR-Client (AAS API): Shell + Submodel-Deskriptoren registrieren, Policy-geschütztes DTR-Asset | M | 5 |
| E4-02 | Submodel-Endpunkt `PpapSubmission` + Dokument-Endpunkt hinter EDC-Assets (je Kunde) | M | 5 |
| E4-03 | Consumer: DTR-Discovery über Katalog (`cx-taxo:DigitalTwinRegistry`), Lookup per `specificAssetIds`, Abruf + Hash-/Signaturprüfung | M | 8 |
| E4-04 | Moduswahl je Beziehung (PUSH / PULL / PUSH_NOTIFY_PULL_PAYLOAD) | M | 3 |
| E4-05 | ADR-0002 mit Messwerten (Latenz, Anzahl EDC-Schritte) aktualisieren → Input für Expert Group | S | 2 |

## Epic E5 – Kaskade und Transparenz (Phase 6, UC-09–12) ✅ abgeschlossen 20.11.2026

Zusätzlich: Herkunftsprüfung für Dokumente von Unterlieferanten, `reconciliation`-Rückmeldung bei Policy-Änderungen, Audit `SERVED_TO_PARTNER`, Ersetzen bestehender Teilezuordnungen, erneute Signaturprüfung.
Offen/Backlog: Audit-Export als PDF; Pseudonymisierung der Unterlieferanten-BPNL (`TODO(EG)`); EDC-Version im Cockpit (Management API liefert keine).

| ID | Story | Prio | SP |
|---|---|---|---|
| E5-01 | Tier-2-Teilnehmer im Compose-Profil + Seed | M | 3 |
| E5-02 | Kindvorgänge + `SubSupplierReference` | M | 5 |
| E5-03 | Connector-Cockpit (Status, Assets, Policies, Katalog, Negotiations, Transfers, Outbox/Inbox + Retry, Live-Log) | M | 13 |
| E5-04 | Policy-Profile je Beziehung in der UI | M | 5 |
| E5-05 | Dashboard (KPIs, Aktivität, Partnerstatus) | M | 5 |
| E5-06 | Datenraum-Ansicht (Demo-Modus, animierter Nachrichtenfluss aus echten ExchangeRecords aller Teilnehmer) | M | 8 |
| E5-07 | Nutzungszähler + Anzeige/Export | M | 3 |
| E5-08 | Audit-Export je Vorgang (JSON/PDF) | S | 3 |

## Epic E6 – Härtung und Demo (Phase 7)

| ID | Story | Prio | SP |
|---|---|---|---|
| E6-01 | Automatisierter E2E-Test der Demo-Story (OEM → Tier-1 → Tier-2 → zurück, alle EDCs) | M | 8 |
| E6-02 | Playwright-Smoke-Tests der Haupt-Screens | M | 5 |
| E6-03 | `docs/DEMO.md` Drehbuch, `make demo` Reset | M | 3 |
| E6-04 | Integrationsleitfaden API (ERP/QM), Betriebsdoku | M | 5 |
| E6-05 | Security-Review: Upload-Härtung, Header, Rate Limits, Secrets | M | 3 |
| E6-06 | Testabdeckung Domäne/Anwendung ≥ 80 % (JaCoCo-Gate) | M | 3 |

## Später (MVP+ / nicht Prototyp)

| ID | Thema | Prio |
|---|---|---|
| L-01 | Signatur über Verifiable Credentials / DCP statt Mandantenschlüssel | S |
| L-02 | Anbindung externer/Managed EDC + DIM-Wallet (Konfigurationsprofil) | S |
| L-03 | Multi-Mandanten-Betrieb auf einer Instanz (Hosting kleiner Zulieferer) | S |
| L-04 | Umstellung auf Catena-X-Standard-Aspektmodelle der Expert Group (`TODO(EG)`) | M (sobald verfügbar) |
| L-05 | Helm-Charts, Observability-Stack (OTel) | S |
| L-06 | Vier-Augen-Freigabe, Vertretungsregeln | C |
| L-07 | E-Mail-/Teams-Benachrichtigungen | C |
