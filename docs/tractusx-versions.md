# Catena-X / Tractus-X: Versionen und Deployment-Grundlagen

Recherchiert am **2026-11-18** aus Primärquellen (Git-Tags und Quellcode der Release-Tags, Docker Hub `tractusx/*`, Tractus-X-Release-CHANGELOG, docs-kits, catenax-ev.github.io). Daten = Tag-Commit- bzw. Image-Push-Datum (die GitHub-Release-API war aus der Sitzung nicht nutzbar).

## Komponenten

| Komponente | Gewählte Version | Datum | Image | Bemerkung |
|---|---|---|---|---|
| Tractus-X Release | **26.09** | 2026-09-16 | – | enthält EDC 0.13.0, DTR 0.13.0 |
| Tractus-X EDC | **0.13.0** (Fallback **0.12.3**) | 2026-08-27 | `tractusx/edc-controlplane-postgresql-hashicorp-vault:0.13.0`, `tractusx/edc-dataplane-hashicorp-vault:0.13.0` | Upstream EDC 0.17.0; Management API **v3**; DSP **2025-1** (+ Legacy 0.8) |
| Digital Twin Registry | **0.13.0** | 2026-09-01 | `tractusx/sldt-digital-twin-registry:0.13.0` | AAS API 3.1 (Digital Twin KIT normativ 3.0) |
| Wallet-Stub | **0.1.0** | 2026-06-30 | `tractusx/ssi-dim-wallet-stub:0.1.0` | nur Test/lokal; STS, DCP-Credential-Service, DID-Dokumente, BPN-Directory |
| BDRS | 0.6.0 (optional) | 2026-04-23 | `tractusx/bdrs-server-memory:0.6.0` | wird durch das BPN-Directory des Wallet-Stubs ersetzt (`<stub>/api/v1/directory`) |
| Umbrella (Referenz) | umbrella-26.06.00 | 2026-11-18 | Helm | nutzt Connector-Chart 0.12.0 + Wallet-Stub – beste Vorlage für unsere Konfiguration |

**Es gibt kein offizielles docker-compose-Setup** für Tractus-X EDC + Wallet-Stub (Umbrella = Helm/Minikube, `tutorial-resources/mxd` = KinD/Terraform, veraltet). Unser Compose-Setup wird aus den Images und den Werten des Umbrella-Charts abgeleitet (Phase 1, E0-03/E0-04).

## Wichtige Fakten für die Umsetzung

### EDC 0.13
- Participant-ID ist jetzt die **DID** (`did:web:…`), BPNL separat (`participant.bpnl`); `EDC_PARTICIPANT_CONTEXT_ID` Pflicht (UUID).
- Identität über **DCP** (ehem. IATP) mit Remote-STS. Konfig-Keys (0.13, umbenannt!): `edc.iam.sts.oauth.token.url`, `edc.iam.sts.oauth.client.id`, `edc.iam.sts.oauth.client.secret.alias`, `tx.edc.iam.sts.div.url` (früher `…dim.url`), `tx.edc.iam.dcp.credentialservice.url`, `tx.edc.iam.dcp.bdrs.server.url` (früher `…iatp…`).
- BDRS-Client ist fest eingebaut, URL Pflicht.
- Management API v3: `/v3/assets`, `/v3/policydefinitions`, `/v3/contractdefinitions`, `/v3/catalog/request`, `/v3/contractnegotiations`, `/v3/transferprocesses`, **`/v3/edrs`** (in 0.12: `/v2/edrs`), `/v3/connectordiscovery`.
- DSP-Endpoint: `…/api/v1/dsp/2025-1`, Protokoll `dataspace-protocol-http:2025-1`.
- Federated Catalog entfernt; Postgres 18.
- **Spike Phase 1 (18.11.2026): erfolgreich.** EDC 0.13.0 + Wallet-Stub 0.1.0 funktionieren mit den neuen Keys (`tx.edc.iam.sts.div.url`, `tx.edc.iam.dcp.*`) und DID als Participant-ID. Katalog → Negotiation → EDR → Data-Plane-Abruf OEM ↔ Tier-1 in < 10 s (`make smoke`, `infra/scripts/edc-smoke.py`). Fallback 0.12.3 nicht nötig.
- Das BPN-Directory des Wallet-Stubs ersetzt den BDRS (`TX_EDC_IAM_DCP_BDRS_SERVER_URL=http://wallet-stub/api/v1/directory`).
- DTR 0.13.0 benötigt zusätzlich `REGISTRY_IDM_OWNING_TENANT_ID` (BPNL des Betreibers).
- Wallet-Stub: DID-Auflösung funktioniert laut Doku nur auf **Port 80** → Stub im Compose-Netz auf Container-Port 80 betreiben (`DID_HOST=wallet-stub`).

### Notifications (Industry Core KIT / CX-0125)
- Ein Asset je Notification-API, POST-only, `HttpData` mit `proxyMethod`, `proxyPath`, `proxyBody` = true; `dct:type` = `cx-taxo:<ApiName>`, `cx-common:version`.
- Sender: `POST /v3/edrs` (Negotiation + Transfer in einem Schritt) → `GET /v3/edrs/{transferProcessId}/dataaddress?auto_refresh=true` → `POST <provider-dataplane>/api/public/<path>` mit `Authorization: <token>`.
- Message-Header: `urn:samm:io.catenax.shared.message_header:3.0.0#MessageHeaderAspect` (messageId, context, sentDateTime, senderBpn, receiverBpn, expectedResponseBy, version) → **wird für `PpapNotification` wiederverwendet.**

### DTR / Submodels (Digital Twin KIT)
- DTR-Asset: `dct:type` = `cx-taxo:DigitalTwinRegistry`, `proxyMethod=false`, Access-Control über Header `Edc-Bpn` + `externalSubjectId` an `specificAssetIds` (`PUBLIC_READABLE` als Wildcard); optional granular access control (`REGISTRY_USE_GRANULAR_ACCESS_CONTROL=true`).
- Submodel-Assets: `cx-taxo:Submodel`; Submodel-Deskriptor-Endpoint mit `subprotocolBody` = `id=<assetId>;dspEndpoint=<cp>/api/v1/dsp`.
- DTR-Auth: OAuth2 (Keycloak) oder `local`-Profil ohne Auth.

### Policies (CX-0152 v1.0.0, CX-Titan)
- Namespace `https://w3id.org/catenax/2025/9/policy/`; JSON-LD-Kontexte `https://w3id.org/dspace/2025/1/odrl-profile.jsonld` + `https://w3id.org/catenax/2025/9/policy/context.jsonld`.
- Jede Usage Policy: `FrameworkAgreement eq "DataExchangeGovernance:1.0"` + mindestens ein `UsagePurpose isAnyOf [...]` (z. B. `cx.core.digitalTwinRegistry:1`; für PPAP vorläufig individueller Zweck, z. B. `msg.ppap.productApproval:1` – `TODO(EG)`). Membership wird bei jedem Angebot geprüft.
- Access Policy: `BusinessPartnerNumber` bzw. `BusinessPartnerGroup` (Gruppen-API `/v3/business-partner-groups`).

### Semantische Modelle (sldt-semantic-models, main, 2026-11-17)
- `urn:samm:io.catenax.industry_core.part_type:2.0.0#PartType` (ersetzt `part_type_information:2.0.0`, deprecated)
- `urn:samm:io.catenax.industry_core.single_level_bom_as_planned:4.0.0#SingleLevelBomAsPlanned`
- `urn:samm:io.catenax.binary_exchange:1.0.0#BinaryExchange` – Dateimetadaten + DSP-Zugriff → **Kandidat für Nachweisdokumente**
- `urn:samm:io.catenax.digital_engineering_master_data:1.0.0#DigitalEngineeringMasterData`
- `urn:samm:io.catenax.requirement:1.0.0#Requirement`, `urn:samm:io.catenax.quality_task_attachment:3.0.0`
- Kein PPAP/PPF-/Approval-Modell vorhanden.

### Expert Group „Product Approval“ und VDA 231-300/-301
- Expert Group im Committee Supply Chain & Quality (One-Pager 16.12.2025): Standard auf Basis VDA 231-300/-301, Aspektmodelle und Tractus-X-KIT, Ziel laut One-Pager **26.09** (Prompt nennt 27.03). Bisher nichts veröffentlicht.
- **VDA 231-300**: Werkstoff- und Oberflächensemantik (verknüpft mit JT-3D-Daten). **VDA 231-301**: Prüf- und Messergebnisse. Beide als **JSON Schema** (generischer Kern + 8 Subschemata), **MIT-Lizenz**, öffentlich auf GitHub, Validator unter https://vda231-301.github.io/. VDA Band 2, 7. Auflage 2025 integriert sie.

## Quellen
- https://github.com/eclipse-tractusx/tractusx-edc/releases/tag/0.13.0
- https://github.com/eclipse-tractusx/tractus-x-release/blob/26.09/CHANGELOG.md
- https://github.com/eclipse-tractusx/sldt-digital-twin-registry/releases/tag/v0.13.0
- https://github.com/eclipse-tractusx/ssi-dim-wallet-stub
- https://github.com/eclipse-tractusx/bpn-did-resolution-service/releases/tag/0.6.0
- https://github.com/eclipse-tractusx/tractus-x-umbrella
- https://eclipse-tractusx.github.io/docs-kits/kits/industry-core-kit/software-development-view/notifications
- https://eclipse-tractusx.github.io/docs-kits/kits/digital-twin-kit/software-development-view/
- https://catenax-ev.github.io/docs/standards/CX-0125-TraceabilityUseCase
- https://catenax-ev.github.io/docs/standards/CX-0152-PolicyConstrainsForDataExchange/Appendix
- https://catenax-ev.github.io/blog-releasenotes/cx-titan
- https://catena-x.net/wp-content/uploads/2025/12/251216_Expert_Group_Product_Approval_OnePager.pdf
- https://vda231-301.github.io/
