#!/usr/bin/env python3
"""Generates consistent, fictional master data for the three demo participants (seed/<p>/*.csv).

One source of truth so that part mappings match on both sides of every relationship:
  OEM customer part number  <->  Tier-1 supplier part number   (relation OEM-SUPPLIER / Tier1-CUSTOMER)
  Tier-1 component number   <->  Tier-2 part number            (relation Tier1-SUPPLIER / Tier2-CUSTOMER)
All companies, numbers, sites and addresses are fictional (no real companies, no personal data).
Run: python3 seed/generate_participants.py
"""
import csv
import pathlib
import random

random.seed(4711)  # deterministic output
ROOT = pathlib.Path(__file__).parent

OEM, T1, T2 = "BPNL00000000OEM1", "BPNL000000TIER01", "BPNL000000TIER02"
SUP3, SUP4, CUST2, TOOL = "BPNL0000000SUP03", "BPNL0000000SUP04", "BPNL0000000CUST2", "BPNL0000000TOOL5"


def dsp(p):
    return f"http://{p}-edc-cp:8084/api/v1/dsp/2025-1"


def did(bpnl):
    return f"did:web:wallet-stub:{bpnl}"


COMPANIES = {
    OEM: ("Demo-OEM AG", "oem", True),
    T1: ("Demo-Tier1 GmbH", "tier1", True),
    T2: ("Demo-Tier2 KG", "tier2", True),
    SUP3: ("Beispiel Kunststofftechnik GmbH", None, False),
    SUP4: ("Muster Metallverarbeitung AG", None, False),
    CUST2: ("Beispiel Nutzfahrzeuge AG", None, False),
    TOOL: ("Werkzeugbau Exempel OHG", None, False),
}

SITES = [  # bpns, name, owner, street, zip, city
    ("BPNS00000000OEMA", "Werk Nord", OEM, "Fahrzeugallee 1", "10001", "Nordhausen am See"),
    ("BPNS00000000OEMB", "Werk Süd", OEM, "Montagering 8", "80002", "Südstadt"),
    ("BPNS0000000T1W01", "Werk Hauptsitz", T1, "Zulieferweg 12", "70003", "Teilestadt"),
    ("BPNS0000000T1W02", "Werk Ost", T1, "Industriepark 4", "01004", "Oststadt"),
    ("BPNS0000000T2W01", "Werk Kunststoff", T2, "Spritzgussstraße 3", "90005", "Formhausen"),
    ("BPNS0000000SUP31", "Werk Kunststofftechnik", SUP3, "Granulatweg 7", "50006", "Beispielheim"),
    ("BPNS0000000SUP41", "Stanzwerk", SUP4, "Blechgasse 2", "40007", "Musterberg"),
    ("BPNS00000CUST021", "Werk Nutzfahrzeuge", CUST2, "Lastwagenstraße 9", "30008", "Exempeldorf"),
]

# Finished parts Tier-1 sells to the OEM: (name de, name en, material)
T1_TO_OEM = [
    ("Halter Stoßfänger vorne links", "Bumper bracket front left", "PP-TD20"),
    ("Halter Stoßfänger vorne rechts", "Bumper bracket front right", "PP-TD20"),
    ("Türgriff außen Fahrerseite", "Outside door handle driver side", "PC/ABS"),
    ("Türgriff außen Beifahrerseite", "Outside door handle passenger side", "PC/ABS"),
    ("Sensorhalter Radar Front", "Front radar sensor bracket", "PA6-GF30"),
    ("Kameraträger Windschutzscheibe", "Windscreen camera carrier", "PA66-GF35"),
    ("Kabelkanal Motorraum", "Engine bay cable duct", "PA6-GF15"),
    ("Luftführung Kühler oben", "Upper radiator air guide", "PP-T20"),
    ("Luftführung Kühler unten", "Lower radiator air guide", "PP-T20"),
    ("Abdeckung Scheinwerfer links", "Headlamp cover left", "PC"),
    ("Abdeckung Scheinwerfer rechts", "Headlamp cover right", "PC"),
    ("Konsole Mittelarmlehne", "Centre armrest console", "PP-GF20"),
    ("Lüftungsgitter Armaturentafel", "Dashboard air vent", "ABS"),
    ("Halteblech Batterie", "Battery holding plate", "DC04"),
    ("Querträger Instrumententafel", "Instrument panel cross member", "AlSi10Mg"),
    ("Lagerbock Motor links", "Engine mount bracket left", "EN-GJS-500-7"),
    ("Lagerbock Motor rechts", "Engine mount bracket right", "EN-GJS-500-7"),
    ("Schutzblech Unterboden", "Underbody heat shield", "AlMg3"),
    ("Abdeckung Ladeanschluss", "Charging port cover", "PC/PBT"),
    ("Gehäuse Steuergerät Tür", "Door control unit housing", "PBT-GF30"),
    ("Dichtung Tür vorne", "Front door seal", "EPDM"),
    ("Dichtung Tür hinten", "Rear door seal", "EPDM"),
    ("Clip Verkleidung Radhaus", "Wheel arch liner clip", "POM"),
    ("Halter Kennzeichenleuchte", "Licence plate lamp holder", "PA6"),
    ("Spoiler Heckklappe", "Tailgate spoiler", "ASA"),
    ("Griffschale Heckklappe", "Tailgate handle recess", "PC/ABS"),
    ("Rahmen Schiebedach", "Sunroof frame", "AlSi7Mg"),
    ("Konsole Lenksäule", "Steering column bracket", "S355MC"),
    ("Abdeckkappe Spiegel links", "Mirror cap left", "ASA"),
    ("Abdeckkappe Spiegel rechts", "Mirror cap right", "ASA"),
]
# OEM parts from the other (non-EDC) suppliers
OEM_FROM_SUP3 = [("Einstiegsleiste vorne", "Front sill trim", "PP"), ("Verkleidung A-Säule", "A-pillar trim", "PP-T20"),
                 ("Ablagefach Tür", "Door storage pocket", "PP"), ("Getränkehalter", "Cup holder", "ABS"),
                 ("Blende Schalthebel", "Gear lever trim", "PC/ABS"), ("Haltegriff Dachhimmel", "Roof grab handle", "PP-GF10"),
                 ("Abdeckung Sicherungskasten", "Fuse box cover", "PP"), ("Verkleidung Kofferraum links", "Trunk trim left", "PP-T20")]
OEM_FROM_SUP4 = [("Scharnier Motorhaube links", "Bonnet hinge left", "S420MC"), ("Scharnier Motorhaube rechts", "Bonnet hinge right", "S420MC"),
                 ("Schlossbügel Heckklappe", "Tailgate striker", "C45"), ("Halter Abgasanlage", "Exhaust hanger bracket", "1.4509"),
                 ("Verstärkung B-Säule", "B-pillar reinforcement", "22MnB5"), ("Befestigungswinkel Kotflügel", "Fender mounting bracket", "DC04"),
                 ("Abschleppöse", "Towing eye", "42CrMo4")]
# Components Tier-1 buys from Tier-2 (used in Tier-1 finished parts)
T2_TO_T1 = [("Rastnase Türgriff", "Door handle latch", "POM"), ("Feder Türgriff", "Door handle spring", "1.4310"),
            ("Linse Kameraträger", "Camera carrier lens", "PMMA"), ("Dämpfer Konsole", "Console damper", "TPE"),
            ("Clip Kabelkanal", "Cable duct clip", "PA66"), ("Lamelle Lüftungsgitter", "Air vent louvre", "ABS"),
            ("Dichtlippe Ladeklappe", "Charging flap seal lip", "TPV"), ("Kontaktträger Steuergerät", "Control unit contact carrier", "PBT-GF30"),
            ("Führung Schiebedach", "Sunroof guide", "POM"), ("Abstandshalter Hitzeschild", "Heat shield spacer", "PA46"),
            ("Buchse Lagerbock", "Engine mount bushing", "NR"), ("Halteklammer Spiegelkappe", "Mirror cap retaining clip", "PA6-GF30")]
T2_OTHER = [("Gehäuse Sensor klein", "Small sensor housing", "PBT"), ("Deckel Gehäuse", "Housing lid", "PA6"),
            ("Kabeldurchführung", "Cable grommet", "EPDM"), ("Distanzhülse", "Spacer sleeve", "PA66"), ("Zierblende", "Decorative trim", "ABS"),
            ("Steckerrahmen", "Connector frame", "PBT-GF20"), ("Lagerschale", "Bearing shell", "POM"), ("Filtergitter", "Filter mesh", "PA6"),
            ("Kappe Schraubdom", "Screw boss cap", "PP"), ("Halter Schlauch", "Hose holder", "PA66-GF30"), ("Stopfen Karosserie", "Body plug", "LDPE"),
            ("Führungsschiene klein", "Small guide rail", "POM"), ("Abdeckkappe", "Cover cap", "PP"), ("Klemmring", "Clamping ring", "PA12"),
            ("Dämpfungselement", "Damping element", "TPE"), ("Schutzkappe Stecker", "Connector protection cap", "TPE"),
            ("Haltestift", "Retaining pin", "PA6"), ("Zwischenring", "Intermediate ring", "PTFE"), ("Lichtleiter", "Light guide", "PMMA"),
            ("Tastenkappe", "Key cap", "PC")]


def rev():
    return random.choice(["A", "B", "C", "D"])


def write(p, name, header, rows):
    path = ROOT / p / name
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, delimiter=";", lineterminator="\n")
        w.writerow(header)
        w.writerows(rows)
    print(f"  {path.relative_to(ROOT.parent)}: {len(rows)} rows")


def partner_row(bpnl, roles):
    name, p, edc = COMPANIES[bpnl]
    domain = name.split()[0].lower().replace("-", "") + "-" + bpnl[-4:].lower() + ".example"
    return [bpnl, name, "|".join(roles), dsp(p) if edc else "", did(bpnl) if edc else "", f"ppap@{domain}"]


def site_rows(owners):
    return [[b, n, o, s, z, c, "DE"] for (b, n, o, s, z, c) in SITES if o in owners]


# ---------------------------------------------------------------- numbering
oem_parts, t1_parts, t2_parts = [], [], []           # [partNumber, nameDe, nameEn, drawingNumber, drawingRevision, material]
oem_t1_map, oem_sup3_map, oem_sup4_map = [], [], []   # (oemNo, supplierNo)
t1_cust2_map, t1_t2_map = [], []

for i, (de, en, mat) in enumerate(T1_TO_OEM):
    oem_no, t1_no = f"D10-{4711 + i * 7:05d}-0{1 + i % 3}", f"T1-31{i:04d}-01"
    r = rev()
    oem_parts.append([oem_no, de, en, f"Z-{oem_no}", r, mat])
    t1_parts.append([t1_no, de, en, f"T1Z-{3100 + i}", r, mat])
    oem_t1_map.append((oem_no, t1_no))
    if i % 6 == 0:  # some Tier-1 parts are also sold to a second (non-EDC) customer
        t1_cust2_map.append((t1_no, f"NF-{88000 + i}"))
for lst, sup, mp, prefix in ((OEM_FROM_SUP3, SUP3, oem_sup3_map, "K3"), (OEM_FROM_SUP4, SUP4, oem_sup4_map, "M4")):
    for i, (de, en, mat) in enumerate(lst):
        oem_no = f"D20-{prefix}{100 + i:03d}-01"
        oem_parts.append([oem_no, de, en, f"Z-{oem_no}", rev(), mat])
        mp.append((oem_no, f"{prefix}-{70000 + i * 13}"))
for i, (de, en, mat) in enumerate(T2_TO_T1):
    t1_no, t2_no = f"T1-52{i:04d}-00", f"T2-88-{1200 + i}"
    r = rev()
    t1_parts.append([t1_no, de, en, f"T1Z-{5200 + i}", r, mat])
    t2_parts.append([t2_no, de, en, f"T2Z-{1200 + i}", r, mat])
    t1_t2_map.append((t1_no, t2_no))
for i, (de, en, mat) in enumerate(T2_OTHER):
    t2_parts.append([f"T2-77-{3000 + i}", de, en, f"T2Z-{3000 + i}", rev(), mat])

PART_H = ["partNumber", "nameDe", "nameEn", "drawingNumber", "drawingRevision", "material"]
PARTNER_H = ["bpnl", "name", "roles", "dspEndpoint", "did", "contactEmail"]
SITE_H = ["bpns", "name", "ownerBpnl", "street", "postalCode", "city", "country"]
REL_H = ["partnerBpnl", "partnerRole", "exchangeMode"]
MAP_H = ["partnerBpnl", "partnerRole", "partNumber", "partnerPartNumber", "ownSiteBpns", "partnerSiteBpns"]
DEFAULT_MODE = "PUSH_NOTIFY_PULL_PAYLOAD"

print("OEM")
write("oem", "parts.csv", PART_H, oem_parts)
write("oem", "partners.csv", PARTNER_H, [partner_row(T1, ["SUPPLIER"]), partner_row(SUP3, ["SUPPLIER"]), partner_row(SUP4, ["SUPPLIER"])])
write("oem", "sites.csv", SITE_H, site_rows({OEM, T1, SUP3, SUP4}))
write("oem", "relations.csv", REL_H, [[T1, "SUPPLIER", DEFAULT_MODE], [SUP3, "SUPPLIER", "PUSH"], [SUP4, "SUPPLIER", "PUSH"]])
write("oem", "part-mappings.csv", MAP_H,
      [[T1, "SUPPLIER", o, s, "BPNS00000000OEMA" if n % 2 == 0 else "BPNS00000000OEMB", "BPNS0000000T1W01" if n % 3 else "BPNS0000000T1W02"]
       for n, (o, s) in enumerate(oem_t1_map)]
      + [[SUP3, "SUPPLIER", o, s, "BPNS00000000OEMA", "BPNS0000000SUP31"] for o, s in oem_sup3_map]
      + [[SUP4, "SUPPLIER", o, s, "BPNS00000000OEMB", "BPNS0000000SUP41"] for o, s in oem_sup4_map])

print("Tier-1")
write("tier1", "parts.csv", PART_H, t1_parts)
write("tier1", "partners.csv", PARTNER_H, [partner_row(OEM, ["CUSTOMER"]), partner_row(CUST2, ["CUSTOMER"]), partner_row(T2, ["SUPPLIER"])])
write("tier1", "sites.csv", SITE_H, site_rows({T1, OEM, CUST2, T2}))
write("tier1", "relations.csv", REL_H, [[OEM, "CUSTOMER", DEFAULT_MODE], [CUST2, "CUSTOMER", "PUSH"], [T2, "SUPPLIER", DEFAULT_MODE]])
write("tier1", "part-mappings.csv", MAP_H,
      [[OEM, "CUSTOMER", s, o, "BPNS0000000T1W01" if n % 3 else "BPNS0000000T1W02", "BPNS00000000OEMA" if n % 2 == 0 else "BPNS00000000OEMB"]
       for n, (o, s) in enumerate(oem_t1_map)]
      + [[CUST2, "CUSTOMER", t, c, "BPNS0000000T1W01", "BPNS00000CUST021"] for t, c in t1_cust2_map]
      + [[T2, "SUPPLIER", t, s, "BPNS0000000T1W01", "BPNS0000000T2W01"] for t, s in t1_t2_map])

print("Tier-2")
write("tier2", "parts.csv", PART_H, t2_parts)
write("tier2", "partners.csv", PARTNER_H, [partner_row(T1, ["CUSTOMER"]), partner_row(TOOL, ["SUPPLIER"])])
write("tier2", "sites.csv", SITE_H, site_rows({T2, T1}))
write("tier2", "relations.csv", REL_H, [[T1, "CUSTOMER", DEFAULT_MODE], [TOOL, "SUPPLIER", "PUSH"]])
write("tier2", "part-mappings.csv", MAP_H,
      [[T1, "CUSTOMER", s, t, "BPNS0000000T2W01", "BPNS0000000T1W01"] for t, s in t1_t2_map])
