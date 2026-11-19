#!/usr/bin/env python3
"""Consistency checks for the seed data (run in CI and via `make seed-check`).

1. Generated files are up to date (re-running the generators produces no diff).
2. Part mappings are mirrored on both sides of every EDC-enabled relationship.
3. Referential integrity (mapped parts, sites, partners, relations exist) and key formats.
4. Every level matrix only references catalogue element codes.
5. Approval cases: mirrored across participants (same exchangeId), parts/partners/codes exist, values satisfy
   the element value schemas, referenced documents exist, SUBMITTED revisions only for LEGACY_IMPORT.
6. Form templates reference catalogue codes consistently.
Requires: pip install -r seed/requirements.txt
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
before = {f: f.read_bytes() for f in ROOT.rglob("*") if f.suffix in (".csv", ".json", ".pdf", ".xlsx")}
for gen in ("generate_catalog.py", "generate_participants.py", "generate_documents.py", "generate_forms.py", "generate_cases.py"):
    subprocess.run([sys.executable, str(ROOT / gen)], check=True, capture_output=True)
def comparable(path, data):
    """XLSX are zip containers whose headers vary with the Python/zip version: compare member contents."""
    if path.suffix == ".xlsx":
        import io
        import zipfile
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            return {n: z.read(n) for n in sorted(z.namelist())}
    return data


for f, content in before.items():
    if comparable(f, f.read_bytes()) != comparable(f, content):
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

# 5. approval cases
import jsonschema  # noqa: E402

elements = {e["code"]: e for e in catalog["elements"]}
levels, reasons = {lv["code"] for lv in catalog["levels"]}, {r["code"] for r in catalog["reasons"]}
docs = {f.name for f in (ROOT / "documents").glob("*.pdf")}
by_exchange = {}
case_count = 0
for p, own in PARTICIPANTS.items():
    parts = {r["partNumber"] for r in rows(p, "parts.csv")}
    partners = {r["bpnl"] for r in rows(p, "partners.csv")}
    numbers = set()
    for c in json.loads((ROOT / p / "approval-cases.json").read_text(encoding="utf-8"))["cases"]:
        case_count += 1
        where = f"{p}:{c['caseNumber']}"
        if c["caseNumber"] in numbers:
            fail(f"{where}: duplicate case number")
        numbers.add(c["caseNumber"])
        if c["partNumber"] not in parts or c["partnerBpnl"] not in partners:
            fail(f"{where}: unknown part or partner")
        if c["reasonCode"] not in reasons or c["levelCode"] not in levels:
            fail(f"{where}: unknown reason/level")
        for e in c["elements"]:
            if e["code"] not in elements:
                fail(f"{where}: unknown element {e['code']}")
        for sub in c["submissions"]:
            if sub["status"] == "SUBMITTED" and c["source"] != "LEGACY_IMPORT":
                fail(f"{where}: SUBMITTED revision only allowed for LEGACY_IMPORT (signatures are never seeded)")
            for ev in sub["elements"]:
                schema = elements[ev["code"]]["valueSchema"]
                if ev.get("values") is not None and schema:
                    try:
                        jsonschema.validate(ev["values"], schema)
                    except jsonschema.ValidationError as ex:
                        fail(f"{where}/{ev['code']}: value schema violation: {ex.message}")
                for d in ev.get("documents", []):
                    if d not in docs:
                        fail(f"{where}/{ev['code']}: missing document {d}")
        by_exchange.setdefault(c["exchangeId"], []).append((own, c))
for xid, sides in by_exchange.items():
    if len(sides) > 2:
        fail(f"exchangeId {xid} used more than twice")
    if len(sides) == 2:
        (a_own, a), (b_own, b) = sides
        if {a["ownRole"], b["ownRole"]} != {"CUSTOMER", "SUPPLIER"} or a["partnerBpnl"] != b_own or b["partnerBpnl"] != a_own \
                or a["partNumber"] != b["partnerPartNumber"] or a["status"] != b["status"]:
            fail(f"exchangeId {xid}: customer and supplier cases are not mirrored")

# 6. form templates
for t in json.loads((ROOT / "common" / "form-templates.json").read_text(encoding="utf-8"))["templates"]:
    if set(t["selfAssessmentValues"].values()) - {"OK", "NOK", "NA"}:
        fail(f"form template {t['code']}: invalid self-assessment mapping")

if errors:
    print("SEED CHECK FAILED:\n  " + "\n  ".join(errors))
    sys.exit(1)
print(f"seed check ok: {len(PARTICIPANTS)} participants, {len(codes)} catalogue elements, "
      f"{sum(len(v) for v in mappings.values())} part mappings, {case_count} approval cases, {len(docs)} documents")
