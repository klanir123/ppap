# ADR-0005 Frontend-Technologie

## Kontext
Die UI ist das Demo- und Arbeitswerkzeug für kleine Zulieferer: modern, zugänglich (WCAG 2.1 AA), zweisprachig, Live-Updates, viele Formulare/Wizards und Tabellen. Das Team soll schnell produktiv sein; das Produkt soll langfristig wartbar sein.

## Entscheidung
**React 19 + TypeScript + Vite 8** (SPA), ausgeliefert als statische Dateien durch das Backend-Image (gleiches Image je Teilnehmer, Laufzeitkonfiguration via `/config.json`).
- Routing: **TanStack Router** (typsicher, Search-Params für Filter)
- Server-State: **TanStack Query**; API-Client + Query-Hooks generiert aus OpenAPI mit **orval**
- UI: **Tailwind CSS v4 + shadcn/ui** (Radix-Primitives → Tastatur/ARIA), Icons lucide, Tabellen TanStack Table, Kanban dnd-kit
- Formulare: **react-hook-form + zod v4** (Schemas teils aus OpenAPI generiert)
- i18n: **i18next/react-i18next** (DE Standard, EN)
- Auth: OIDC Authorization Code + PKCE (`oidc-client-ts`/`react-oidc-context`)
- Live: **Server-Sent Events** (unidirektional reicht; einfacher als WebSocket, funktioniert durch Proxies)
- Dokumentvorschau: react-pdf; Tests: Vitest + Testing Library, Playwright (E2E/Smoke, axe-core für A11y)

## Alternativen
- *Angular*: im Enterprise-Umfeld verbreitet, sehr gut strukturiert; schwerer, geringere Auswahl an modernen Komponentenbibliotheken im shadcn-Stil.
- *Next.js*: SSR nicht nötig (authentifizierte Fach-App), zusätzlicher Node-Server je Teilnehmer.
- *Vaadin* (Java-only): schnelle Entwicklung, aber weniger Freiheit im UX und kommerzielle Lizenzanteile.

## Konsequenzen
+ Breites Ökosystem, sehr gute DX, A11y-fähige Primitives, kein zusätzlicher Laufzeit-Server.
− TypeScript 7 (nativer Compiler) ist neu; bei Tooling-Problemen auf TS 5.x/6.x pinnen.
