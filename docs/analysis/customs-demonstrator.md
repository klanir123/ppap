# Analyse: Customs-Demonstrator (`klanir123/exchangeCustomsInformation`)

Stand: 18.11.2026, Commit `bc24339` („Initial commit“, einziger Commit). 107 Dateien, ca. 1.200 Zeilen Java im Backend, **keine Tests**.

## Zweck und Stack
Erfassung von Zoll-/Ursprungsdaten je Teil (Catena-X-ID, Kunden-/Herstellerteil, Zollgebiet, Präferenzstatus, Kontakt) und – geplant – Versand als Notification über EDC.

| Bereich | Technik |
|---|---|
| Backend | Java 21, Spring Boot **3.4.2**, Lombok, springdoc (Swagger-UI) |
| Persistenz | JPA, **Liquibase**, H2 (Profil `h2`) / PostgreSQL 17.3 (Profil `dev`) |
| UI | **Thymeleaf**-Server-Rendering (separate Spring-Boot-App, Port 9090), 2 Seiten (Liste, Bearbeiten), Vanilla-JS |
| Build | Maven-Multimodul: `commons`, `backend-api`, `backend-core`, `backend-jpa`, `backend-notify`, `backend-app`, `ui` |
| Betrieb | docker-compose (Postgres + Backend + UI), Helm-Chart für Minikube |

## EDC-Integration
**Nicht implementiert.** `EdcCustomsDataNotificationAdapter` ist ein Skelett: Discovery, Katalog, Negotiation und Transfer sind `TODO`s, die leere Objekte zurückgeben; `sendNotification` schreibt das JSON nur ins Log. Kein EDC, kein Wallet/BDRS, keine DTR im Compose/Helm. Sender- und Empfänger-BPN sind hart codiert (`"Bob-123-456-789"`, `"ALICE-987-654-321"`, nicht BPNL-konform).

Brauchbar ist nur die **Schrittfolge** in `AbstractCustomsDataNotificationAdapter` (Template Method: Endpoint ermitteln → Vertrag wählen → verhandeln → Transfer → senden). Sie entspricht unserem Port `DataspaceConnector` (ADR-0001), ist aber synchron, ohne Outbox/Retry/EDR-Cache und wirft `RuntimeException`.

## Stammdaten
- Ein Aggregat `CustomsData` mit eingebetteten `Part` (Nummer, Name) für Kunde/Hersteller und `ContactData`. Keine Partner-, Standort- oder Beziehungsverwaltung.
- Wertelisten (Zollgebiete, Bundesländer, Präferenzstatus) als **Java-Enums** in `customs-commons` → widerspricht unserer Regel „Kataloge als Daten“.
- **Kein CSV/Excel-Import**, keine Validierung, `delete` gibt `null` zurück.

## Bewertung

### Übernehmen (als Muster, nicht als Code)
| Was | Wo | Verwendung bei uns |
|---|---|---|
| Hexagonale Paketstruktur (`adapter.in/out`, `application.domain`, `application.port.in/out`) | `customs-backend-core` | bestätigt unseren Schnitt (ARCHITECTURE §5.3); wir nutzen Modulith-Module statt Maven-Module je Schicht |
| Stereotyp-Annotation `@Adapter` | `common/config/Adapter.java` | ersetzen durch jMolecules-Annotationen (`@SecondaryAdapter` …), die ArchUnit direkt prüft |
| Schrittfolge des Notification-Versands | `AbstractCustomsDataNotificationAdapter` | Ablauf in `NotificationSender` (asynchron via Outbox) |
| Use-Case-Interfaces + Command/Query-Objekte | `application/port/in` | gleiche Konvention für unsere Application-Services |
| Hinweis aus der README: UI soll einen aus OpenAPI generierten Client nutzen statt das API-Modul zu importieren | `readme.md` | umgesetzt über orval (ADR-0005) |

### Nicht übernehmen
| Was | Grund |
|---|---|
| EDC-Adapter-Code | nur Stubs; muss für Tractus-X EDC 0.13 / Management API v3 neu geschrieben werden |
| Thymeleaf-UI | erfüllt die UX-Anforderungen (Prompt §10) nicht; React-SPA geplant |
| Spring Boot 3.4 / Java 21 | OSS-Support für Boot 3.x beendet; wir starten mit Boot 4 / Java 25 |
| Liquibase | Vorgabe ist Flyway |
| Enums für Wertelisten, hart codierte BPNs | verstößt gegen „keine festverdrahteten Daten“ |
| Lombok `@Data` auf Domänenobjekten | veränderliche Domäne ohne Invarianten; wir nutzen Records/explizite Aggregate |
| Compose/Helm mit Klartext-Passwörtern (`test/test`) | Secrets per `.env`/Bootstrap |
| Getrennte UI-App | unnötiger Zusatzprozess; SPA wird vom Backend ausgeliefert |

## Lizenz
Im Repository gibt es **keine LICENSE-Datei**, das Repo ist öffentlich. Ohne Lizenz gilt Urheberrecht des Autors (Package `com.msg.customs` deutet auf msg als Urheber hin). Da wir ohnehin nur Muster und keinen Code übernehmen, ist das unkritisch. Falls doch Code übernommen werden soll: Urheberschaft intern bestätigen lassen.

## Fazit
Der Demonstrator ist ein frühes Gerüst ohne funktionierenden EDC-Datenaustausch, ohne Import und ohne Tests. Er bestätigt die Architekturrichtung (hexagonal, Ports für Speicher und Benachrichtigung), liefert aber **keinen wiederverwendbaren Code** für Kernthemen des PPAP Connectors. Die Planung aus Phase 0 bleibt unverändert; im Backlog entfällt nichts.
