# Annahmen und offene Punkte

Stand: Phase 0. Alles, was von der Catena-X Expert Group „Product Approval“ abhängt, ist mit `TODO(EG)` markiert.
Status: **offen** = Freigabe durch Product Owner nötig · **gesetzt** = Entscheidung getroffen, änderbar.

## Eingangsmaterial

| ID | Annahme / Feststellung | Status |
|---|---|---|
| A-01 | `docs/input/` war zu Projektbeginn **leer**. Kickoff-Folien, Entscheidungsvorlage, Continental-PPF-Präsentation und VDA 231-300/-301 lagen **nicht** vor. Das Datenmodell basiert daher auf öffentlich zugänglichem Wissen über VDA Band 2 (PPF, 2020) und AIAG PPAP 4th Edition sowie auf begründeten Annahmen. Sobald die Unterlagen vorliegen, wird das SAMM-Modell abgeglichen. | offen |
| A-03 | **Korrektur zum Prompt:** Laut Recherche sind VDA 231-300 (Werkstoff-/Oberflächensemantik, JT-bezogen) und VDA 231-301 (Prüf- und Messergebnisse) **öffentliche JSON-Schemas unter MIT-Lizenz** – sie decken Teilinhalte des PPF ab (Werkstoff, Prüfergebnisse), nicht den gesamten Vorlage-/Freigabeprozess. Wir verwenden sie als Werte-Schemas für die betroffenen Elemente (ADR-0003); Kopf, Elementliste, Selbstbeurteilung und Entscheidung modellieren wir selbst nach VDA Band 2 (7. Aufl. 2025). Bitte bestätigen bzw. mit den Kickoff-Unterlagen abgleichen. | offen |
| A-04 | Zieltermin der Expert Group: Der öffentliche One-Pager (16.12.2025) nennt **Tractus-X 26.09** für Standard/KIT, der Prompt **27.03**. Bis heute (18.11.2026) ist nichts veröffentlicht. Wir planen mit 27.03. | offen |
| A-02 | Customs-Demonstrator analysiert (siehe `docs/analysis/customs-demonstrator.md`): EDC-Austausch dort nur als Stub, kein Import, keine Tests. Übernommen werden nur Architekturmuster (hexagonale Struktur, Use-Case-Ports, Schrittfolge des Notification-Versands), **kein Code**. Repo hat keine LICENSE-Datei. | gesetzt |

## Fachlich

| ID | Annahme | Status |
|---|---|---|
| A-10 | Begriffe: Wir verwenden im Code die englischen Begriffe aus PPAP (`ApprovalCase`, `Submission`, `Decision`), in der UI (DE) die VDA-Begriffe (Freigabevorgang, PPF-Vorlage, Bemusterung, Vorlagestufe). | gesetzt |
| A-11 | Der Seed-Elementkatalog orientiert sich an den Vorlageelementen nach VDA Band 2 (Deckblatt, Selbstbeurteilung, Prüfergebnisse Maß/Werkstoff/Funktion/Haptik/Akustik…, Prozessfähigkeit, Prozess-FMEA, Produktionslenkungsplan, Prüfmittelfähigkeit, IMDS, Teilehistorie, Software …) und an den 18 AIAG-PPAP-Elementen. Die genaue Nummerierung ist `TODO(EG)`/VDA-Abgleich. | gesetzt |
| A-12 | Vorlagestufen: VDA Stufe 0–3 (+ kundenspezifisch) und AIAG Level 1–5 werden beide als Daten angeboten; die Matrix „Element × Stufe“ ist Seed, nicht Code. | gesetzt |
| A-13 | Ein `ApprovalCase` bezieht sich auf **genau ein Teil** (Kundensachnummer) und eine Kunden-Lieferanten-Beziehung. Sammelvorlagen (Teilefamilien) sind nicht im MVP. `TODO(EG)` | offen |
| A-14 | Der Kunde schickt immer die **eigene** Kundensachnummer; die Zuordnung zur Lieferantensachnummer erfolgt beim Lieferanten über `PartMapping`. Optional sendet der Kunde die ihm bekannte Lieferantensachnummer mit. | gesetzt |
| A-15 | Der Status `IN_BEARBEITUNG` und `IN_PRÜFUNG` werden als optionale Statusmeldungen an den Partner übertragen (`PpapStatusUpdate`), damit beide Seiten denselben Stand sehen. Ob das standardisiert wird: `TODO(EG)`. | offen |
| A-16 | Die Entscheidung wird pro **Revision** getroffen. Eine neue Revision nach Ablehnung behält die Vorgangs-ID; Revisionsnummer zählt hoch. | gesetzt |
| A-17 | „Bedingte Freigabe“ beendet den Vorgang fachlich (Lieferung erlaubt); Auflagen werden als Liste mit Frist geführt. Der Nachweis der Auflagenerfüllung erfolgt als neue Revision. Keine eigenständige Auflagenverfolgung (Out of Scope). | offen |
| A-18 | Unterlieferanten-Referenzen (UC-09) enthalten die BPNL des Unterlieferanten im Klartext. Pseudonymisierung (aus Wettbewerbsgründen gewünscht?) `TODO(EG)`. | offen |
| A-19 | Digitale Bestätigung = angemeldeter Benutzer bestätigt explizit (mit optionaler Re-Authentisierung) + serverseitige JWS-Signatur mit **Mandantenschlüssel** (nicht personengebunden). Personengebundene Signaturen/qualifizierte e-Signatur sind nicht Ziel. Später: Signatur über Verifiable Credentials des Teilnehmers. | gesetzt |

## Technisch / Catena-X

| ID | Annahme | Status |
|---|---|---|
| A-20 | Es gibt noch keinen Catena-X-Standard für PPAP/PPF. Eigene vorläufige SAMM-Modelle im Namespace `urn:samm:com.msg.ppap:0.1.0#` (`PpapRequest`, `PpapSubmission`, `PpapDecision`, `PpapNotification`). Das Mapping liegt in einer austauschbaren Schicht (`semantics`-Modul, ADR-0003). `TODO(EG)` | gesetzt |
| A-21 | Notifications folgen strukturell dem Catena-X-Notification-Muster (Header/Content-Trennung wie bei Quality/Industry-Core-Notifications), aber mit eigenem Content-Modell. | gesetzt |
| A-21a | Vorläufige Catena-X-Kennungen bis zur Standardisierung (`TODO(EG)`): Asset-Typ `cx-taxo:PpapNotificationAPI`, UsagePurpose `msg.ppap.productApproval:1` (individueller Zweck nach CX-0152). | gesetzt |
| A-22 | Identität wird lokal mit dem Tractus-X **Wallet-Stub** (`ssi-dim-wallet-stub`, inkl. BPN-Directory statt separatem BDRS) simuliert; kein echter DIM/Managed Identity Wallet. Der Wallet-Stub ist **nicht produktionsgeeignet** – für das MVP ist die Anbindung an einen Wallet-Anbieter des Betreibers (DIM o. ä.) über Konfiguration vorgesehen. | gesetzt |
| A-23a | Postgres Row-Level-Security (ADR-0004, zweite Verteidigungslinie) ist noch **nicht** umgesetzt; Mandantentrennung erfolgt über `tenant_id` + `TenantContext` in allen Repositories und eine Instanz je Teilnehmer. RLS folgt vor Multi-Mandanten-Betrieb (L-03). | gesetzt |
| A-37 | **Abweichung zu Phase 0:** Backends hängen am Netz `dataspace` (Aufruf der Partner-Data-Plane als Konsument). Netzwerk-Isolation der Backends untereinander ist damit lokal nicht mehr erzwungen; Schutz über Shared Secret (Vault) an `/internal/edc/**` und Realm-Tokens an `/api/**`. Tractus-X-Consumer-Proxy bewusst nicht genutzt (laut EDC temporär). | gesetzt (vom Auftraggeber im Chat am 19.11.2026 bestätigt) |
| A-38 | Usage Purpose des PPAP-Austauschs: Default `cx.quality.base:1`; ein eigener Zweck (`msg.ppap.productApproval:1`) wird von EDC 0.13 akzeptiert und ist konfigurierbar (`EDC_USAGE_PURPOSE`). Endgültiger Zweck `TODO(EG)`. | gesetzt |
| A-39 | Einreichungen werden in Phase 4 vollständig per Push übertragen; Dokumente nur als Metadaten (Name, Typ, Größe, SHA-256). Der Abruf der Dokumente per Pull über EDC/DTR folgt in Phase 5. | gesetzt |
| A-40 | Pull-Modell: **eine Shell je Freigabevorgang** (nicht je Teil), Submodel-Deskriptor je Revision, Sichtbarkeit nur für den Kunden; im Modus Push wird kein Twin registriert. Alternative „Freigabe-Submodel am Industry-Core-Twin“ ist in ADR-0002 beschrieben. `TODO(EG)` | gesetzt |
| A-41 | `BinaryExchange` wird für Nachweisdokumente nicht verwendet (kein Hash, keine Größe, eine Datei je Aspekt); eigene Dokumentreferenzen mit SHA-256 (ADR-0003). `TODO(EG)` | gesetzt |
| A-34 | Demo-Altvorgänge (`source = LEGACY_IMPORT`, z. B. aus einem früheren Portal migriert) werden **ohne Signatur** geseedet und im UI als „Altdaten / nicht signiert“ gekennzeichnet; signierte Revisionen entstehen nur live. | gesetzt |
| A-35 | Eingegangene Revisionen mit unbekanntem Schlüssel (`kid`) des Partners erhalten Signaturstatus `UNVERIFIED` und dürfen geprüft werden; `INVALID` wird abgelehnt. Die Auflösung der Partnerschlüssel (JWKS/DID über EDC) folgt in Phase 4. `TODO(EG)`: Verfahren zur Schlüsselverteilung im Catena-X-Kontext. | gesetzt |
| A-36 | „Offene“ Vorgänge (Dashboard) = REQUESTED, IN_PROGRESS, SUBMITTED, IN_REVIEW, REJECTED; überfällig = offen und Termin überschritten. | gesetzt |
| A-23 | Je Teilnehmer läuft ein **eigenes** Connector-Backend (eigene DB) – der Datenraum wird ehrlich simuliert. Die Software ist trotzdem mandantenfähig vorbereitet (ADR-0004), damit ein Betreiber später mehrere kleine Zulieferer auf einer Instanz hosten kann. | gesetzt |
| A-24 | Der Notification-Empfangsendpunkt des Backends ist nur aus dem internen Netz der Data Plane erreichbar und zusätzlich per Shared Secret (Header, im EDC-Asset als `authKey/authCode` hinterlegt) geschützt. Die Absender-BPNL wird aus dem Payload-Header übernommen und gegen die Contract-Agreement-Information des EDC geprüft (siehe ADR-0002). | gesetzt |
| A-25 | Dokumente werden nicht in Notifications eingebettet, sondern immer per Pull über EDC übertragen (Binärdaten, Größe, Policies). | gesetzt |
| A-26 | Die DTR läuft beim Lieferanten. Da ein Tier-1 auch Lieferant ist, haben OEM **keine**, Tier-1 und Tier-2 **je eine** DTR. Ein OEM, der selbst Anhänge zur Anforderung bereitstellt (Zeichnung), nutzt im MVP direkte EDC-Assets ohne DTR. | offen |
| A-27 | Lokale Hostnamen in Docker Compose sind gleichzeitig die DSP-Endpoints (z. B. `http://oem-edc-cp:8084/api/v1/dsp`). TLS ist im lokalen Setup aus; in Zielumgebungen per Reverse-Proxy. | gesetzt |
| A-28 | Ressourcenbudget gemessen (Phase 1, Leerlauf): **Vollausbau mit Tier-2 = 4,4 GiB** (18 Container), Summe der Speicherlimits ≈ 13 GiB → passt auf 32-GB-Laptop. Messung unter Last folgt in Phase 7. | gesetzt |

## Produkt / Lizenz

| ID | Annahme | Status |
|---|---|---|
| A-30 | Zähleinheit „Freigabevorgang“ = erste Einreichung eines `ApprovalCase` im Monat; Revisionen werden separat gezählt (siehe UC-12). | offen |
| A-31 | Kommerzielles Produkt (Closed Source). Verwendete Bibliotheken müssen permissiv lizenziert sein (Apache-2.0, MIT, BSD, EPL-2.0 als Laufzeitabhängigkeit akzeptabel). Lizenzprüfung als CI-Schritt. AGPL-Komponenten nur als separat betriebene Infrastruktur, nicht eingebettet. | offen |
| A-33 | **Abweichung vom Prompt:** Statt MinIO wird lokal **SeaweedFS** (Apache-2.0) als S3-kompatibler Object Storage verwendet, da MinIO Community eingestellt ist (keine Images, Nachfolger proprietär). Der Code spricht ausschließlich die S3-API (AWS SDK v2), jeder S3-kompatible Speicher ist austauschbar – siehe ADR-0007. | gesetzt |
| A-32 | UI-Standard Deutsch, Code/Kommentare Englisch, Dokumentation Deutsch. | gesetzt |
