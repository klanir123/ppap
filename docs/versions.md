# Technologie-Versionen (verifiziert)

Geprüft am **2026-11-18** gegen offizielle Quellen (Maven Central, npm-Registry, api.spring.io / start.spring.io, Adoptium-API, Docker Hub, maven.apache.org, services.gradle.org). Versionen werden in Phase 1 in `pom.xml` / `package.json` / `compose` fixiert und per Renovate/Dependabot aktuell gehalten.

## Backend

| Komponente | Version | Hinweis |
|---|---|---|
| Java (LTS) | **25** (Temurin `25.0.5+8`, Image `eclipse-temurin:25-jre-noble`) | 27 ist kein LTS |
| Spring Boot | **4.1.2** | 4.2.0 GA geplant 30.11.2026 → Upgrade in Phase 1/2 prüfen. 3.5.x: OSS-Support seit 30.06.2026 beendet → nicht verwenden |
| Spring Modulith | 2.1.2 | Linie folgt Boot-Minor (2.1 ↔ 4.1, 2.2 ↔ 4.2) |
| Spring Cloud | 2025.1.4 | nur falls benötigt |
| springdoc-openapi | 3.2.0 | 3.x = Boot 4 |
| openapi-generator-maven-plugin | 7.27.0 | |
| Flyway | 13.12.2 | explizit gepinnt (Boot 4.1.2 managt 12.4.0) |
| Testcontainers | 2.0.5 | Module heißen `testcontainers-<modul>`, kein JUnit 4 |
| ArchUnit | 1.5.1 | |
| jMolecules | BOM 2025.0.2 | |
| Nimbus JOSE+JWT | 10.10 | JWS-Signatur Entscheidungen/Einreichungen |
| Apache POI | 6.0.0 | neue Major-Version |
| MapStruct | 1.6.3 | |
| Apache Commons CSV | 1.14.1 | CSV-Import |
| Apache Tika core | 4.2.0 | Dateityp-Erkennung (Magic Bytes) |
| java-json-canonicalization (erdtman) | 1.1 | RFC 8785 |
| Resilience4j | 2.4.0 | Modul `resilience4j-spring-boot4` |
| AWS SDK v2 (S3) | 2.59.3 | einziger Storage-Client (S3-API) |
| networknt json-schema-validator | 3.0.9 | 3.x mit API-Änderungen |
| Eclipse ESMF SDK | 2.16.2 | im Einsatz: `backend/semantics-models` (SAMM 2.2 → JSON Schema, HTML, Beispiel-Payloads) |
| Maven | **3.10.1** | Maven 4 noch RC |

Wichtige Breaking Changes: Spring Boot 4 = Spring Framework 7, Jakarta EE 11, **Jackson 3** (`tools.jackson.*`), modularisierte Starter.

## Infrastruktur

| Komponente | Version | Hinweis |
|---|---|---|
| PostgreSQL | 18.7 | |
| Keycloak | 26.8.2 (`quay.io/keycloak/keycloak`) | |
| Object Storage | **SeaweedFS 4.54** (Apache-2.0) | MinIO Community ist eingestellt (Repo „no longer maintained“, kein Docker-Image mehr) → siehe ADR-0007 |
| Secrets (EDC) | **OpenBao 2.7.2** (MPL-2.0, Vault-API-kompatibel) bzw. HashiCorp Vault 2.2.2 (BSL) | Auswahl siehe ADR-0001 |
| Tractus-X-Komponenten | siehe [tractusx-versions.md](tractusx-versions.md) | |

## Frontend

| Paket | Version | Hinweis |
|---|---|---|
| react / react-dom | 19.3.1 | |
| typescript | **6.0.x** (gepinnt `~6.0.3`) | TS 7 liefert die klassische Compiler-API nicht mehr; typescript-eslint 8.x verlangt `<6.1` |
| vite / @vitejs/plugin-react | 8.3.8 / 6.3.0 | |
| @tanstack/react-query | 5.108.0 | |
| @tanstack/react-router | 1.170.46 | |
| @tanstack/react-table | 8.21.3 | 9.x mit neuer API – bewusst noch nicht |
| tailwindcss / @tailwindcss/vite | 4.3.5 | CSS-first-Konfiguration (`@theme`) |
| shadcn (CLI) | 4.23.0 | |
| react-hook-form | 7.92.0 | |
| zod | 4.6.7 | v4-API |
| i18next / react-i18next | 27.1.5 / 18.0.2 | |
| orval | 8.48.0 | OpenAPI-Client inkl. TanStack-Query-Hooks (statt @hey-api/openapi-ts, noch < 1.0) |
| @playwright/test | 1.65.1 | |
| vitest | 5.1.1 | |
| eslint | 9.39 | jsx-a11y 6.10 unterstützt ESLint 10 noch nicht |
| oidc-client-ts | 3.5.0 | |
| react-pdf / pdfjs-dist | 11.1.0 / 6.5.438 | Dokumentvorschau |
