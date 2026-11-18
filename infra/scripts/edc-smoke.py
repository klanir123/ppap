#!/usr/bin/env python3
"""End-to-end smoke test of the local dataspace (no third-party dependencies).

Provider offers an HttpData asset with Catena-X policies (CX-0152, 2025/9 namespace); the consumer
requests the provider's catalog over DSP 2025-1, negotiates via /v3/edrs, obtains the EDR and pulls
the data through the provider's data plane. Exits non-zero on the first failing step.

Usage: infra/scripts/edc-smoke.py [--provider tier1] [--consumer oem]
"""
import argparse
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

EDC_NS = "https://w3id.org/edc/v0.0.1/ns/"
ODRL_PROFILE_CTX = "https://w3id.org/dspace/2025/1/odrl-profile.jsonld"
CX_POLICY_CTX = "https://w3id.org/catenax/2025/9/policy/context.jsonld"
DSP_PROTOCOL = "dataspace-protocol-http:2025-1"

PARTICIPANTS = {
    "oem": {"bpnl": os.getenv("OEM_BPNL", "BPNL00000000OEM1"), "mgmt": "http://localhost:8191", "cp": "oem-edc-cp"},
    "tier1": {"bpnl": os.getenv("TIER1_BPNL", "BPNL000000TIER01"), "mgmt": "http://localhost:8291", "cp": "tier1-edc-cp"},
    "tier2": {"bpnl": os.getenv("TIER2_BPNL", "BPNL000000TIER02"), "mgmt": "http://localhost:8391", "cp": "tier2-edc-cp"},
}
API_KEY = os.getenv("EDC_MANAGEMENT_API_KEY", "local-management-key")


def call(base, method, path, body=None, expect=(200, 204)):
    req = urllib.request.Request(base + "/management" + path, method=method,
                                 data=json.dumps(body).encode() if body is not None else None,
                                 headers={"X-Api-Key": API_KEY, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read()
            return resp.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        payload = e.read().decode()
        if e.code == 409:  # already exists -> idempotent re-run
            return 409, None
        if e.code not in expect:
            sys.exit(f"FAIL {method} {path}: HTTP {e.code} {payload[:800]}")
        return e.code, payload


def step(msg):
    print(f"\n==> {msg}", flush=True)


def provision(provider, consumer_bpnl, asset_id):
    base = provider["mgmt"]
    step(f"provider: create asset {asset_id}")
    call(base, "POST", "/v3/assets", {
        "@context": {"@vocab": EDC_NS},
        "@id": asset_id,
        "properties": {"description": "PPAP connector smoke-test asset"},
        "dataAddress": {"@type": "DataAddress", "type": "HttpData",
                        # any URL reachable from the provider data plane; the DID document is always there
                        "baseUrl": f"http://wallet-stub/{provider['bpnl']}/did.json"},
    })
    step("provider: create access policy (BPN of consumer) and usage policy (FrameworkAgreement + UsagePurpose)")
    ctx = [ODRL_PROFILE_CTX, CX_POLICY_CTX, {"@vocab": EDC_NS}]
    call(base, "POST", "/v3/policydefinitions", {
        "@context": ctx, "@type": "PolicyDefinition", "@id": "smoke-access",
        "policy": {"@type": "Set", "permission": [{
            "action": "access",
            "constraint": [{"leftOperand": "BusinessPartnerNumber", "operator": "isAnyOf",
                            "rightOperand": [consumer_bpnl]}]}]},
    })
    call(base, "POST", "/v3/policydefinitions", {
        "@context": ctx, "@type": "PolicyDefinition", "@id": "smoke-usage",
        "policy": {"@type": "Set", "permission": [{
            "action": "use",
            "constraint": {"and": [
                {"leftOperand": "FrameworkAgreement", "operator": "eq", "rightOperand": "DataExchangeGovernance:1.0"},
                {"leftOperand": "UsagePurpose", "operator": "isAnyOf", "rightOperand": ["cx.core.industrycore:1"]},
            ]}}]},
    })
    step("provider: create contract definition")
    call(base, "POST", "/v3/contractdefinitions", {
        "@context": {"@vocab": EDC_NS}, "@id": "smoke-cd",
        "accessPolicyId": "smoke-access", "contractPolicyId": "smoke-usage",
        "assetsSelector": [{"operandLeft": EDC_NS + "id", "operator": "=", "operandRight": asset_id}],
    })


def run(provider_name, consumer_name):
    provider, consumer = PARTICIPANTS[provider_name], PARTICIPANTS[consumer_name]
    asset_id = f"smoke-asset-{provider_name}"
    provision(provider, consumer["bpnl"], asset_id)

    dsp = f"http://{provider['cp']}:8084/api/v1/dsp/2025-1"
    provider_did = f"did:web:wallet-stub:{provider['bpnl']}"

    step(f"consumer: request catalog from {provider_name} via DSP ({dsp})")
    t0 = time.time()
    _, catalog = call(consumer["mgmt"], "POST", "/v3/catalog/request", {
        "@context": {"@vocab": EDC_NS}, "@type": "CatalogRequest",
        "counterPartyAddress": dsp, "counterPartyId": provider_did, "protocol": DSP_PROTOCOL,
        "querySpec": {"filterExpression": [{"operandLeft": EDC_NS + "id", "operator": "=", "operandRight": asset_id}]},
    })
    datasets = catalog.get("dataset", catalog.get("dcat:dataset", []))
    datasets = datasets if isinstance(datasets, list) else [datasets]
    if not datasets:
        sys.exit(f"FAIL: asset not in catalog: {json.dumps(catalog)[:800]}")
    offer = datasets[0].get("hasPolicy", datasets[0].get("odrl:hasPolicy"))
    offer = offer[0] if isinstance(offer, list) else offer
    print(f"    catalog OK in {time.time() - t0:.1f}s, offer id {offer.get('@id')}")

    step("consumer: negotiate contract + start transfer (POST /v3/edrs)")
    policy = dict(offer)
    policy["assigner"] = provider_did
    policy["target"] = asset_id
    policy.pop("@context", None)
    _, created = call(consumer["mgmt"], "POST", "/v3/edrs", {
        "@context": [ODRL_PROFILE_CTX, CX_POLICY_CTX, {"@vocab": EDC_NS}], "@type": "ContractRequest",
        "counterPartyAddress": dsp, "protocol": DSP_PROTOCOL, "policy": policy,
    })
    print(f"    negotiation {created.get('@id')}")

    step("consumer: wait for EDR")
    tp_id = None
    for _ in range(60):
        _, edrs = call(consumer["mgmt"], "POST", "/v3/edrs/request", {
            "@context": {"@vocab": EDC_NS}, "@type": "QuerySpec",
            "filterExpression": [{"operandLeft": "assetId", "operator": "=", "operandRight": asset_id}]})
        if edrs:
            tp_id = edrs[-1].get("transferProcessId")
            break
        time.sleep(1)
    if not tp_id:
        _, neg = call(consumer["mgmt"], "GET", f"/v3/contractnegotiations/{created.get('@id')}")
        sys.exit(f"FAIL: no EDR after 60s; negotiation state: {json.dumps(neg)[:800]}")
    _, address = call(consumer["mgmt"], "GET", f"/v3/edrs/{tp_id}/dataaddress?auto_refresh=true")
    endpoint, token = address["endpoint"], address["authorization"]
    print(f"    transfer {tp_id}, endpoint {endpoint}")

    step("consumer: pull data through provider data plane (inside the dataspace network)")
    out = subprocess.run(["docker", "run", "--rm", "--network", "ppap_dataspace", "--entrypoint", "curl",
                          os.getenv("CURL_IMAGE", "tractusx/ssi-dim-wallet-stub:0.1.0"),
                          "-sf", "-H", f"Authorization: {token}", endpoint],
                         capture_output=True, text=True)
    if out.returncode != 0:
        sys.exit(f"FAIL: data plane call failed (curl exit {out.returncode}): {out.stderr[:500]}")
    body = json.loads(out.stdout)
    print(f"    received payload: DID document id={body.get('id')}")
    print(f"\nSMOKE TEST PASSED: {consumer_name} -> {provider_name} (catalog, negotiation, EDR, data plane) "
          f"in {time.time() - t0:.1f}s")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--provider", default="tier1", choices=PARTICIPANTS)
    ap.add_argument("--consumer", default="oem", choices=PARTICIPANTS)
    a = ap.parse_args()
    run(a.provider, a.consumer)
