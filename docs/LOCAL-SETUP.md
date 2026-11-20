# Den PPAP Connector auf dem eigenen Notebook betreiben

Diese Anleitung bringt die komplette Demo-Umgebung auf ein Notebook: drei Teilnehmer (Demo-OEM AG, Demo-Tier1 GmbH, Demo-Tier2 KG) mit je eigenem Tractus-X-Connector, dazu Digital Twin Registry, Keycloak, Datenbank und Dokumentenspeicher. Insgesamt laufen rund 20 Container.

## 1. Was das Notebook braucht

| | Minimum | Empfohlen |
|---|---|---|
| Arbeitsspeicher | 16 GB, davon **8 GB für Docker** | 32 GB, 10–12 GB für Docker |
| Freier Plattenplatz | 15 GB | 25 GB |
| CPU | 4 Kerne | 8 Kerne |
| Betriebssystem | macOS 13+ (Intel oder Apple Silicon), Windows 10/11 mit WSL2, Linux | |

Gemessen: Alle drei Teilnehmer zusammen belegen im Betrieb etwa 5 GB RAM. Mehr wird beim ersten Bauen (Java, Node) gebraucht.

**Software:**

| Werkzeug | Version | Wofür |
|---|---|---|
| Docker Desktop (macOS/Windows) bzw. Docker Engine + Compose-Plugin (Linux) | aktuell, Compose v2 | alle Container |
| Git | aktuell | Repository holen |
| make | beliebig | Kommandos (`make up` …) |
| Java JDK | **25** (Temurin empfohlen) | Backend bauen |
| Node.js | **22** | Frontend bauen |
| Python | 3.10+ (nur Standardbibliothek) | optional: `make smoke` |

## 2. Einrichtung je Betriebssystem

### macOS

1. **Docker Desktop** installieren → *Settings → Resources*: Memory mindestens 8 GB.
   Auf **Apple Silicon (M1–M4)** zusätzlich *Settings → General → „Use Rosetta for x86_64/amd64 emulation“* einschalten. Die Digital Twin Registry gibt es nur für Intel und läuft darüber; alle anderen Komponenten laufen nativ.
2. **Command Line Tools** (bringen `make` und `git`): `xcode-select --install`
3. **Java 25**: Temurin 25 von https://adoptium.net installieren (oder per SDKMAN, siehe unten). Danach in `~/.zshrc`:
   ```bash
   export JAVA_HOME=$(/usr/libexec/java_home -v 25)
   ```
4. **Node 22**: z. B. über `nvm` (`nvm install 22`) oder von https://nodejs.org.

### Windows (über WSL2)

Die Umgebung läuft unter Windows **in WSL2** (Ubuntu). So funktionieren `make`, die Shell-Skripte und die Pfade wie unter Linux.

1. PowerShell als Administrator: `wsl --install -d Ubuntu-24.04`, danach neu starten und in Ubuntu einen Benutzer anlegen.
2. **Docker Desktop** installieren → *Settings → Resources → WSL Integration*: Ubuntu aktivieren. Unter *Resources* mindestens 8 GB Speicher zuweisen (bei WSL2-Backend über `%UserProfile%\.wslconfig`, z. B. `memory=10GB`).
3. In der **Ubuntu-Shell** (nicht in PowerShell):
   ```bash
   sudo apt update && sudo apt install -y make git python3 curl zip unzip
   ```
4. Java 25 und Node 22 in Ubuntu installieren, am einfachsten mit SDKMAN und nvm (siehe unten).
5. **Wichtig:** Das Repository im Linux-Dateisystem klonen (z. B. `~/projekte/ppap`), **nicht** unter `/mnt/c/…`. Das ist deutlich schneller und vermeidet Probleme mit Zeilenenden.

### Linux

Docker Engine mit Compose-Plugin installieren (https://docs.docker.com/engine/install/), den eigenen Benutzer in die Gruppe `docker` aufnehmen, dann `make`, `git`, `python3` aus dem Paketmanager. Java 25 und Node 22 wie unten.

### Java 25 und Node 22 mit SDKMAN und nvm (macOS, Linux, WSL)

```bash
curl -s "https://get.sdkman.io" | bash && source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk list java | grep -- '-tem' | grep ' 25'     # passende Temurin-25-Version heraussuchen
sdk install java <25.x.y-tem>
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/master/install.sh | bash && source ~/.nvm/nvm.sh
nvm install 22
```

Prüfen:

```bash
docker compose version && java -version && node -v && make -v | head -1
```

`java -version` muss **25** zeigen. Zeigt es eine ältere Version, `JAVA_HOME` auf das JDK 25 setzen oder `make … JDK25_HOME=/pfad/zum/jdk-25` übergeben.

### Im Firmennetz (Proxy, TLS-Inspection)

Läuft der Internetverkehr über einen Proxy bzw. Zscaler o. Ä.:
- Docker Desktop: *Settings → Resources → Proxies* setzen; ggf. das Firmen-Root-Zertifikat im Betriebssystem vertrauen.
- Maven (Backend-Build): Proxy in `~/.m2/settings.xml` eintragen; das Firmen-Zertifikat in den Truststore des JDK 25 importieren (`keytool -importcert -cacerts -alias firma -file firma-root.crt`).
- npm (Frontend-Build): `npm config set proxy …`, `npm config set https-proxy …`, ggf. `npm config set cafile firma-root.crt`.

## 3. Repository holen

Das Repository ist privat. Mit einem GitHub-Konto, das Zugriff hat:

```bash
git clone https://github.com/klanir123/ppap.git && cd ppap
```

(Bei HTTPS fragt Git nach Benutzer und einem Personal Access Token; alternativ `gh auth login` mit der GitHub CLI.)

## 4. Erster Start

```bash
make up PROFILE=tier2
```

Was passiert:
1. Frontend und Backend werden gebaut (`npm ci`, `./mvnw package`), danach das Container-Image `ppap-connector:local`.
2. Alle Container werden gestartet: Postgres, Keycloak, Dokumentenspeicher, Identitätsdienst (Wallet-Stub), je Teilnehmer Connector (Control und Data Plane), Secret Store und App, für die Lieferanten zusätzlich die Digital Twin Registry.
3. Beim Start legt jede App ihre Angebote im eigenen Connector an und lädt die Demo-Daten (Teile, Partner, Elementkatalog, Beispielvorgänge).
4. `make` wartet, bis alle drei Apps bereit sind.

Der **erste Start dauert 15–25 Minuten**, weil rund 6 GB Images und die Build-Abhängigkeiten heruntergeladen werden. Ab dann:

```bash
make up SKIP_BUILD=1 PROFILE=tier2
```

startet in wenigen Minuten ohne Neubau.

Kontrolle:

```bash
make ps
```

```bash
make smoke PROFILE=tier2
```

`make smoke` prüft den Datenaustausch über die echten Connectoren (Katalog, Vertragsverhandlung, Zugangsschlüssel, Datenabruf) und meldet zweimal `SMOKE TEST PASSED`.

> Ohne Tier-2 (weniger Speicher): `make up`. Dann fehlen die Kaskade und die Tier-2-Beispiele.

## 5. Anmelden

| Teilnehmer | Adresse | Benutzer | Rollen |
|---|---|---|---|
| Demo-OEM AG | http://localhost:8081 | `oem.quality` | Qualitätsingenieur Kunde |
| | | `oem.masterdata` | Stammdaten |
| | | `oem.admin` | alles inkl. Connector-Cockpit, Policy-Profile, Nutzung |
| Demo-Tier1 GmbH | http://localhost:8082 | `tier1.quality` | Lieferant **und** Kunde (Kaskade) |
| | | `tier1.masterdata`, `tier1.admin` | |
| Demo-Tier2 KG | http://localhost:8083 | `tier2.quality` | Lieferant, Stammdaten |
| | | `tier2.admin` | |

Passwort für alle: **`demo`**. Keycloak-Verwaltung: http://localhost:8180 (`admin` / `admin`).

Tipps:
- Immer `localhost` verwenden, nicht `127.0.0.1`. Die Anmeldung ist auf `localhost` konfiguriert.
- Für die Rundgänge OEM, Tier-1 und Tier-2 in **getrennten Fenstern** nebeneinander öffnen. Die Anmeldung gilt je Browser-Tab.
- Oben rechts lassen sich Sprache (DE/EN) und Hell/Dunkel umschalten.

## 6. Rundgang: alle Funktionen selbst ausprobieren

**A. Freigabe über Catena-X (ca. 10 Minuten)**
1. **OEM** (`oem.quality`): *Freigabevorgänge → Vorlage anfordern* → ein Teil mit Lieferant *Demo-Tier1 GmbH* wählen → Grund, Stufe, Elemente, Termin → **Anfordern**.
2. **Tier-1** (`tier1.quality`): Die Anforderung erscheint live mit Hinweis. Öffnen → **Bearbeitung starten** → **PPF-Paket erstellen**. Je Element Selbstbeurteilung setzen, Werte ausfüllen, Nachweise hochladen (Beispiel-PDFs liegen im Repo unter `seed/documents/`) → **Einreichen** und bestätigen.
3. **OEM**: Der Vorgang steht auf *Eingereicht*, die Signatur ist gültig, die Dokumente sind abgerufen und geprüft (Vorschau öffnen) → **Prüfung starten** → Prüfergebnisse je Element → **Entscheidung** (z. B. Ablehnung mit Begründung) bestätigen.
4. **Tier-1**: Die Entscheidung ist da → *Neue Revision anlegen*.
5. In beiden Vorgängen der Tab **Datenaustausch**: Jede Nachricht mit allen Connector-Schritten.

**B. Kaskade über Tier-2 (ca. 10 Minuten)**
1. In einem Tier-1-Vorgang, der vom OEM kommt: **Unterlieferanten-Vorlage anfordern** → Unterteil von *Demo-Tier2 KG*.
2. **Tier-2** (`tier2.quality`): bearbeiten und einreichen. Schneller geht es mit *Excel-Formblatt importieren* und `seed/forms/ppf-formblatt-v1-beispiel-tier2.xlsx` beim bereits vorhandenen Tier-2-Beispielvorgang.
3. **Tier-1**: Tier-2-Paket prüfen und freigeben. Im eigenen Paket an den OEM unter *Unterlieferanten-Freigaben* referenzieren und einreichen.
4. **OEM**: In der Prüfansicht steht die Unterlieferanten-Freigabe (Ergebnis, Datum, Hash). Dokumente von Tier-2 werden nicht weitergegeben.

**C. Weitere Funktionen**
- **Austauschmodus** (Tier-1 → *Stammdaten → Beziehungen → Demo-OEM AG*): Push, Pull oder Benachrichtigung + Pull. Gilt ab der nächsten Einreichung; im Vorgang zeigt ein Badge den verwendeten Modus, bei Pull der Tab *Digital Twin*.
- **Stammdaten**: Teile, Partner, Standorte, Beziehungen, Elementkatalog mit Stufenmatrix; *Importieren* mit CSV/Excel.
- **Connector-Cockpit, Policy-Profile, Nutzung** (als `*.admin`): Status, Angebote, Live-Katalog eines Partners, Live-Log, Policies je Beziehung, Zähler je Monat.
- **Datenraum** (am besten bei Tier-1): animierte Ansicht des Nachrichtenflusses; *Präsentationsmodus* für Bildschirm/Beamer, *Replay* zeigt die letzten 15 Minuten.
- **Audit**: In jedem Vorgang *Audit exportieren* (signiertes JSON).

## 7. Im Alltag

| Aufgabe | Kommando |
|---|---|
| Stoppen (Daten bleiben erhalten) | `make down` |
| Wieder starten ohne Neubau | `make up SKIP_BUILD=1 PROFILE=tier2` |
| Demo-Daten zurücksetzen | `make demo PROFILE=tier2` |
| Nach `git pull` (neue Version) | `make up PROFILE=tier2` (baut neu) |
| Status / Logs / Speicher | `make ps` · `make logs SERVICE=tier1-app` · `make stats` |
| Alles löschen (inkl. Daten, Connector-Zustand) | `make clean` |

Im Pull-Modus fragt der Kunde die Registry des Lieferanten standardmäßig alle 60 Sekunden ab. Für Vorführungen: `APP_PULL_POLL_INTERVAL=15s make up SKIP_BUILD=1 PROFILE=tier2`.

## 8. Fehlerbehebung

| Symptom | Ursache und Abhilfe |
|---|---|
| `port is already allocated` beim Start | Ein anderer Dienst nutzt einen Port (häufig 5432 durch ein lokales Postgres oder 8081). `infra/.env.example` nach `infra/.env` kopieren und den Port ändern, z. B. `POSTGRES_HOST_PORT=15432`. Belegt werden 8081–8083, 8090, 8180, 8191, 8291, 8391, 8333, 5432. |
| Build bricht mit Hinweis auf **Java 25** (Enforcer) ab | `java -version` zeigt eine ältere Version → `JAVA_HOME` auf JDK 25 setzen oder `make up PROFILE=tier2 JDK25_HOME=/pfad/jdk-25`. |
| `npm ci` oder Maven-Downloads scheitern | Proxy/Zertifikat im Firmennetz, siehe Abschnitt 2. |
| Container starten neu oder sind `Exited (137)` | Docker hat zu wenig Speicher → in Docker Desktop mehr RAM zuweisen (mind. 8 GB) und `make up SKIP_BUILD=1 PROFILE=tier2`. |
| Digital Twin Registry startet auf dem Mac nicht | Rosetta-Emulation in Docker Desktop einschalten (Abschnitt 2, macOS). |
| `waiting for tier1-app … ✗` | Logs ansehen: `make logs SERVICE=tier1-app`. Beim allerersten Start kann ein Connector länger brauchen. Einfach `make up SKIP_BUILD=1 PROFILE=tier2` erneut ausführen. |
| Anmeldung springt zurück zur Login-Seite | `localhost` statt `127.0.0.1` verwenden; Browser-Erweiterungen, die Cookies blocken, für localhost deaktivieren. |
| Nachrichten bleiben auf „Wird zugestellt“ | Im Connector-Cockpit (als `*.admin`) die Nachricht öffnen, Fehler lesen, *Erneut senden*. Hilft das nicht: `make smoke PROFILE=tier2` prüft die Connectoren. |
| Nach vielen Testläufen ist die Demo unübersichtlich | `make demo PROFILE=tier2` setzt die Anwendungsdaten auf den Ausgangsstand. |
| Docker-Speicher voll | `docker system prune` (entfernt ungenutzte Images und Caches). |

Alle Zugangsdaten in dieser Umgebung sind **lokale Demo-Werte** und dürfen nirgends sonst verwendet werden.
