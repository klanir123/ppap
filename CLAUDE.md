# CLAUDE.md – msg CX PPAP Connector

Kommerzielles Produkt (Closed Source) von msg for automotive: digitaler PPAP/PPF-Datenaustausch zwischen OEM und Zulieferern über Catena-X (Tractus-X EDC, DSP). Prototyp → MVP, kein Wegwerf-PoC.

## Projektstatus
- Phase 0 (Plan) freigegeben, **Phase 1 (Fundament) abgeschlossen**. Nächste Phase: 2 – Stammdaten (UC-01/02).
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
- Backend allein: `JAVA_HOME=<jdk25> ./mvnw -B verify` (regeneriert `api/openapi.json` → danach `cd frontend && npm run api:generate`)
- Frontend: `cd frontend && npm run dev|lint|typecheck|test|build`

## Wichtige Implementierungsdetails
- API-Vertrag: Backend exportiert `api/openapi.json` im Test `OpenApiExportTest`; Frontend-Client wird daraus mit orval generiert (`src/api/generated`, eingecheckt). CI prüft beide Richtungen auf Drift.
- Docker: `Dockerfile` kopiert nur das fertige Jar (Frontend wird per maven-resources-plugin aus `frontend/dist` nach `classpath:/static` gebündelt) → Image-Build braucht kein Netz.
- Keycloak: Issuer für Browser `http://localhost:8180/realms/<p>`, Backend holt JWKS intern (`PPAP_OIDC_JWK_SET_URI`). Realms nur über `infra/keycloak/generate-realms.py` ändern.
- EDC 0.13: Participant-ID = DID `did:web:wallet-stub:<BPNL>`, DSP-Endpoint `http://<p>-edc-cp:8084/api/v1/dsp/2025-1`, Management API `http://<p>-edc-cp:8081/management/v3/...` (Header `X-Api-Key`). Policies im Namespace CX 2025/9 (siehe `infra/scripts/edc-smoke.py`).
- Container haben ggf. kein Internet: keine Package-Installation in Bootstrap-Jobs.
- Versionsabweichungen: TypeScript 6.0 (nicht 7), ESLint 9, Flyway 13.12.2 explizit gepinnt.
