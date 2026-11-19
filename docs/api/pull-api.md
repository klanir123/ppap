# Vertrag Pull über Digital Twin Registry und Dokumente (Phase 5)

Stand: Phase 5. Ergänzt [exchange-api.md](exchange-api.md); Architektur: ADR-0002. REST verbindlich über `api/openapi.json`.

## Ziele
1. **Paket per Pull** (Digital Twin KIT): Der Lieferant registriert je Freigabevorgang einen Digital Twin in seiner DTR; der Kunde findet ihn über den Katalog, sucht ihn per `specificAssetIds` und ruft das Submodel `PpapSubmission` über EDC ab.
2. **Dokumente per Pull** in **allen** Modi: Nachweise (Lieferant → Kunde) und Anforderungs-Anhänge (Kunde → Lieferant) werden über EDC abgerufen, per SHA-256 geprüft und im eigenen Object Storage abgelegt (danach Vorschau wie bei eigenen Dokumenten).
3. **Moduswahl je Beziehung** (`exchangeMode`) wirkt ohne Neustart: `PUSH`, `PULL`, `PUSH_NOTIFY_PULL_PAYLOAD` (Standard).
4. **Messwerte** für ADR-0002 (Push vs. Pull: EDC-Schritte, Latenz) als Beitrag für die Expert Group.

## Angebote (Provisionierung, zusätzlich zu Phase 4)

| Asset-ID | Anbieter | `dct:type` | DataAddress (HttpData) | Access Policy |
|---|---|---|---|---|
| `ppap-dtr` | Lieferant (hat DTR) | `cx-taxo:DigitalTwinRegistry`, `cx-common:version` `3.0` | DTR-Basis-URL, `proxyPath`, `proxyQueryParams`, `proxyMethod` = true (`proxyBody` false), OAuth2 Client Credentials (`oauth2:tokenUrl`, `oauth2:clientId` = `ppap-edc-dtr`, Secret im Vault) | Gruppe `ppap-partners` |
| `ppap-submission-<BPNL>` | Lieferant | `cx-taxo:Submodel` | `http://<p>-app:8080/internal/edc/submodels`, `proxyPath` = true, Shared Secret | `BusinessPartnerNumber eq <BPNL des Kunden>` |
| `ppap-documents-<BPNL>` | jeder Teilnehmer (je Partner) | `cx-taxo:PpapDocuments` (vorläufig, `TODO(EG)`) | `http://<p>-app:8080/internal/edc/documents`, `proxyPath` = true, Shared Secret | `BusinessPartnerNumber eq <BPNL des Partners>` |

Usage Policy wie Phase 4. Assets je Partner werden angelegt, sobald eine Beziehung mit Austauschbedarf besteht (Reconciliation). **Zusätzlich prüft das Backend jeden Abruf** anhand des von der Data Plane gesetzten `Edc-Bpn`: Ein Submodel bzw. Dokument wird nur ausgeliefert, wenn es zu einem Vorgang mit genau diesem Partner gehört (Dokument: Anhang einer gesendeten Anforderung bzw. Nachweis einer **eingereichten** Revision). Entwürfe sind nie abrufbar.

## Digital Twin (Lieferant)

- **Eine Shell je Freigabevorgang** (nicht je Teil): `id` / `globalAssetId` = `urn:uuid:<exchangeId>`, `idShort` = `ppap-<caseNumber>`.
- `specificAssetIds`: `customerPartId` (Kundensachnummer), `manufacturerPartId` (Lieferantensachnummer), `manufacturerId` (Lieferanten-BPNL), `ppapExchangeId` (= exchangeId); Sichtbarkeit `externalSubjectId` = Kunden-BPNL (nicht `PUBLIC_READABLE`).
- **Submodel-Deskriptor je eingereichter Revision**: `id` = `urn:uuid:<deterministisch aus exchangeId+revision>`, `semanticId` = `urn:samm:com.msg.ppap:0.1.0#PpapSubmission`, Endpoint `href` = `<eigene Data-Plane-Public-URL>/<exchangeId>/<revision>`, `subprotocol` = `DSP`, `subprotocolBody` = `id=ppap-submission-<Kunden-BPNL>;dspEndpoint=<eigene DSP-URL>`.
- Registrierung beim Einreichen (vor dem Versand der Notification, gleiche fachliche Transaktion über Outbox-Schritt), Pflege bei neuer Revision; Abbruch → Shell bleibt (Historie). Backend nutzt den Keycloak-Client `ppap-backend-dtr`.
- `TODO(EG)`: Alternative „Freigabe-Submodel am Industry-Core-Twin des Teils“ in ADR-0002 bewerten.

## Abläufe je Modus

| Modus | Einreichung | Dokumente |
|---|---|---|
| `PUSH` | `PPAP_SUBMISSION` mit vollständigem Payload (wie Phase 4) | Pull über `ppap-documents-<BPNL>` |
| `PUSH_NOTIFY_PULL_PAYLOAD` | Twin/Deskriptor registrieren → `PPAP_SUBMISSION_AVAILABLE { exchangeId, revision, payloadHash, shellId, submodelId }` → Kunde holt per Pull (DTR → Submodel) | Pull |
| `PULL` | Twin/Deskriptor registrieren, **keine** Notification; Kunde fragt periodisch (`PPAP_PULL_POLL_INTERVAL`, Default 60 s lokal) die DTR des Lieferanten für seine offenen Vorgänge ab (Lookup über `ppapExchangeId`) und holt neue Revisionen | Pull |

Anforderung, Rückzug, Statusmeldungen und Entscheidungen bleiben in allen Modi Notifications. Maßgeblich ist der Modus der Beziehung **beim Lieferanten** (er entscheidet, wie er bereitstellt); der Kunde kann alle Modi empfangen.

**Kundenseitiger Abruf (Pull-Job im Modul `twin`, Protokoll als `ExchangeRecord`):** Katalog des Lieferanten (Filter `dct:type = cx-taxo:DigitalTwinRegistry`) → EDR → `GET /lookup/shells?assetIds=[{name: ppapExchangeId, value}]` über Data Plane → Shell-Deskriptor → Submodel-Deskriptor der Revision → `subprotocolBody` auswerten → EDR für Submodel-Asset → `GET` Submodel → Hash (gleich `payloadHash` der Notification, falls vorhanden) → Signatur (Partner-JWKS) → `ReceiveSubmissionUseCase`. Danach je Dokument: EDR für `ppap-documents-<eigene BPNL>` beim Partner → `GET /<sha256>` → SHA-256 prüfen (`DOCUMENT_HASH_MISMATCH`) → eigenes S3. Retry/Backoff wie Outbox.

Neue Schritt-Typen im `ExchangeRecord`: `DTR_LOOKUP`, `SHELL_DESCRIPTOR`, `SUBMODEL_FETCH`, `HASH_CHECK`, `SIGNATURE_CHECK`, `DOCUMENT_FETCH`, `TWIN_REGISTRATION`; neue Nachrichtentypen/Record-Arten: `PPAP_SUBMISSION_AVAILABLE` (Notification), `PULL_SUBMISSION`, `PULL_DOCUMENT` (eingehende Abrufe), `TWIN_REGISTRATION` (ausgehend, lokal).

## REST-Ergänzungen
- Dokument-DTO (Referenz): `transferStatus: LOCAL | PENDING | FETCHED | FAILED | NOT_AVAILABLE`, `id` gesetzt, sobald lokal vorhanden; `hashVerified: boolean`.
- `POST /api/v1/approval-cases/{id}/documents/{sha256}/fetch` – Abruf erneut anstoßen (Kunde bzw. Lieferant für Anhänge).
- Vorgang: `exchangeMode` (wirksamer Modus der Beziehung zum Zeitpunkt der Einreichung je Revision: `submissions[].exchangeMode`).
- `GET /api/v1/twins/{exchangeId}` (Lieferant, `SUPPLIER_QUALITY_MANAGER`/`CONNECTOR_ADMIN`) – registrierte Shell inkl. Deskriptoren (Diagnose/Cockpit).
- `PUT /api/v1/relations/{id}` mit `exchangeMode` existiert; Wirkung sofort für die nächste Einreichung.

## Konfiguration (Env)
`PPAP_DTR_URL` (intern, Backend), `PPAP_DTR_PUBLIC_URL` (für Asset-DataAddress, aus Sicht der eigenen Data Plane), `PPAP_DTR_CLIENT_ID`/`SECRET` (Backend, `ppap-backend-dtr`), `PPAP_DTR_EDC_CLIENT_ID`/`SECRET` (für die DataAddress, `ppap-edc-dtr`), `PPAP_EDC_DATAPLANE_PUBLIC_URL` (eigene Data Plane, für Submodel-`href`), `PPAP_PULL_POLL_INTERVAL`.

## Ergänzungen (Abstimmung Frontend, Phase 5)
1. Dokument-Referenz: zusätzlich `transferError` (Fehlercode, nur bei `FAILED`).
2. `GET /exchange/messages` filtert zusätzlich nach `messageType` (mehrfach).
3. `POST …/documents/{sha256}/fetch` erwartet den reinen 64-stelligen Hex-Hash ohne Präfix `sha256:`.
4. `GET /twins/{exchangeId}` → `{ exchangeId, caseId, registeredAt, updatedAt, shell: <AAS-3.0-ShellDescriptor wie in der DTR registriert> }`, `404` ohne Twin.
5. Austauschschritte erhalten `target` (`DTR`, `SUBMODEL`, `DOCUMENT`, `NOTIFICATION`, `PUBLIC_KEYS`) und optional `targetRef` (z. B. SHA-256, Asset-ID): Ein Abruf durchläuft Katalog/Verhandlung/Transfer/EDR legitim mehrfach (je Asset); echte Wiederholungen erhöhen `attempt`.
