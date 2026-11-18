# CLAUDE.md – msg CX PPAP Connector

Kommerzielles Produkt (Closed Source) von msg for automotive: digitaler PPAP/PPF-Datenaustausch zwischen OEM und Zulieferern über Catena-X (Tractus-X EDC, DSP). Prototyp → MVP, kein Wegwerf-PoC.

## Projektstatus
- **Phase 0 (Recherche & Plan) abgeschlossen – wartet auf Freigabe.** Noch kein Produktivcode.
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

## Kommandos (ab Phase 1)
- `make up` – komplette Umgebung inkl. Seed; `make up PROFILE=tier2` mit Tier-2
- `make demo` – Demo-Zustand zurücksetzen
- `make test` – Backend + Frontend Tests
