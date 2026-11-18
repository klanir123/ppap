#!/usr/bin/env python3
"""Consistency checks for the seed data (run in CI and via `make seed-check`).

1. Generated files are up to date (re-running the generators produces no diff).
2. Part mappings are mirrored on both sides of every EDC-enabled relationship.
3. Referential integrity (mapped parts, sites, partners, relations exist) and key formats.
4. Every level matrix only references catalogue element codes.
"""
import csv
import json
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).parent
PARTICIPANTS = {"oem": "BPNL00000000OEM1", "tier1": "BPNL000000TIER01", "tier2": "BPNL000000TIER02"}
BPNL, BPNS = re.compile(r"BPNL[0-9A-Z]{12}"), re.compile(r"BPNS[0-9A-Z]{12}")
errors = []


def rows(p, name):
    with (ROOT / p / name).open(encoding="utf-8") as f:
        return list(csv.DictReader(f, delimiter=";"))


def fail(msg):
    errors.append(msg)


# 1. generators reproduce the committed files
before = {f: f.read_bytes() for f in ROOT.rglob("*") if f.suffix in (".csv", ".json")}
for gen in ("generate_catalog.py", "generate_participants.py"):
    subprocess.run([sys.executable, str(ROOT / gen)], check=True, capture_output=True)
for f, content in before.items():
    if f.read_bytes() != content:
        fail(f"{f.relative_to(ROOT)} is not up to date with its generator")
        f.write_bytes(content)

# 2./3. per participant integrity
mappings = {}
for p, own in PARTICIPANTS.items():
    parts = {r["partNumber"] for r in rows(p, "parts.csv")}
    partners = {r["bpnl"]: r for r in rows(p, "partners.csv")}
    sites = {r["bpns"] for r in rows(p, "sites.csv")}
    relations = {(r["partnerBpnl"], r["partnerRole"]) for r in rows(p, "relations.csv")}
    if own in partners:
        fail(f"{p}: own BPNL listed as partner")
    for b, r in partners.items():
        if not BPNL.fullmatch(b):
            fail(f"{p}: invalid BPNL {b}")
        if not set(r["roles"].split("|")) <= {"CUSTOMER", "SUPPLIER"}:
            fail(f"{p}: invalid roles {r['roles']}")
    for r in rows(p, "sites.csv"):
        if not BPNS.fullmatch(r["bpns"]):
            fail(f"{p}: invalid BPNS {r['bpns']}")
        if r["ownerBpnl"] != own and r["ownerBpnl"] not in partners:
            fail(f"{p}: site {r['bpns']} owner {r['ownerBpnl']} unknown")
    for pb, role in relations:
        if pb not in partners or role not in partners[pb]["roles"].split("|"):
            fail(f"{p}: relation {pb}/{role} has no matching partner role")
    for m in rows(p, "part-mappings.csv"):
        key = (m["partnerBpnl"], m["partnerRole"])
        if key not in relations:
            fail(f"{p}: mapping without relation {key}")
        if m["partNumber"] not in parts:
            fail(f"{p}: mapping references unknown part {m['partNumber']}")
        for s in (m["ownSiteBpns"], m["partnerSiteBpns"]):
            if s and s not in sites:
                fail(f"{p}: mapping references unknown site {s}")
        mappings.setdefault((own, m["partnerBpnl"], m["partnerRole"]), set()).add((m["partNumber"], m["partnerPartNumber"]))

# mirrored mappings between seeded participants
mirror_role = {"SUPPLIER": "CUSTOMER", "CUSTOMER": "SUPPLIER"}
for (own, partner, role), pairs in mappings.items():
    if partner in PARTICIPANTS.values():
        other = mappings.get((partner, own, mirror_role[role]), set())
        if {(b, a) for a, b in other} != pairs:
            fail(f"mappings {own}->{partner} ({role}) are not mirrored by {partner}->{own}")

# 4. catalogue
catalog = json.loads((ROOT / "common" / "catalog.json").read_text(encoding="utf-8"))
codes = {e["code"] for e in catalog["elements"]}
for lvl in catalog["levels"]:
    unknown = set(lvl["requirements"]) - codes
    if unknown:
        fail(f"level {lvl['code']} references unknown elements {sorted(unknown)}")
    if set(lvl["requirements"].values()) - {"REQUIRED", "ON_REQUEST", "NOT_REQUIRED"}:
        fail(f"level {lvl['code']} has invalid requirement values")

if errors:
    print("SEED CHECK FAILED:\n  " + "\n  ".join(errors))
    sys.exit(1)
print(f"seed check ok: {len(PARTICIPANTS)} participants, {len(codes)} catalogue elements, "
      f"{sum(len(v) for v in mappings.values())} part mappings")
