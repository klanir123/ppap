# ADR-0006 Build-Tool und Modulstruktur

## Kontext
Java-Backend als modularer Monolith (Spring Modulith, hexagonal), SAMM-Codegenerierung, OpenAPI-Generierung, Frontend-Build im selben Repository.

## Entscheidung
**Maven 3.10.x** (mit Maven Wrapper), Multi-Modul:
```
pom.xml                    (Parent, BOMs, Plugin-Management)
backend/
  app/                     Spring-Boot-Anwendung (Modulith-Module als Packages)
  semantics-models/        SAMM .ttl + ESMF-Plugin → JSON-Schemas (Artefakt)
frontend/                  Vite-Projekt (über frontend-maven-plugin oder separat in CI)
```
Die fachlichen Module (`masterdata`, `approval`, `exchange`, `twin`, `semantics`, `documents`, `audit`, `identity`, `usage`) sind **Spring-Modulith-Module (Top-Level-Packages)** innerhalb von `app`, nicht separate Maven-Module. Begründung: Modulith verifiziert Grenzen per Test (`ApplicationModules.verify()`), Refactorings bleiben billig; Maven-Module nur dort, wo sie echten Build-Nutzen haben (Codegenerierung).
Innerhalb jedes Moduls: `domain` (keine Spring-/JPA-Abhängigkeiten, ArchUnit-geprüft), `application` (Use Cases, Ports), `adapter.in` (REST, Messaging), `adapter.out` (JPA, EDC, S3 …).

## Alternativen
- *Gradle 9*: schneller, flexibler; aber Maven ist bei msg/Kunden-Betrieben verbreiteter, ESMF/openapi-Plugins sind primär Maven-first, deklarativ leichter zu warten.
- *Ein Maven-Modul je Fachmodul*: härtere Grenzen, aber mehr Boilerplate; Modulith + ArchUnit liefern die Grenzen ausreichend.

## Konsequenzen
+ Einfacher, verbreiteter Build; Codegenerierung sauber gekapselt.
− Maven-Builds langsamer als Gradle mit Build-Cache (akzeptabel).
