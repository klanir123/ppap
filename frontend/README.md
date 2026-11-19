# Frontend – msg CX PPAP Connector

React-SPA (ADR-0005) für den PPAP/PPF-Datenaustausch über Catena-X. Jeder Teilnehmer (OEM, Tier-1, Tier-2 …) betreibt eine eigene Instanz desselben Builds; Teilnehmer, OIDC-Client und Standardsprache kommen zur Laufzeit vom Backend (`GET /api/v1/config`).

## Stack

React 19.3 · TypeScript 6.0 · Vite 8.3 · TanStack Router (code-based, typsicher) + Query 5 · Tailwind CSS 4.3 (CSS-first, `@theme`) · shadcn/ui (Radix, Stil „nova“) · lucide-react · react-hook-form + zod 4 · i18next · oidc-client-ts/react-oidc-context · orval 8 · Vitest 5 + Testing Library · Playwright · ESLint 9 (flat) + Prettier.

**TypeScript 6.0 statt 7.0:** TS 7 (nativer Compiler) liefert keine klassische Compiler-API mehr; `typescript-eslint` 8.x setzt `typescript >=4.8.4 <6.1.0` voraus. Daher gepinnt auf `~6.0.3`. Wechsel auf 7.x, sobald typescript-eslint ihn unterstützt.
**ESLint 9 statt 10:** `eslint-plugin-jsx-a11y` 6.10 unterstützt ESLint 10 noch nicht.

## Scripts

| Script                            | Zweck                                                                                                   |
| --------------------------------- | ------------------------------------------------------------------------------------------------------- |
| `npm run dev`                     | Dev-Server auf http://localhost:5173, Proxy `/api`, `/actuator`, `/v3` → `VITE_BACKEND_URL`             |
| `npm run build`                   | Typecheck + Produktions-Build nach `dist/` (Base `/`; wird ins Spring-Boot-Image kopiert)               |
| `npm run preview`                 | Build lokal ausliefern                                                                                  |
| `npm run typecheck`               | `tsc -b`                                                                                                |
| `npm run lint`                    | ESLint (typescript-eslint, react-hooks, jsx-a11y), 0 Warnungen erlaubt                                  |
| `npm run format` / `format:check` | Prettier                                                                                                |
| `npm run test` / `test:watch`     | Vitest (jsdom)                                                                                          |
| `npm run e2e`                     | Playwright-Smoke-Tests (benötigt laufendes Backend)                                                     |
| `npm run api:generate`            | API-Client aus `../api/openapi.json` (vom Backend exportiert)                                           |
| `npm run api:check`               | Drift-Check (CI): generiert in ein Temp-Verzeichnis und schlägt fehl, wenn `src/api/generated` abweicht |

## Entwicklung

```bash
cp .env.example .env.local        # optional, Default: http://localhost:8080
npm ci
npm run dev
```

`VITE_BACKEND_URL` bestimmt nur das Proxy-Ziel des Dev-Servers; im Betrieb liefert das Backend die SPA selbst aus (same origin). Gegen einen anderen Teilnehmer: `VITE_BACKEND_URL=http://localhost:8082 npm run dev`.

Ablauf beim Start: `GET /api/v1/config` → OIDC (Authorization Code + PKCE, Scope `openid profile email`, Silent Renew, Token in `sessionStorage` je Tab) → Login-Landingpage (kein Auto-Redirect) → `GET /api/v1/me` → Router. Nach dem Redirect entfernt `onSigninCallback` `code`/`state` aus der URL und stellt den Zielpfad wieder her.

## API-Client

`orval.config.ts` erzeugt React-Query-Hooks nach `src/api/generated/` (eingecheckt, nicht von Hand ändern). Alle Requests laufen über den Mutator `src/api/fetcher.ts`: Bearer-Token des OIDC-Users, `X-Correlation-Id`, Fehler als `ApiError` mit RFC-9457-`ProblemDetail`. Single Source of Truth ist die vom Backend exportierte Spec `../api/openapi.json`; nach Änderungen `npm run api:generate` ausführen und committen (`npm run api:check` prüft das in CI). Komponentennamen im Systemstatus sind freie Strings – unbekannte erhalten ein generisches Icon und den Rohnamen als Label (i18n `defaultValue`).

## Architektur

```
src/
  app/            Bootstrap (App.tsx), Router, QueryClient, Navigation
  features/       fachliche/strukturelle Features
    auth/         OIDC, Login-Seite, AuthGate (/me), RequirePermission
    config/       Laufzeitkonfiguration, Teilnehmer-Branding
    shell/        App-Shell: Sidebar, Top-Bar, Teilnehmerkontext, Breadcrumbs, Benutzermenü
    dashboard/ system/ placeholder/ errors/
  components/ui/  shadcn/ui-Primitives (generiert, minimal angepasst: i18n-Texte)
  components/     eigene wiederverwendbare Komponenten (StatusBadge, EmptyState, …)
  lib/            i18n, permissions, participant-Farben, Status-Vokabular, utils
  api/            fetcher, ProblemDetail, generated/
  locales/de|en/  Übersetzungen je Namespace (common, nav, status, auth, dashboard, system, pages, errors)
```

- **Berechtigungen** zentral in `src/lib/permissions.ts` (Permission → Rollen). Navigation (`src/app/navigation.ts`) und Routen-Guards leiten sich daraus ab; das Backend bleibt maßgeblich.
- **Teilnehmerkontext:** `src/lib/participant.ts` bildet `roleLabel` deterministisch auf einen Farbton ab (OEM Indigo 277°, Tier-1 Teal 185°, Tier-2 Amber 70°, Tier-3..5 weitere; sonst Hash der BPNL). Der Farbton wird als `--participant-hue` auf `<html>` gesetzt; zusätzlich Tab-Titel-Präfix und eingefärbtes Favicon – so sind mehrere Instanzen nebeneinander unterscheidbar.
- **i18n:** keine sichtbaren Texte im Code; DE Standard (bzw. `defaultLocale` aus der Config), EN umschaltbar, Auswahl in `localStorage` (`ppap.language`). Theme in `localStorage` (`ppap.theme`).
- **Schrift:** Inter (selbst gehostet via `@fontsource-variable/inter`, kein CDN).

## Stammdaten (Phase 2)

- **DataTable** (`src/components/data-table/`): TanStack Table 8, Server-Modus (`page` = `Page<T>` der API) oder Client-Modus (`rows`, z. B. Katalog). Listenzustand (`q`, `active`, `page`, `size`, `sort=feld,asc`) liegt in den Router-Search-Params (`listSearchSchema`), Spaltenauswahl pro Tabelle in `localStorage`. Sticky Header, Skeleton-Zeilen, Leer-/Keine-Treffer-Zustand, Zeilenaktionen.
- **Formulare** (`src/components/forms/`): `FormSheet` (Drawer, react-hook-form + zod 4, Dirty-Guard, `VERSION_CONFLICT`/412 mit „Neu laden“) und Feldkomponenten. Zod-Meldungen sind Fehlercodes; Server-`errors[]` werden per `field` auf Felder gemappt, Texte über `errors:codes.<CODE>` (DE/EN). Formatregeln in `src/lib/validation.ts` (= Vertrag).
- **Import** (`features/masterdata/import/`): `ImportWizard` (Datei ≤ 10 MB → Zuordnung + Validierungsbericht → Ausführen/Fehlerbericht), generisch über Callbacks (`importApi(entity)`).
- **Katalog-Matrix** (`features/masterdata/catalog/LevelMatrix.tsx`): Elemente × Stufen, Roving-Tabindex mit Pfeiltasten, Tasten 1/2/3, Speichern je Stufe (`PUT /catalog/levels/{id}` ersetzt die Matrix).
- **Datenzugriff** (`features/masterdata/api/`): dünner Wrapper (`createResource`) über die generierten orval-Funktionen; Query-Keys = generierte Keys, `If-Match: "<version>"` aus `version` (orval `headers: true`), Invalidierung per URL-Präfix inkl. Audit. Import über `previewImport`/`executeImport`/`importErrorReport` (Multipart: `file` + Textteil `mapping` als JSON).
- **Änderungsverlauf**: `AuditTrail` (`GET /audit-events?objectType=PART|PARTNER|SITE|RELATION|PART_MAPPING&objectId=`) in den Drawern bzw. der Beziehungs-Detailseite.
- Schreibrechte: `masterdata:write` (MASTERDATA_MANAGER), Partner zusätzlich `partners:write` (CONNECTOR_ADMIN); ohne Recht read-only.
- TanStack Table bewusst **8.21** (stabile API); 9.x hat eine neue API und wird separat evaluiert.

## Design-Tokens

Alle Farben sind CSS-Variablen in `src/index.css` (`:root` = hell, `.dark` = dunkel) und über `@theme inline` als Tailwind-Farben verfügbar.

**Statusfarben** – je Status vier Tokens: `--status-<name>-bg`, `-fg`, `-border`, `-solid` (Punkt/Indikator). `fg` auf `bg` erfüllt ≥ 4,5:1 (WCAG AA) in beiden Themes.

| Token                     | Bedeutung           | Farbton                       |
| ------------------------- | ------------------- | ----------------------------- |
| `draft`                   | Entwurf             | Neutral                       |
| `requested`               | Angefordert         | Himmelblau                    |
| `in-progress`             | In Bearbeitung      | Indigo                        |
| `submitted`               | Eingereicht         | Cyan                          |
| `in-review`               | In Prüfung          | Violett                       |
| `approved`                | Freigegeben         | Grün                          |
| `conditionally-approved`  | Bedingt freigegeben | Amber                         |
| `rejected`                | Abgelehnt           | Rot                           |
| `cancelled`               | Abgebrochen         | Warmgrau                      |
| `up` / `down` / `unknown` | Komponentenstatus   | = approved / rejected / draft |

Verwendung ausschließlich über `<StatusBadge kind="approval" status="in-review" />` bzw. `<StatusBadge kind="health" status="UP" />`: Farbe + Icon + Text (nie Farbe allein), `role="status"` und `aria-label` „Status: …“.

**Teilnehmer-Akzent:** `--participant`, `--participant-foreground`, `--participant-soft`, `--participant-soft-foreground` (Tailwind: `bg-participant`, `text-participant-soft-foreground` …), berechnet aus `--participant-hue`.

## Bekannte Punkte / TODO

- `npm audit` meldet moderate Findings ausschließlich in der Dev-Abhängigkeit `shadcn` (CLI, transitiv `minimatch`); nicht im Bundle.
- 401 bei abgelaufenem Token: aktuell Fehlerbildschirm mit Neuanmeldung; später Retry nach Silent Renew.
- Live-Updates (SSE), TanStack Table, Formulare folgen mit den Fachphasen.
- Playwright-Smoke mit Keycloak-Login + axe-core (`e2e/smoke.spec.ts`, `test.fixme`).

## Freigabevorgänge (Phase 3, UC-03/05/07)

- **Feature** `src/features/approvals/`: `api/` (einziger Ort mit API-Wissen; Phase A handgeschrieben nach `docs/api/approval-api.md`, Phase B → orval), `model/` (reine Logik: Status-Tokens, `allowedActions` × Berechtigungen, Kanban-Gruppierung, Entscheidungsregeln), `schema-form/` (Formular-Renderer aus dem JSON-Schema-Snapshot: object, Array von Objekten als Tabelle, string/date/number/integer/boolean/enum, required/min/max/Länge/Pattern; Server-`VALUE_SCHEMA_VIOLATION` per JSON-Pointer), `list/` (Tabelle + read-only Board, Zustand in der URL), `detail/`, `request/` (Wizard „Vorlage anfordern“), `manual/`, `package/` (Arbeitsbereich Lieferant: Autosave mit If-Match-Warteschlange, Vollständigkeit, Excel-Import, Einreichen), `review/` (Prüfung, Entscheidung, Re-Login bei `RECENT_AUTH_REQUIRED` mit `max_age=0`).
- Berechtigungen `approvals:request|supply|decide`; Aktionen werden **immer** mit den serverseitigen `allowedActions` kombiniert.
- Dokumentvorschau: `react-pdf`/`pdfjs-dist` (lazy Chunk `pdf`), SHA-256 wird im Browser gegen den gespeicherten Hash geprüft.
- `src/lib/i18n-keys.test.ts` stellt sicher, dass DE und EN dieselben Schlüssel haben.
