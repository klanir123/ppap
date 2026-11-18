#!/usr/bin/env python3
"""Generates the Keycloak realm imports (one realm per dataspace participant, see ADR-0004).

Run after changing users/roles/clients:  python3 infra/keycloak/generate-realms.py
Local demo credentials only – never use these realms outside the local environment.
"""
import json
import pathlib

APP_ROLES = {
    "CUSTOMER_QUALITY_ENGINEER": "Qualitätsingenieur Kunde – fordert an, prüft, entscheidet",
    "SUPPLIER_QUALITY_MANAGER": "Qualitätsverantwortlicher Lieferant – erstellt und reicht PPF-Pakete ein",
    "MASTERDATA_MANAGER": "Stammdatenpfleger – Teile, Partner, Standorte, Elementkatalog",
    "CONNECTOR_ADMIN": "Connector-Administrator – EDC-Anbindung, Policies, Monitoring",
    "SYSTEM_INTEGRATION": "Technischer Client für ERP/QM-Anbindung",
}
DTR_CLIENT_ROLES = ["view_digital_twin", "add_digital_twin", "update_digital_twin", "delete_digital_twin",
                    "submodel_access_control", "read_access_rules", "write_access_rules"]
DEMO_PASSWORD = "demo"

PARTICIPANTS = {
    "oem": {
        "display": "Demo-OEM AG", "app_port": 8081, "dtr": False,
        "users": [
            ("oem.quality", "Qualitätsingenieur", "OEM", ["CUSTOMER_QUALITY_ENGINEER"]),
            ("oem.masterdata", "Stammdaten", "OEM", ["MASTERDATA_MANAGER"]),
            ("oem.admin", "Administrator", "OEM", list(APP_ROLES)[:4]),
        ],
    },
    "tier1": {
        "display": "Demo-Tier1 GmbH", "app_port": 8082, "dtr": True,
        "users": [
            ("tier1.quality", "Qualitätsverantwortlicher", "Tier-1", ["SUPPLIER_QUALITY_MANAGER", "CUSTOMER_QUALITY_ENGINEER"]),
            ("tier1.masterdata", "Stammdaten", "Tier-1", ["MASTERDATA_MANAGER"]),
            ("tier1.admin", "Administrator", "Tier-1", list(APP_ROLES)[:4]),
        ],
    },
    "tier2": {
        "display": "Demo-Tier2 KG", "app_port": 8083, "dtr": True,
        "users": [
            ("tier2.quality", "Qualitätsverantwortlicher", "Tier-2", ["SUPPLIER_QUALITY_MANAGER", "MASTERDATA_MANAGER"]),
            ("tier2.admin", "Administrator", "Tier-2", list(APP_ROLES)[:4]),
        ],
    },
}


def realm(name, p):
    clients = [
        {   # SPA, authorization code + PKCE
            "clientId": "ppap-ui", "name": "PPAP Connector UI", "publicClient": True, "protocol": "openid-connect",
            "standardFlowEnabled": True, "directAccessGrantsEnabled": False, "implicitFlowEnabled": False,
            "redirectUris": [f"http://localhost:{p['app_port']}/*", "http://localhost:5173/*"],
            "webOrigins": [f"http://localhost:{p['app_port']}", "http://localhost:5173"],
            "attributes": {"pkce.code.challenge.method": "S256", "post.logout.redirect.uris": "+"},
        },
        {   # ERP/QM integration (client credentials), secret is a local placeholder
            "clientId": "ppap-integration", "name": "ERP/QM-Integration", "publicClient": False,
            "secret": f"{name}-integration-local-secret", "serviceAccountsEnabled": True,
            "standardFlowEnabled": False, "directAccessGrantsEnabled": False, "protocol": "openid-connect",
        },
    ]
    users = [
        {
            "username": u, "enabled": True, "emailVerified": True, "firstName": first, "lastName": last,
            "email": f"{u}@{name}.example", "realmRoles": roles,
            "credentials": [{"type": "password", "value": DEMO_PASSWORD, "temporary": False}],
        }
        for (u, first, last, roles) in p["users"]
    ]
    users.append({"username": "service-account-ppap-integration", "enabled": True,
                  "serviceAccountClientId": "ppap-integration", "realmRoles": ["SYSTEM_INTEGRATION"]})
    client_roles = {}
    if p["dtr"]:
        # Resource client whose client roles the Digital Twin Registry checks (registry.idm.public-client-id)
        clients.append({"clientId": "ppap-dtr", "name": "Digital Twin Registry", "bearerOnly": True,
                        "publicClient": False, "standardFlowEnabled": False, "protocol": "openid-connect"})
        client_roles["ppap-dtr"] = [{"name": r} for r in DTR_CLIENT_ROLES]
        # Connector backend registers twins; EDC data plane reads them on behalf of consumers
        for cid, roles in (("ppap-backend-dtr", DTR_CLIENT_ROLES), ("ppap-edc-dtr", ["view_digital_twin"])):
            clients.append({"clientId": cid, "publicClient": False, "secret": f"{name}-{cid}-local-secret",
                            "serviceAccountsEnabled": True, "standardFlowEnabled": False,
                            "directAccessGrantsEnabled": False, "protocol": "openid-connect"})
            users.append({"username": f"service-account-{cid}", "enabled": True, "serviceAccountClientId": cid,
                          "clientRoles": {"ppap-dtr": roles}})
    return {
        "realm": name, "displayName": p["display"], "enabled": True,
        "sslRequired": "none", "registrationAllowed": False, "loginWithEmailAllowed": True,
        "internationalizationEnabled": True, "supportedLocales": ["de", "en"], "defaultLocale": "de",
        "accessTokenLifespan": 900, "ssoSessionIdleTimeout": 3600,
        "roles": {"realm": [{"name": r, "description": d} for r, d in APP_ROLES.items()], "client": client_roles},
        "clients": clients, "users": users,
    }


out = pathlib.Path(__file__).parent / "realms"
for n, p in PARTICIPANTS.items():
    (out / f"{n}-realm.json").write_text(json.dumps(realm(n, p), indent=2, ensure_ascii=False) + "\n")
    print(f"wrote {out / f'{n}-realm.json'}")
