#!/usr/bin/env python3
"""Generates seed/common/catalog.json – the default PPF/PPAP element catalogue.

Content is oriented on VDA Volume 2 (PPF, 7th ed. 2025) and AIAG PPAP 4th ed. Element numbering,
level matrices and wording are assumptions until the Catena-X Expert Group "Product Approval"
publishes its standard (TODO(EG)); everything is data and can be edited in the UI (UC-02).
"""
import json
import pathlib

R, O, N = "REQUIRED", "ON_REQUEST", "NOT_REQUIRED"

def schema(props, required=(), array=False):
    obj = {"type": "object", "properties": props, "required": list(required), "additionalProperties": False}
    s = {"type": "array", "minItems": 1, "items": obj} if array else obj
    return {"$schema": "https://json-schema.org/draft/2020-12/schema", **s}

num = {"type": "number"}
txt = {"type": "string", "maxLength": 200}
characteristic_schema = schema({
    "characteristic": txt, "nominal": num, "lowerTolerance": num, "upperTolerance": num,
    "measured": num, "unit": {"type": "string", "maxLength": 20}, "ok": {"type": "boolean"}},
    required=("characteristic", "measured", "ok"), array=True)

# code, de, en, vda ref, aiag ref, evidenceType, valueSchema, description de, description en
ELEMENTS = [
    ("PPF-01", "Deckblatt / Teilevorlagebestätigung", "Cover sheet / Part Submission Warrant", "VDA 2: PPF-Deckblatt", "AIAG #18 PSW", "DOCUMENT_AND_VALUES",
     schema({"submissionDeclaration": {"type": "boolean"}, "weightKg": num, "remarks": {"type": "string", "maxLength": 2000}}, required=("submissionDeclaration",)),
     "Kopfdaten der Vorlage und Bestätigung des Lieferanten, dass die Muster aus serienmäßigen Bedingungen stammen.",
     "Header data and supplier declaration that samples were produced under series conditions."),
    ("PPF-02", "Selbstbeurteilung Lieferant", "Supplier self-assessment", "VDA 2: Selbstbeurteilung", "", "VALUES",
     schema({"summary": {"type": "string", "maxLength": 4000}, "deviationsDeclared": {"type": "boolean"}}, required=("deviationsDeclared",)),
     "Gesamtbewertung des Lieferanten inklusive offengelegter Abweichungen.", "Overall supplier assessment including declared deviations."),
    ("PPF-03", "Zeichnung / Spezifikation mit Änderungsstand", "Design records with change level", "VDA 2: Kundenzeichnung, Spezifikation", "AIAG #1", "DOCUMENT", None,
     "Gültige Kundenzeichnung bzw. Spezifikation, auf die sich die Vorlage bezieht.", "Valid customer drawing or specification the submission refers to."),
    ("PPF-04", "Technische Änderungsdokumente", "Authorized engineering change documents", "VDA 2: Änderungsdokumentation", "AIAG #2", "DOCUMENT", None,
     "Noch nicht in die Zeichnung eingearbeitete, freigegebene Änderungen.", "Approved changes not yet incorporated into the design record."),
    ("PPF-05", "Freigabe Entwicklung durch Kunden", "Customer engineering approval", "", "AIAG #3", "DOCUMENT", None,
     "Nachweis einer technischen Freigabe durch die Kundenentwicklung, falls gefordert.", "Evidence of customer engineering approval, if required."),
    ("PPF-06", "Design-FMEA", "Design FMEA", "VDA 2 / AIAG-VDA FMEA", "AIAG #4", "DOCUMENT", None,
     "Nur bei Produktverantwortung des Lieferanten.", "Only if the supplier is design-responsible."),
    ("PPF-07", "Prozessablaufplan", "Process flow diagram", "VDA 2: Prozessablauf", "AIAG #5", "DOCUMENT", None, "", ""),
    ("PPF-08", "Prozess-FMEA", "Process FMEA", "VDA 2 / AIAG-VDA FMEA", "AIAG #6", "DOCUMENT", None, "", ""),
    ("PPF-09", "Produktionslenkungsplan", "Control plan", "VDA 2: Produktionslenkungsplan", "AIAG #7", "DOCUMENT", None, "", ""),
    ("PPF-10", "Prüfmittelfähigkeit", "Measurement system analysis", "VDA 5", "AIAG #8", "DOCUMENT_AND_VALUES",
     schema({"gauge": txt, "cg": num, "cgk": num, "grrPercent": num}, required=("gauge",), array=True),
     "Nachweis der Eignung der eingesetzten Prüfmittel (z. B. Cg/Cgk, %GRR).", "Capability of the measurement systems used (e.g. Cg/Cgk, %GRR)."),
    ("PPF-11", "Prüfergebnisse Maß", "Dimensional results", "VDA 2; Werte-Struktur künftig VDA 231-301 TODO(EG)", "AIAG #9", "DOCUMENT_AND_VALUES", characteristic_schema,
     "Messergebnisse aller Zeichnungsmerkmale.", "Measurement results for all drawing characteristics."),
    ("PPF-12", "Prüfergebnisse Werkstoff", "Material test results", "VDA 2; Werkstoffsemantik VDA 231-300 TODO(EG)", "AIAG #10", "DOCUMENT_AND_VALUES", characteristic_schema, "", ""),
    ("PPF-13", "Prüfergebnisse Funktion / Leistung", "Performance test results", "VDA 2", "AIAG #10", "DOCUMENT_AND_VALUES", characteristic_schema, "", ""),
    ("PPF-14", "Prozessfähigkeitsnachweis", "Initial process studies", "VDA 4", "AIAG #11", "DOCUMENT_AND_VALUES",
     schema({"characteristic": txt, "cpk": num, "ppk": num, "sampleSize": {"type": "integer", "minimum": 1}}, required=("characteristic",), array=True),
     "Vorläufige Prozessfähigkeit besonderer Merkmale.", "Preliminary process capability of special characteristics."),
    ("PPF-15", "Nachweis Prüflabor", "Qualified laboratory documentation", "", "AIAG #12", "DOCUMENT", None,
     "Akkreditierung bzw. Qualifikation der prüfenden Labore.", "Accreditation or qualification of the testing laboratories."),
    ("PPF-16", "Aussehen / Haptik / Akustik / Geruch", "Appearance approval report", "VDA 2: Haptik, Akustik, Geruch, Aussehen", "AIAG #13", "DOCUMENT", None, "", ""),
    ("PPF-17", "Erstmuster (Produktionsteile)", "Sample production parts", "VDA 2: Muster", "AIAG #14", "VALUES",
     schema({"quantity": {"type": "integer", "minimum": 1}, "deliveryNoteNumber": txt, "shippedOn": {"type": "string", "format": "date"}}, required=("quantity",)), "", ""),
    ("PPF-18", "Referenzmuster", "Master sample", "", "AIAG #15", "VALUES",
     schema({"storageLocation": txt, "retainedUntil": {"type": "string", "format": "date"}}), "", ""),
    ("PPF-19", "Prüfhilfsmittel", "Checking aids", "", "AIAG #16", "DOCUMENT", None, "", ""),
    ("PPF-20", "Kundenspezifische Anforderungen", "Customer-specific requirements", "VDA 2: kundenspezifische Forderungen", "AIAG #17", "DOCUMENT", None, "", ""),
    ("PPF-21", "Materialdaten / IMDS", "Material data / IMDS", "VDA 2: IMDS", "", "VALUES",
     schema({"imdsId": {"type": "string", "pattern": "^[0-9]{3,12}$"}, "imdsVersion": {"type": "integer", "minimum": 1}, "acceptedByCustomer": {"type": "boolean"}}, required=("imdsId",)),
     "Referenz auf das Materialdatenblatt im IMDS.", "Reference to the material data sheet in IMDS."),
    ("PPF-22", "Teilehistorie", "Part history", "VDA 2: Teilelebenslauf", "", "DOCUMENT", None, "", ""),
    ("PPF-23", "Softwarestand / Software-Konformität", "Software version / conformity", "VDA 2: Software", "", "DOCUMENT_AND_VALUES",
     schema({"softwareVersion": txt, "hardwareVersion": txt}, required=("softwareVersion",)), "Nur für Teile mit Software.", "Only for parts containing software."),
    ("PPF-24", "Kapazitätsnachweis (Run@Rate)", "Capacity verification (run@rate)", "VDA 2 / Kundenvorgabe", "", "DOCUMENT_AND_VALUES",
     schema({"plannedPerWeek": {"type": "integer", "minimum": 0}, "demonstratedPerWeek": {"type": "integer", "minimum": 0}}, required=("demonstratedPerWeek",)), "", ""),
]
codes = [e[0] for e in ELEMENTS]

def matrix(default, **overrides):
    m = {c: default for c in codes}
    for c, v in overrides.items():
        m[c.replace("_", "-")] = v
    return m

AIAG = {1: "PPF-03", 2: "PPF-04", 3: "PPF-05", 4: "PPF-06", 5: "PPF-07", 6: "PPF-08", 7: "PPF-09", 8: "PPF-10", 9: "PPF-11",
        10: ["PPF-12", "PPF-13"], 11: "PPF-14", 12: "PPF-15", 13: "PPF-16", 14: "PPF-17", 15: "PPF-18", 16: "PPF-19", 17: "PPF-20", 18: "PPF-01"}

def aiag(submit=(), retain_default=O):
    m = {c: N for c in codes}
    for n, c in AIAG.items():
        for code in (c if isinstance(c, list) else [c]):
            m[code] = R if n in submit else retain_default
    return m

vda3_optional = {"PPF_06": O, "PPF_15": O, "PPF_18": O, "PPF_19": O, "PPF_23": O, "PPF_05": O, "PPF_04": O}
LEVELS = [
    ("VDA-1", "VDA Stufe 1 – nur Deckblatt", "VDA level 1 – cover sheet only", "VDA", 10,
     matrix(O, PPF_01=R, PPF_02=R), "Nur Deckblatt und Selbstbeurteilung werden vorgelegt; weitere Nachweise auf Anforderung. TODO(EG)"),
    ("VDA-2", "VDA Stufe 2 – Deckblatt, Muster, eingeschränkte Nachweise", "VDA level 2 – cover sheet, samples, limited evidence", "VDA", 20,
     matrix(O, PPF_01=R, PPF_02=R, PPF_11=R, PPF_12=R, PPF_13=R, PPF_17=R, PPF_21=R), "TODO(EG)"),
    ("VDA-3", "VDA Stufe 3 – Deckblatt, Muster, vollständige Nachweise (Standard)", "VDA level 3 – cover sheet, samples, complete evidence (default)", "VDA", 30,
     matrix(R, **vda3_optional), "Standardstufe, sofern nicht anders vereinbart. TODO(EG)"),
    ("AIAG-1", "AIAG Level 1", "AIAG level 1", "AIAG", 110, aiag(submit=(18, 13)), "PSW (und ggf. Appearance Approval Report)."),
    ("AIAG-2", "AIAG Level 2", "AIAG level 2", "AIAG", 120, aiag(submit=(1, 2, 9, 10, 11, 12, 13, 14, 18)), "PSW mit Mustern und eingeschränkten Nachweisen."),
    ("AIAG-3", "AIAG Level 3", "AIAG level 3", "AIAG", 130, aiag(submit=tuple(n for n in range(1, 19) if n not in (15, 16, 17))), "PSW mit Mustern und vollständigen Nachweisen (Standard)."),
    ("AIAG-4", "AIAG Level 4", "AIAG level 4", "AIAG", 140, aiag(submit=(18,)), "PSW und weitere Anforderungen nach Kundenvorgabe."),
    ("AIAG-5", "AIAG Level 5", "AIAG level 5", "AIAG", 150, aiag(submit=(14, 18)), "PSW mit Mustern; vollständige Nachweise werden beim Lieferanten geprüft."),
    ("CUSTOM", "Kundenspezifisch", "Customer-specific", "CUSTOM", 200, matrix(N, PPF_01=R, PPF_02=R), "Vorbelegung minimal; Elemente je Anforderung festlegen."),
]

REASONS = [
    ("NEW_PART", "Neuteil", "New part"),
    ("PRODUCT_CHANGE", "Produktänderung (Konstruktion, Spezifikation, Werkstoff)", "Product change (design, specification, material)"),
    ("PROCESS_CHANGE", "Änderung des Fertigungsverfahrens", "Manufacturing process change"),
    ("TOOLING", "Neue oder geänderte Werkzeuge / Betriebsmittel", "New or modified tooling"),
    ("RELOCATION", "Verlagerung der Fertigung", "Production relocation"),
    ("SUBSUPPLIER_CHANGE", "Wechsel Unterlieferant oder Werkstoffquelle", "Change of sub-supplier or material source"),
    ("PRODUCTION_PAUSE", "Fertigungsunterbrechung über 12 Monate", "Production interruption > 12 months"),
    ("SOFTWARE_CHANGE", "Softwareänderung", "Software change"),
    ("CORRECTION", "Abstellen einer Abweichung", "Correction of a deviation"),
    ("REQUALIFICATION", "Requalifikation", "Requalification"),
]

catalog = {
    "_comment": "Seed catalogue oriented on VDA Volume 2 and AIAG PPAP; numbering/matrices are assumptions – TODO(EG). Generated by seed/generate_catalog.py.",
    "elements": [
        {"code": c, "nameDe": de, "nameEn": en, "descriptionDe": dd, "descriptionEn": de_en, "referenceVda": vda, "referenceAiag": aiag_ref,
         "evidenceType": et, "valueSchema": vs, "sortOrder": (i + 1) * 10, "active": True}
        for i, (c, de, en, vda, aiag_ref, et, vs, dd, de_en) in enumerate(ELEMENTS)
    ],
    "levels": [
        {"code": c, "nameDe": de, "nameEn": en, "descriptionDe": d, "descriptionEn": "", "scheme": sch, "sortOrder": so, "active": True, "requirements": m}
        for (c, de, en, sch, so, m, d) in LEVELS
    ],
    "reasons": [
        {"code": c, "nameDe": de, "nameEn": en, "descriptionDe": "", "descriptionEn": "", "sortOrder": (i + 1) * 10, "active": True}
        for i, (c, de, en) in enumerate(REASONS)
    ],
}
out = pathlib.Path(__file__).parent / "common" / "catalog.json"
out.write_text(json.dumps(catalog, indent=2, ensure_ascii=False) + "\n")
print(f"wrote {out}: {len(ELEMENTS)} elements, {len(LEVELS)} levels, {len(REASONS)} reasons")
