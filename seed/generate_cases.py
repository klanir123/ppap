#!/usr/bin/env python3
"""Generates mirrored demo approval cases: seed/<participant>/approval-cases.json.

Both sides of a customer–supplier pair get their own case with the SAME exchangeId (the customer's
case id that travels with every message from phase 4 on). Contents:
  * active cases in early states (DRAFT, REQUESTED, IN_PROGRESS with partially filled drafts),
  * completed legacy cases (source LEGACY_IMPORT, e.g. migrated from a former portal) with revisions and
    decisions but WITHOUT signatures – signed submissions are only ever produced live,
  * one manually captured request from a customer without Catena-X (source MANUAL).
Dates are relative (±Nd) and resolved by the loader at load time. Values are validated against the
element value schemas of seed/common/catalog.json. Format: docs/api/approval-api.md.
"""
import csv
import json
import pathlib
import uuid

import jsonschema

ROOT = pathlib.Path(__file__).parent
CATALOG = json.loads((ROOT / "common" / "catalog.json").read_text(encoding="utf-8"))
ELEMENTS = {e["code"]: e for e in CATALOG["elements"]}
LEVELS = {lv["code"]: lv for lv in CATALOG["levels"]}
OEM, T1, T2, CUST2, SUP3 = "BPNL00000000OEM1", "BPNL000000TIER01", "BPNL000000TIER02", "BPNL0000000CUST2", "BPNL0000000SUP03"
NS = uuid.UUID("6f1c2d3e-4b5a-4c6d-8e7f-000000000ppa".replace("ppa", "abc"))
USERS = {OEM: "oem.quality", T1: "tier1.quality", T2: "tier2.quality"}

DOCS = {"PPF-01": "deckblatt-ppf.pdf", "PPF-03": "zeichnung-beispielteil.pdf", "PPF-07": "prozessablaufplan.pdf",
        "PPF-08": "prozess-fmea.pdf", "PPF-09": "produktionslenkungsplan.pdf", "PPF-10": "pruefmittelfaehigkeit.pdf",
        "PPF-11": "messprotokoll.pdf", "PPF-12": "werkstoffzeugnis.pdf", "PPF-13": "funktionspruefung.pdf",
        "PPF-14": "prozessfaehigkeit.pdf", "PPF-21": "imds-nachweis.pdf"}


def characteristic(rows):
    return [{"characteristic": c, "nominal": n, "lowerTolerance": -t, "upperTolerance": t, "measured": m, "unit": "mm", "ok": abs(m - n) <= t}
            for c, n, t, m in rows]


VALUES = {
    "PPF-01": {"submissionDeclaration": True, "weightKg": 0.142},
    "PPF-02": {"summary": "Alle geforderten Nachweise liegen vor.", "deviationsDeclared": False},
    "PPF-10": [{"gauge": "Messschieber MS-0815", "cg": 1.85, "cgk": 1.52, "grrPercent": 8.4}],
    "PPF-11": characteristic([("Maß A1", 12.0, 0.1, 12.02), ("Maß A2", 45.5, 0.2, 45.45), ("Maß B1", 8.0, 0.05, 8.01)]),
    "PPF-12": characteristic([("Zugfestigkeit [MPa]", 60, 5, 62), ("Glasfaseranteil [%]", 30, 2, 30.4)]),
    "PPF-13": characteristic([("Betätigungskraft [N]", 27.5, 7.5, 28)]),
    "PPF-14": [{"characteristic": "Maß A1", "cpk": 2.01, "ppk": 1.93, "sampleSize": 125}],
    "PPF-17": {"quantity": 5, "deliveryNoteNumber": "LS-2026-0815"},
    "PPF-21": {"imdsId": "123456789", "imdsVersion": 2, "acceptedByCustomer": True},
    "PPF-23": {"softwareVersion": "n/a", "hardwareVersion": "n/a"},
    "PPF-24": {"plannedPerWeek": 12000, "demonstratedPerWeek": 12600},
}
for code, v in VALUES.items():  # seed values must satisfy the catalogue schemas
    if ELEMENTS[code]["valueSchema"]:
        jsonschema.validate(v, ELEMENTS[code]["valueSchema"])


def parts(p):
    with (ROOT / p / "parts.csv").open(encoding="utf-8") as f:
        return {r["partNumber"]: r for r in csv.DictReader(f, delimiter=";")}


def mappings(p, partner):
    with (ROOT / p / "part-mappings.csv").open(encoding="utf-8") as f:
        return [(r["partNumber"], r["partnerPartNumber"]) for r in csv.DictReader(f, delimiter=";") if r["partnerBpnl"] == partner]


def required(level):
    return [c for c, req in LEVELS[level]["requirements"].items() if req == "REQUIRED"]


def evidence(code, assessment="OK", comment=""):
    e = ELEMENTS[code]
    ev = {"code": code, "selfAssessment": assessment, "comment": comment, "values": None, "documents": []}
    if assessment != "NA":
        if e["valueSchema"] and code in VALUES:
            ev["values"] = VALUES[code]
        if e["evidenceType"] in ("DOCUMENT", "DOCUMENT_AND_VALUES"):
            ev["documents"] = [DOCS.get(code, "deckblatt-ppf.pdf")]
    return ev


def full_submission(level, rev, submitted, submitter, overrides=None):
    overrides = overrides or {}
    els = [evidence(c, *overrides.get(c, ("OK", ""))) for c in required(level)]
    return {"revision": rev, "status": "SUBMITTED", "submittedAt": submitted, "submittedBy": submitter, "elements": els}


def decision(result, decided, by, findings=None, conditions=None, due=None, comment=""):
    return {"result": result, "conditions": [{"text": t} for t in (conditions or [])], "conditionDueDate": due, "comment": comment,
            "elementFindings": [{"code": c, "result": r, "comment": cm} for c, r, cm in (findings or [])],
            "decidedAt": decided, "decidedBy": by}


counters = {}


def case_number(owner, year):
    counters[(owner, year)] = counters.get((owner, year), 0) + 1
    return f"PPF-{year}-{counters[(owner, year)]:05d}"


out = {OEM: [], T1: [], T2: []}


def pair(key, customer, supplier, cust_part, supp_part, status, level, reason, due, requested, remarks,
         timeline_extra=(), submissions=(), draft=None, customer_only=False, legacy=False, year=2026):
    """Creates the customer case and (unless customer_only) the mirrored supplier case."""
    xid = str(uuid.uuid5(NS, key))
    els = [{"code": c, "requirement": "REQUIRED", "note": ""} for c in required(level)]
    cu, su = USERS.get(customer, "import"), USERS.get(supplier, "import")
    timeline = [] if status == "DRAFT" else [{"at": requested, "actor": cu, "from": "DRAFT", "to": "REQUESTED", "action": "SEND", "comment": ""}]
    timeline += [dict(t, actor=cu if t["actor"] == "C" else su) for t in timeline_extra]
    base = {"exchangeId": xid, "status": status, "reasonCode": reason, "levelCode": level, "dueDate": due, "remarks": remarks,
            "requestedAt": None if status == "DRAFT" else requested, "requestedBy": cu, "elements": els, "timeline": timeline}
    subs = list(submissions) + ([draft] if draft else [])
    if customer in out:
        out[customer].append(dict(base, caseNumber=case_number(customer, year), ownRole="CUSTOMER",
                                  source="LEGACY_IMPORT" if legacy else "LOCAL", partnerBpnl=supplier, partNumber=cust_part,
                                  partnerPartNumber=supp_part, submissions=[s for s in subs if s["status"] == "SUBMITTED"]))
    if not customer_only and supplier in out:
        out[supplier].append(dict(base, caseNumber=case_number(supplier, year), ownRole="SUPPLIER",
                                  source="LEGACY_IMPORT" if legacy else "EXCHANGE", partnerBpnl=customer, partNumber=supp_part,
                                  partnerPartNumber=cust_part, submissions=subs))


def T(at, frm, to, action, actor, comment=""):
    return {"at": at, "actor": actor, "from": frm, "to": to, "action": action, "comment": comment}


oem_t1 = mappings("oem", T1)        # (oem part, tier1 part)
t1_t2 = mappings("tier1", T2)       # (tier1 part, tier2 part)

# ------------------------------------------------------------------ OEM -> Tier-1, active
pair("oem-t1-requested-1", OEM, T1, *oem_t1[0], "REQUESTED", "VDA-3", "NEW_PART", "+21d", "-2d",
     "Erstbemusterung für Serienanlauf Modelljahr 2027.")
pair("oem-t1-requested-2", OEM, T1, *oem_t1[4], "REQUESTED", "VDA-2", "TOOLING", "+30d", "-1d",
     "Zweitwerkzeug für Kapazitätserweiterung.")
pair("oem-t1-inprogress-1", OEM, T1, *oem_t1[2], "IN_PROGRESS", "VDA-3", "PRODUCT_CHANGE", "+14d", "-6d",
     "Änderung Griffkontur gemäß Änderungsantrag (fiktiv).",
     timeline_extra=[T("-4d", "REQUESTED", "IN_PROGRESS", "START_WORK", "S")],
     draft={"revision": 1, "status": "DRAFT", "elements": [evidence("PPF-01"), evidence("PPF-02"), evidence("PPF-03"),
                                                            evidence("PPF-11", "NOK", "Merkmal A2 knapp außerhalb, Nachmessung läuft")]})
pair("oem-t1-inprogress-overdue", OEM, T1, *oem_t1[5], "IN_PROGRESS", "AIAG-3", "NEW_PART", "-2d", "-25d",
     "Neuteil Kameraträger, Termin abgestimmt mit Projekt.",
     timeline_extra=[T("-20d", "REQUESTED", "IN_PROGRESS", "START_WORK", "S")],
     draft={"revision": 1, "status": "DRAFT", "elements": [evidence(c) for c in required("AIAG-3") if c not in ("PPF-14", "PPF-15")]})
pair("oem-draft-1", OEM, T1, *oem_t1[7], "DRAFT", "VDA-3", "RELOCATION", "+35d", "-0d",
     "Verlagerung der Fertigung in Werk Ost – Entwurf.", customer_only=True)

# ------------------------------------------------------------------ OEM -> Tier-1, legacy (migrated from a former portal)
pair("oem-t1-legacy-approved", OEM, T1, *oem_t1[1], "APPROVED", "VDA-3", "NEW_PART", "-90d", "-120d",
     "Altvorgang aus Portal-Migration.", legacy=True, year=2026,
     timeline_extra=[T("-115d", "REQUESTED", "IN_PROGRESS", "START_WORK", "S"), T("-95d", "IN_PROGRESS", "SUBMITTED", "SUBMIT", "S"),
                     T("-92d", "SUBMITTED", "IN_REVIEW", "START_REVIEW", "C"), T("-85d", "IN_REVIEW", "APPROVED", "DECIDE", "C")],
     submissions=[dict(full_submission("VDA-3", 1, "-95d", "tier1.quality"),
                       decision=decision("APPROVED", "-85d", "oem.quality", comment="Freigabe erteilt."))])
pair("oem-t1-legacy-two-revisions", OEM, T1, *oem_t1[3], "APPROVED", "VDA-3", "PRODUCT_CHANGE", "-150d", "-210d",
     "Altvorgang: Ablehnung in Revision 1, Freigabe in Revision 2.", legacy=True, year=2025,
     timeline_extra=[T("-205d", "REQUESTED", "IN_PROGRESS", "START_WORK", "S"), T("-190d", "IN_PROGRESS", "SUBMITTED", "SUBMIT", "S"),
                     T("-188d", "SUBMITTED", "IN_REVIEW", "START_REVIEW", "C"), T("-180d", "IN_REVIEW", "REJECTED", "DECIDE", "C"),
                     T("-178d", "REJECTED", "IN_PROGRESS", "NEW_REVISION", "S"), T("-160d", "IN_PROGRESS", "SUBMITTED", "SUBMIT", "S"),
                     T("-158d", "SUBMITTED", "IN_REVIEW", "START_REVIEW", "C"), T("-152d", "IN_REVIEW", "APPROVED", "DECIDE", "C")],
     submissions=[dict(full_submission("VDA-3", 1, "-190d", "tier1.quality", {"PPF-14": ("NOK", "Cpk Merkmal 3 = 1,42")}),
                       decision=decision("REJECTED", "-180d", "oem.quality",
                                         findings=[("PPF-14", "NOT_ACCEPTED", "Prozessfähigkeit Merkmal 3 nicht nachgewiesen (Cpk < 1,67)")])),
                  dict(full_submission("VDA-3", 2, "-160d", "tier1.quality"),
                       decision=decision("APPROVED", "-152d", "oem.quality", comment="Nach Prozessoptimierung freigegeben."))])
pair("oem-t1-legacy-conditional", OEM, T1, *oem_t1[9], "CONDITIONALLY_APPROVED", "VDA-3", "PROCESS_CHANGE", "-40d", "-70d",
     "Altvorgang mit Auflage.", legacy=True,
     timeline_extra=[T("-65d", "REQUESTED", "IN_PROGRESS", "START_WORK", "S"), T("-50d", "IN_PROGRESS", "SUBMITTED", "SUBMIT", "S"),
                     T("-48d", "SUBMITTED", "IN_REVIEW", "START_REVIEW", "C"), T("-42d", "IN_REVIEW", "CONDITIONALLY_APPROVED", "DECIDE", "C")],
     submissions=[dict(full_submission("VDA-3", 1, "-50d", "tier1.quality"),
                       decision=decision("CONDITIONALLY_APPROVED", "-42d", "oem.quality",
                                         findings=[("PPF-24", "ACCEPTED_WITH_CONDITION", "Run@Rate mit zweiter Schicht wiederholen")],
                                         conditions=["Run@Rate-Nachweis im Zweischichtbetrieb nachreichen"], due="+20d"))])
pair("oem-sup3-legacy", OEM, SUP3, *mappings("oem", SUP3)[0], "APPROVED", "VDA-2", "NEW_PART", "-200d", "-240d",
     "Altvorgang eines Lieferanten ohne Catena-X-Anbindung.", legacy=True, customer_only=True, year=2025,
     timeline_extra=[T("-210d", "REQUESTED", "SUBMITTED", "SUBMIT", "S"), T("-205d", "SUBMITTED", "APPROVED", "DECIDE", "C")],
     submissions=[dict(full_submission("VDA-2", 1, "-210d", "import"), decision=decision("APPROVED", "-205d", "oem.quality"))])

# ------------------------------------------------------------------ Tier-1 -> Tier-2 (cascade context)
pair("t1-t2-inprogress-1", T1, T2, *t1_t2[0], "IN_PROGRESS", "VDA-3", "PRODUCT_CHANGE", "+10d", "-3d",
     "Unterteil zu Türgriff außen Fahrerseite (Änderung Griffkontur). Formblatt-Beispiel: seed/forms/ppf-formblatt-v1-beispiel-tier2.xlsx",
     timeline_extra=[T("-2d", "REQUESTED", "IN_PROGRESS", "START_WORK", "S")],
     draft={"revision": 1, "status": "DRAFT", "elements": [evidence("PPF-01")]})
pair("t1-t2-requested-1", T1, T2, *t1_t2[2], "REQUESTED", "VDA-2", "NEW_PART", "+25d", "-1d", "Neues Linsenmaterial (fiktiv).")
pair("t1-t2-legacy-approved", T1, T2, *t1_t2[5], "APPROVED", "VDA-3", "NEW_PART", "-60d", "-100d", "Altvorgang.", legacy=True,
     timeline_extra=[T("-95d", "REQUESTED", "IN_PROGRESS", "START_WORK", "S"), T("-70d", "IN_PROGRESS", "SUBMITTED", "SUBMIT", "S"),
                     T("-68d", "SUBMITTED", "IN_REVIEW", "START_REVIEW", "C"), T("-62d", "IN_REVIEW", "APPROVED", "DECIDE", "C")],
     submissions=[dict(full_submission("VDA-3", 1, "-70d", "tier2.quality"), decision=decision("APPROVED", "-62d", "tier1.quality"))])

# ------------------------------------------------------------------ Tier-1: manual request from a customer without Catena-X
t1_cust2 = mappings("tier1", CUST2)[0]
out[T1].append({"exchangeId": str(uuid.uuid5(NS, "t1-cust2-manual")), "caseNumber": case_number(T1, 2026), "ownRole": "SUPPLIER",
                "source": "MANUAL", "status": "REQUESTED", "partnerBpnl": CUST2, "partNumber": t1_cust2[0], "partnerPartNumber": t1_cust2[1],
                "reasonCode": "REQUALIFICATION", "levelCode": "VDA-2", "dueDate": "+18d",
                "remarks": "Anforderung per E-Mail vom Kunden erhalten (kein Catena-X-Zugang), manuell erfasst.",
                "requestedAt": "-1d", "requestedBy": "tier1.quality",
                "elements": [{"code": c, "requirement": "REQUIRED", "note": ""} for c in required("VDA-2")],
                "timeline": [T("-1d", None, "REQUESTED", "CAPTURE_MANUAL", "tier1.quality")], "submissions": []})

for bpnl, p in ((OEM, "oem"), (T1, "tier1"), (T2, "tier2")):
    own_parts = parts(p)
    for c in out[bpnl]:
        assert c["partNumber"] in own_parts, (p, c["partNumber"])
    path = ROOT / p / "approval-cases.json"
    path.write_text(json.dumps({"_comment": "Generated by seed/generate_cases.py – see docs/api/approval-api.md",
                                "cases": out[bpnl]}, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"  seed/{p}/approval-cases.json: {len(out[bpnl])} cases")
