# msg CX PPAP Connector

Digitaler, strukturierter PPAP/PPF-Datenaustausch zwischen OEMs und Zulieferern über Catena-X (Eclipse Tractus-X EDC, Dataspace Protocol) – Connector mit REST-API für die Systemintegration und vollständiger Oberfläche für kleine Zulieferer.

> **Status: Phase 4 (Austausch über EDC) abgeschlossen.** Anforderung, Statusmeldungen, signierte Einreichung und Entscheidung laufen über echte Tractus-X-EDCs zwischen OEM, Tier-1 und Tier-2 (Outbox/Inbox, Partnerschlüssel über EDC, Klärungsliste, Live-Updates, Tab „Datenaustausch“ mit jedem EDC-Schritt, erstes Connector-Cockpit). Bisher: Lokaler Datenraum mit echten EDCs; Stammdaten mit Import und Audit; Freigabevorgänge mit Anforderungs-Assistent, PPF-Paket (Formulare aus JSON-Schema, Nachweise mit SHA-256, Excel-Formblatt), unveränderlichen signierten Revisionen (JWS), Prüfansicht und digital bestätigter Entscheidung, Dashboard-Kennzahlen; Demo-Vorgänge je Teilnehmer. Nächste Phase: Austausch über EDC (UC-04/06/08).

## Schnellstart

Voraussetzungen: Docker (Compose v2), Java 25 (Temurin), Node 22, Python 3, `make`. Ca. 6 GB freier RAM (Vollausbau gemessen: 4,4 GiB).

```bash
make up
```

```bash
make up PROFILE=tier2
```

| Teilnehmer | URL | Login (lokal) |
|---|---|---|
| Demo-OEM AG (`BPNL00000000OEM1`) | http://localhost:8081 | `oem.quality` / `demo` (weitere: `oem.masterdata`, `oem.admin`) |
| Demo-Tier1 GmbH (`BPNL000000TIER01`) | http://localhost:8082 | `tier1.quality` / `demo` (`tier1.masterdata`, `tier1.admin`) |
| Demo-Tier2 KG (`BPNL000000TIER02`, Profil `tier2`) | http://localhost:8083 | `tier2.quality` / `demo` (`tier2.admin`) |
| Keycloak-Admin | http://localhost:8180 | `admin` / `admin` |

Weitere Kommandos (`make help`):

| Kommando | Wirkung |
|---|---|
| `make smoke` | Ende-zu-Ende-Test über echte EDCs: Katalog → Vertragsverhandlung → EDR → Data-Plane-Abruf |
| `make test` | Backend (`./mvnw verify`: Unit, Integration mit Testcontainers, Modulith/ArchUnit) + Frontend (Lint, Typen, Tests, API-Drift) |
| `make demo` | Anwendungsdaten zurücksetzen und Seed-Daten neu laden |
| `make seed-check` | Seed-Daten prüfen (Generatoren aktuell, Zuordnungen und Vorgänge spiegelgleich) |
| `make e2e` | Browser-Ende-zu-Ende-Specs gegen die laufende Umgebung (nach `make up PROFILE=tier2`) |
| `make build` / `make restart` | Neu bauen / App-Container neu starten |
| `make ps`, `make logs SERVICE=oem-app`, `make stats` | Status, Logs, Speicherverbrauch |
| `make down` / `make clean` | Stoppen / Stoppen inkl. Löschen aller Daten |

Eigene Werte: `infra/.env.example` nach `infra/.env` kopieren. Alle Zugangsdaten sind **nur lokale Demo-Werte**.

## Repository

```
backend/app/        Spring Boot 4 / Java 25, modularer Monolith (Spring Modulith, hexagonal)
frontend/           React 19 + TypeScript + Vite, TanStack, Tailwind v4, shadcn/ui
api/openapi.json    API-Vertrag (vom Backend generiert, Quelle für den Frontend-Client)
infra/              docker compose: EDCs, Wallet-Stub, Keycloak, Postgres, SeaweedFS, DTR
  keycloak/         Realm je Teilnehmer (generiert: make realms)
  scripts/          edc-smoke.py
docs/               Architektur, ADRs, Use Cases, Backlog, Versionen
```

## Dokumentation

| Dokument | Inhalt |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Architektur (arc42-light): Kontext, Container, Module, Sequenzen Push/Pull, Deployment, Risiken, Phasenplan |
| [docs/adr/](docs/adr/README.md) | Architekturentscheidungen 0001–0008 |
| [docs/use-cases/](docs/use-cases/README.md) | UC-01 … UC-12 inkl. Diagramm und Akzeptanzkriterien |
| [docs/domain-model.md](docs/domain-model.md) | Datenmodell, Zustandsmaschine, Nachrichtentypen |
| [docs/api/masterdata-api.md](docs/api/masterdata-api.md) | API-Vertrag Stammdaten inkl. Import und Fehlercodes (auch für ERP/QM-Integration) |
| [docs/api/exchange-api.md](docs/api/exchange-api.md) | Vertrag Datenaustausch über EDC: Nachrichten, Assets/Policies, Outbox/Inbox, Klärungsliste, SSE |
| [backend/semantics-models/README.md](backend/semantics-models/README.md) | Vorläufige SAMM-Aspektmodelle (Beitrag für die Expert Group) |
| [docs/api/approval-api.md](docs/api/approval-api.md) | API-Vertrag Freigabevorgang: Zustandsmaschine, Einreichung, Entscheidung, Signatur, Dokumente, Excel-Formblatt |
| [seed/README.md](seed/README.md) | Seed-Daten |
| [docs/backlog.md](docs/backlog.md) | Priorisiertes Backlog (Epics/Stories) |
| [docs/versions.md](docs/versions.md) · [docs/tractusx-versions.md](docs/tractusx-versions.md) | Verifizierte Versionen |
| [ASSUMPTIONS.md](ASSUMPTIONS.md) | Annahmen und offene Punkte (`TODO(EG)`) |
| [docs/analysis/customs-demonstrator.md](docs/analysis/customs-demonstrator.md) | Analyse des Customs-Demonstrators (Wiederverwendung) |
| [frontend/README.md](frontend/README.md) | Frontend-Entwicklung, Design-Tokens |
| [CLAUDE.md](CLAUDE.md) | Arbeitsregeln für KI-gestützte Entwicklung |

## Entwicklung ohne Container für die App

```bash
make up SKIP_BUILD=1
```

Backend lokal gegen die Compose-Infrastruktur starten (Werte siehe `infra/compose.yaml`, Service `oem-app`), Frontend mit Hot Reload:

```bash
cd frontend && VITE_BACKEND_URL=http://localhost:8081 npm run dev
```
