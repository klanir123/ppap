# msg CX PPAP Connector

Digitaler, strukturierter PPAP/PPF-Datenaustausch zwischen OEMs und Zulieferern über Catena-X (Eclipse Tractus-X EDC, Dataspace Protocol) – Connector mit REST-API für die Systemintegration und vollständiger Oberfläche für kleine Zulieferer.

> **Status: Phase 0 – Recherche und Plan.** Es gibt noch keinen lauffähigen Code. Die Dokumente unten warten auf Freigabe.

## Dokumentation

| Dokument | Inhalt |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Architektur (arc42-light): Kontext, Container, Module, Sequenzen Push/Pull, Deployment, Risiken, Phasenplan |
| [docs/adr/](docs/adr/README.md) | Architekturentscheidungen 0001–0008 |
| [docs/use-cases/](docs/use-cases/README.md) | UC-01 … UC-12 inkl. Diagramm und Akzeptanzkriterien |
| [docs/domain-model.md](docs/domain-model.md) | Datenmodell, Zustandsmaschine, Nachrichtentypen |
| [docs/backlog.md](docs/backlog.md) | Priorisiertes Backlog (Epics/Stories) |
| [docs/versions.md](docs/versions.md) · [docs/tractusx-versions.md](docs/tractusx-versions.md) | Verifizierte Versionen (Stand 18.11.2026) |
| [ASSUMPTIONS.md](ASSUMPTIONS.md) | Annahmen und offene Punkte (`TODO(EG)`) |
| [CLAUDE.md](CLAUDE.md) | Arbeitsregeln für KI-gestützte Entwicklung |

## Schnellstart (ab Phase 1)

```bash
make up            # OEM + Tier-1 inkl. EDCs, Keycloak, Seed-Daten
make up PROFILE=tier2
make demo          # Demo-Zustand zurücksetzen
```
