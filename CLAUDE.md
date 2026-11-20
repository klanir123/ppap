# CLAUDE.md – msg CX PPAP Connector

Kommerzielles Produkt (Closed Source) von msg for automotive: digitaler PPAP/PPF-Datenaustausch zwischen OEM und Zulieferern über Catena-X (Tractus-X EDC, DSP). Prototyp → MVP, kein Wegwerf-PoC.

## Projektstatus
- Phase 0–6 abgeschlossen (… Pull über DTR, **Kaskade und Transparenz UC-09…12**). Nächste Phase: 7 – Härtung und Demo (E2E der gesamten Demo-Story, DEMO.md, Integrations- und Betriebsdoku).
- Vollständige Aufgabenstellung: siehe `docs/input/` bzw. Erst-Prompt; Phasenplan in `docs/backlog.md` und `docs/ARCHITECTURE.md` §12.

## Wichtige Dokumente
- `docs/ARCHITECTURE.md` – arc42-light, Diagramme (Kontext, Container, Sequenzen Push/Pull)
- `docs/adr/` – Architekturentscheidungen (EDC-Port, Push/Pull, Semantik, Mandanten, Frontend, Build, Storage, Signatur)
- `docs/use-cases/` – UC-01 … UC-12 mit Akzeptanzkriterien
- `docs/domain-model.md` – Datenmodell, Zustandsmaschine, Nachrichtentypen
- `docs/versions.md`, `docs/tractusx-versions.md` – verifizierte Versionen (Stand 2026-11-18)
- `ASSUMPTIONS.md` – Annahmen; `TODO(EG)` = hängt von Catena-X Expert Group „Product Approval“ ab

## Architekturregeln (verbindlich)
- Modularer Monolith (Spring Modulith), Module: `masterdata, approval, exchange, twin, semantics, documents, audit, identity, usage` (+ `platform`). Grenzen per `ApplicationModules.verify()` + ArchUnit getestet.
- Innen hexagonal: `domain` (keine Spring/JPA-Imports) → `application` (Use Cases, Ports) → `adapter.in` / `adapter.out`.
- Datenaustausch mit Partnern **ausschließlich über EDC** (Port `DataspaceConnector`); nie direkte HTTP-Calls zwischen Teilnehmer-Backends, keine Mocks im Datenpfad.
- Ausgehende Nachrichten nur über Transactional Outbox; Empfänger idempotent (Message-ID, Inbox).
- Austauschformate nur im Modul `semantics` (versionierte Mapper, JSON-Schema-Validierung aller Payloads). Domäne kennt keine Austausch-DTOs.
- Keine fachlichen Daten oder Kataloge im Code – alles DB + `seed/`. Konfiguration nur über Env/Config (12-Factor).
- Eingereichte Revisionen, Entscheidungen, Audit-Events sind unveränderlich; Hash (SHA-256, RFC 8785) für alle Payloads/Dokumente.
- Object Storage nur über S3-API (AWS SDK v2); lokal SeaweedFS.

## Konventionen
- Code, Kommentare, Commit-Messages: Englisch. Doku: Deutsch. UI: DE (Standard) + EN.
- REST unter `/api/v1`, OpenAPI generiert (springdoc) → Frontend-Client mit orval.
- Java 25, Spring Boot 4.x (Jackson 3!), Maven 3.10 Wrapper. Frontend: React 19, TS, Vite, TanStack, Tailwind v4, shadcn/ui, zod v4.

## Kommandos
- `make up` (OEM + Tier-1) / `make up PROFILE=tier2`; `make up SKIP_BUILD=1` ohne Neubau
- `make test` – `./mvnw verify` + Frontend lint/typecheck/test/api:check
- `make smoke` – EDC-Ende-zu-Ende (Katalog, Negotiation, EDR, Data Plane) gegen laufende Umgebung
- `make demo` – App-Datenbanken zurücksetzen; `make clean` – alles inkl. Volumes löschen
- `make e2e` – Playwright-Specs (`frontend/e2e/live`) gegen die laufende Umgebung (`make up PROFILE=tier2`); lokal ggf. `E2E_CHROMIUM_PATH=/opt/pw-browsers/chromium-*/chrome-linux64/chrome`
- Backend allein: `JAVA_HOME=<jdk25> ./mvnw -B verify` (regeneriert `api/openapi.json` → danach `cd frontend && npm run api:generate`)
- Frontend: `cd frontend && npm run dev|lint|typecheck|test|build`

## Wichtige Implementierungsdetails
- API-Vertrag: Backend exportiert `api/openapi.json` im Test `OpenApiExportTest`; Frontend-Client wird daraus mit orval generiert (`src/api/generated`, eingecheckt). CI prüft beide Richtungen auf Drift.
- Docker: `Dockerfile` kopiert nur das fertige Jar (Frontend wird per maven-resources-plugin aus `frontend/dist` nach `classpath:/static` gebündelt) → Image-Build braucht kein Netz.
- Keycloak: Issuer für Browser `http://localhost:8180/realms/<p>`, Backend holt JWKS intern (`PPAP_OIDC_JWK_SET_URI`). Realms nur über `infra/keycloak/generate-realms.py` ändern.
- EDC 0.13: Participant-ID = DID `did:web:wallet-stub:<BPNL>`, DSP-Endpoint `http://<p>-edc-cp:8084/api/v1/dsp/2025-1`, Management API `http://<p>-edc-cp:8081/management/v3/...` (Header `X-Api-Key`). Policies im Namespace CX 2025/9 (siehe `infra/scripts/edc-smoke.py`).
- Container haben ggf. kein Internet: keine Package-Installation in Bootstrap-Jobs.
- Versionsabweichungen: TypeScript 6.0 (nicht 7), ESLint 9, Flyway 13.12.2 explizit gepinnt, @tanstack/react-table 8.21 (9.x neue API).
- Stammdaten: Vertrag `docs/api/masterdata-api.md`; Tabellen `md_*` mit `tenant_id`; Shared Kernel `platform.domain` (Bpnl, Bpns, Validation, fachliche Exceptions → Fehlercodes). REST, Import und Seed nutzen dieselben Application-Services.
- Seed: `seed/generate_*.py` erzeugen die Dateien (nicht von Hand editieren), `make seed-check` prüft Spiegelgleichheit/Integrität. Laden beim App-Start (`PPAP_SEED_ENABLED`).
- Audit: synchroner Listener nach Commit (eigene Transaktion), Hash-Kette je (tenant, objectType, objectId), append-only per Trigger.
- Frontend-Datenzugriff: generierter orval-Client + dünner Wrapper `features/masterdata/api/` bzw. `features/approvals/api/` (If-Match/ETag; `apiFetch` mit `onResponse` für ETag). Fehler über `code` → i18n (`lib/problem-errors.ts`).
- Freigabevorgang: Vertrag `docs/api/approval-api.md` (inkl. „Klarstellungen“). Zustandsmaschine als Regeltabelle (Prüfreihenfolge Mandantenrolle → Benutzerrolle → Status → Guards), `allowedActions` vom Server. Je Seite ein Vorgang, verknüpft über `exchangeId`. Phase 3 lokal: Events (`CaseRequested`, `SubmissionSubmitted`, `DecisionMade` …) werden veröffentlicht, Zustellung per Outbox/EDC folgt in Phase 4; Eingang über `Receive*UseCase` (bisher nur Tests).
- Signatur: Austausch-Payload v0.1 (`semantics`, handgeschriebene JSON-Schemas, später aus SAMM) → RFC 8785 (erdtman) → SHA-256 (`sha256:<hex>`) → JWS ES256 detached, `b64:false` (RFC 7797), `kid` = RFC-7638-Thumbprint; Schlüssel aus OpenBao `secret/ppap-signing-key`; öffentlich `/.well-known/jwks.json`. App startet ohne Schlüssel nicht (außer `PPAP_SIGNING_PROVIDER=generated`).
- Dokumente: Typprüfung per Magic Bytes (Tika), inhaltsadressiert `<tenant>/<sha256>` in S3.
- Seed: `seed/generate_*.py` (catalog, participants, documents, forms, cases) – PDFs/XLSX reproduzierbar (unkomprimiert, `lxml` gepinnt, `seed/requirements.txt`).
- EDC-Healthcheck in Compose nutzt `/api/check/readiness` (liveness liefert direkt nach Start sporadisch 404).
- Austausch (Phase 4, Vertrag `docs/api/exchange-api.md`): Outbox = `BEFORE_COMMIT`-Listener in `exchange` schreibt validierten Umschlag in `ex_message` (gleiche Tabelle für Outbox, Inbox, Austauschprotokoll; Schritte in `ex_message_step`), Worker mit SKIP LOCKED + Lease, Reihenfolge je exchangeId. Provisionierung idempotent beim Start/bei Partneränderungen (Assets `ppap-notification-receive`, `ppap-public-keys`; BPN-Gruppe `ppap-partners`; Policy-IDs mit Inhalts-Hash, da referenzierte Policies in 0.13 nicht änderbar). Shared Secret über Vault-`secretName`. Data Plane reicht `Edc-Bpn` durch → Absenderprüfung. EDR-Wiederverwendung mit `auto_refresh`. Partnerschlüssel per EDC (`ppap-public-keys`).
- SAMM: `backend/semantics-models` (SAMM 2.2, ESMF 2.16.2) generiert die JSON-Schemas, gegen die `semantics` validiert; `values`/`valueSchema`/`payload` im Austausch als kanonischer JSON-Text.
- Live-Test des Austauschs: `APP_DECISION_ALLOW_TECHNICAL_CLIENTS=true make up PROFILE=tier2`, dann `./mvnw -pl backend/app -am -Plive verify`. Standard: technische Clients dürfen nicht entscheiden.
- EDC 0.13: Durch die Data Plane kommt 202 als 200 und 401 als 500 beim Sender an; „Policy referenced“ liefert 400 statt 409. Die Data Plane reicht Antwort-Header des Backends **nicht** weiter (JWS bei Pull daher unter `<href>/signature`).
- Pull (Phase 5, Vertrag `docs/api/pull-api.md`): Twin je Vorgang in der DTR des Lieferanten (`/api/v3`, Tenant über Header `Edc-Bpn`, `lookup/shells` mit base64url-JSON), Registrierung als lokale Outbox-Aufgabe `TWIN_REGISTRATION` vor `PPAP_SUBMISSION_AVAILABLE`. Pull-Job + Poller in `exchange` (nicht `twin`, Modulzyklus). Assets je Partner `ppap-submission-<BPNL>`, `ppap-documents-<BPNL>`, `ppap-dtr` (OAuth2 über Data Plane, Secret im Vault). Interne Endpunkte: Secret → 401, kein `Edc-Bpn` → 403, nicht geteilt/Entwurf → 404. Dokumente in beiden Richtungen per Pull mit SHA-256-Prüfung (`ex_document_transfer`). Modus der Lieferanten-Beziehung bei Einreichung gilt je Revision. DTR-Angebote werden gecacht (positiv 10 min, „keine DTR“ 60 s, `PPAP_PULL_NO_OFFER_CACHE_TTL`); Retries umgehen den Cache; Poller startet erst nach eigener Provisionierung.
- Live-Specs `make e2e` (01–10) inkl. Moduswechsel (08), Kaskade (09), Cockpit/Policies/Nutzung (10); lokal schneller mit `APP_PULL_POLL_INTERVAL=15s make up PROFILE=tier2`. Vor wiederholten Läufen ggf. `make demo PROFILE=tier2`.
- Kaskade (Phase 6, Vertrag `docs/api/cascade-transparency-api.md`): Kindvorgang = Kundenvorgang mit `parent_case_id`; Referenzen je Revision (JSONB, unveränderlich nach Einreichung), im signierten `PpapSubmission.subSupplierReferences`. Dokumente von Partnern (Uploader `partner:<BPNL>`) sind **nach Herkunft** gesperrt: nie an eigene Einreichung anhängbar (422 `SUB_SUPPLIER_DOCUMENT_NOT_SHAREABLE`), nie ausgeliefert; eigene Uploads mit gleichem Inhalt sind erlaubt (Autorisierung je Dokument-Datensatz, nicht je Hash).
- Policy-Profile: Profil `default` per Flyway-Migration (Platzhalter), nicht per Seed; gemeinsame Assets nutzen `default`, partnerspezifische das Profil der Beziehung; Policy-IDs inhaltsadressiert; Schreibantworten liefern `reconciliation`.
- Nutzung: Zähler ereignisbasiert, Monat = `submittedAt` der Revision auf beiden Seiten, `POST /usage/recompute` rechnet aus Rohdaten nach. Audit-Export: kanonisches JSON + JWS (`X-Signature`).
- Datenraum-Ansicht `/dataspace`: Perspektive des eigenen Teilnehmers (keine Sammelansicht); für Messen die Tier-1-Ansicht, `?present=1` Präsentationsmodus.
