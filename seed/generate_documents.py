#!/usr/bin/env python3
"""Generates fictional sample evidence documents (PDF) for the demo cases: seed/documents/*.pdf.

Every page carries a visible "fictional sample" notice. Output is byte-for-byte deterministic
(reportlab invariant mode) so that `seed/check_seed.py` can verify it in CI.
Run: python3 seed/generate_documents.py   (requires reportlab)
"""
import pathlib

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4, landscape
from reportlab.lib.units import mm
from reportlab.pdfgen import canvas

OUT = pathlib.Path(__file__).parent / "documents"
OUT.mkdir(exist_ok=True)
NOTICE = "FIKTIVES BEISPIELDOKUMENT – nur für Demonstrationszwecke / fictional sample"


def frame(c, title, subtitle, size):
    w, h = size
    c.setTitle(title)
    c.setAuthor("msg CX PPAP Connector – Demo-Daten")
    c.setFillColor(colors.HexColor("#1f2937"))
    c.setFont("Helvetica-Bold", 16)
    c.drawString(20 * mm, h - 22 * mm, title)
    c.setFont("Helvetica", 10)
    c.setFillColor(colors.HexColor("#4b5563"))
    c.drawString(20 * mm, h - 29 * mm, subtitle)
    c.setStrokeColor(colors.HexColor("#d1d5db"))
    c.line(20 * mm, h - 33 * mm, w - 20 * mm, h - 33 * mm)
    c.setFont("Helvetica-Oblique", 8)
    c.setFillColor(colors.HexColor("#b91c1c"))
    c.drawString(20 * mm, 12 * mm, NOTICE)
    c.saveState()
    c.setFillColor(colors.Color(0.85, 0.1, 0.1, alpha=0.07))
    c.setFont("Helvetica-Bold", 60)
    c.translate(w / 2, h / 2)
    c.rotate(35)
    c.drawCentredString(0, 0, "MUSTER")
    c.restoreState()
    c.setFillColor(colors.black)


def table(c, x, y, headers, rows, widths):
    c.setFont("Helvetica-Bold", 9)
    cx = x
    for hd, wd in zip(headers, widths):
        c.drawString(cx, y, hd)
        cx += wd
    c.setFont("Helvetica", 9)
    for r in rows:
        y -= 6 * mm
        cx = x
        for v, wd in zip(r, widths):
            c.drawString(cx, y, str(v))
            cx += wd
    return y


def doc(name, title, subtitle, body, size=A4):
    c = canvas.Canvas(str(OUT / name), pagesize=size, invariant=1)
    frame(c, title, subtitle, size)
    body(c, size)
    c.showPage()
    c.save()
    print(f"  seed/documents/{name}")


def text_lines(lines, start=45):
    def body(c, size):
        y = size[1] - start * mm
        c.setFont("Helvetica", 10)
        for ln in lines:
            c.drawString(20 * mm, y, ln)
            y -= 6 * mm
    return body


def measurement(c, size):
    rows = [(i + 1, f"Maß {chr(65 + i % 6)}{i // 6 + 1}", f"{n:.2f}", f"±{t:.2f}", f"{n + d:.3f}", "mm", "i.O.")
            for i, (n, t, d) in enumerate([(12.0, .1, .02), (45.5, .2, -.05), (8.0, .05, .01), (120.0, .3, .12), (3.5, .05, -.01),
                                           (60.0, .2, .03), (22.0, .1, -.04), (15.25, .05, .02), (90.0, .25, .1), (5.0, .05, .0)])]
    table(c, 20 * mm, size[1] - 45 * mm, ["Nr.", "Merkmal", "Soll", "Toleranz", "Ist", "Einheit", "Bewertung"], rows,
          [12 * mm, 35 * mm, 22 * mm, 24 * mm, 24 * mm, 18 * mm, 25 * mm])


def capability(c, size):
    rows = [(f"Merkmal {i + 1}", f"{v:.2f}", f"{v - .08:.2f}", 125, "i.O." if v >= 1.67 else "Maßnahme")
            for i, v in enumerate([2.01, 1.88, 1.72, 2.35, 1.69, 1.95])]
    table(c, 20 * mm, size[1] - 45 * mm, ["Merkmal", "Cpk", "Ppk", "n", "Bewertung"], rows, [40 * mm, 25 * mm, 25 * mm, 20 * mm, 30 * mm])


def msa(c, size):
    rows = [("Messschieber MS-0815", "1.85", "1.52", "8.4 %", "geeignet"), ("Lehre GL-12", "2.10", "1.90", "6.1 %", "geeignet"),
            ("KMG Zeiss-frei-erfunden", "3.20", "2.95", "4.2 %", "geeignet")]
    table(c, 20 * mm, size[1] - 45 * mm, ["Prüfmittel", "Cg", "Cgk", "%GRR", "Ergebnis"], rows, [55 * mm, 20 * mm, 20 * mm, 22 * mm, 30 * mm])


def fmea(c, size):
    rows = [("Spritzgießen", "Füllung unvollständig", "Funktion beeinträchtigt", 7, 3, 4, "Prozessüberwachung Druck"),
            ("Entformen", "Verzug", "Montage nicht möglich", 6, 3, 3, "Kühlzeit validiert"),
            ("Prüfen", "Fehler nicht erkannt", "Feldausfall", 8, 2, 3, "100 % Kamera-Prüfung"),
            ("Verpacken", "Beschädigung", "Optischer Mangel", 4, 2, 2, "Fachverpackung")]
    table(c, 15 * mm, size[1] - 45 * mm, ["Prozessschritt", "Fehlerart", "Folge", "B", "A", "E", "Maßnahme"], rows,
          [35 * mm, 45 * mm, 45 * mm, 10 * mm, 10 * mm, 10 * mm, 60 * mm])


def control_plan(c, size):
    rows = [("10", "Granulat trocknen", "Restfeuchte", "< 0,2 %", "Feuchtemessgerät", "je Charge"),
            ("20", "Spritzgießen", "Werkzeugtemperatur", "60 ± 5 °C", "Prozessdatenerfassung", "100 %"),
            ("30", "Maßprüfung", "Maß A1", "12,00 ± 0,10", "Lehre GL-12", "5 Teile/Std."),
            ("40", "Sichtprüfung", "Oberfläche", "Grenzmuster", "visuell", "100 %")]
    table(c, 15 * mm, size[1] - 45 * mm, ["AG", "Arbeitsgang", "Merkmal", "Spezifikation", "Prüfmittel", "Häufigkeit"], rows,
          [12 * mm, 40 * mm, 40 * mm, 35 * mm, 45 * mm, 30 * mm])


def drawing(c, size):
    w, h = size
    c.setStrokeColor(colors.HexColor("#1f2937"))
    c.rect(20 * mm, 25 * mm, w - 40 * mm, h - 65 * mm)
    c.roundRect(70 * mm, 70 * mm, 110 * mm, 60 * mm, 8 * mm)
    c.circle(100 * mm, 100 * mm, 10 * mm)
    c.circle(150 * mm, 100 * mm, 10 * mm)
    c.setFont("Helvetica", 8)
    c.drawString(72 * mm, 135 * mm, "110 ±0,3")
    c.drawString(185 * mm, 100 * mm, "60 ±0,2")
    c.rect(w - 110 * mm, 25 * mm, 90 * mm, 25 * mm)
    c.drawString(w - 107 * mm, 43 * mm, "Benennung: Beispielteil (fiktiv)")
    c.drawString(w - 107 * mm, 36 * mm, "Zeichnungsindex: B   Maßstab 1:1")
    c.drawString(w - 107 * mm, 29 * mm, "Werkstoff: siehe Stückliste")


print("documents")
doc("deckblatt-ppf.pdf", "PPF-Bericht – Deckblatt", "Produktionsprozess- und Produktfreigabe (Beispiel)",
    text_lines(["Lieferant: siehe Vorgang", "Vorlagegrund: siehe Vorgang", "Vorlagestufe: siehe Vorgang",
                "Bestätigung: Die Muster wurden unter Serienbedingungen gefertigt.", "", "Ort, Datum: digital bestätigt im PPAP Connector"]))
doc("messprotokoll.pdf", "Messprotokoll – Prüfergebnisse Maß", "Erstmusterprüfbericht, 5 Teile je Merkmal (Auszug)", measurement)
doc("werkstoffzeugnis.pdf", "Werkstoffprüfzeugnis 3.1", "Prüfergebnisse Werkstoff (Beispiel nach EN 10204)",
    text_lines(["Werkstoff: gemäß Zeichnung", "Zugfestigkeit: 62 MPa (Soll ≥ 55 MPa) – i.O.", "Bruchdehnung: 4,1 % (Soll ≥ 3 %) – i.O.",
                "Glasfaseranteil: 30,4 % (Soll 30 ± 2 %) – i.O.", "Feuchte bei Verarbeitung: 0,12 % – i.O."]))
doc("funktionspruefung.pdf", "Prüfbericht Funktion / Leistung", "Funktions- und Dauerlaufprüfung (Beispiel)",
    text_lines(["Betätigungskraft: 28 N (Soll 20–35 N) – i.O.", "Dauerlauf 50.000 Zyklen: ohne Befund – i.O.",
                "Klimawechseltest −40 °C / +85 °C, 20 Zyklen: ohne Befund – i.O."]))
doc("prozessfaehigkeit.pdf", "Nachweis vorläufige Prozessfähigkeit", "Besondere Merkmale, Stichprobe n = 125", capability)
doc("pruefmittelfaehigkeit.pdf", "Prüfmittelfähigkeitsnachweis", "Verfahren 1 und Messsystemanalyse (Beispiel)", msa)
doc("prozess-fmea.pdf", "Prozess-FMEA (Auszug)", "Bewertung B/A/E, Maßnahmen", fmea, size=landscape(A4))
doc("produktionslenkungsplan.pdf", "Produktionslenkungsplan (Serie)", "Control Plan – Auszug", control_plan, size=landscape(A4))
doc("prozessablaufplan.pdf", "Prozessablaufplan", "Wareneingang → Trocknen → Spritzgießen → Prüfen → Verpacken → Versand",
    text_lines(["10 Wareneingang Granulat", "20 Trocknen", "30 Spritzgießen", "40 Maß- und Sichtprüfung", "50 Verpacken", "60 Versand"]))
doc("zeichnung-beispielteil.pdf", "Zeichnung Beispielteil", "Kundenzeichnung mit Änderungsstand (vereinfacht, fiktiv)", drawing, size=landscape(A4))
doc("imds-nachweis.pdf", "IMDS-Nachweis", "Materialdatenblatt eingereicht und vom Kunden akzeptiert (Beispiel)",
    text_lines(["IMDS-ID: 123456789 (fiktiv)", "Version: 2", "Status: akzeptiert"]))
