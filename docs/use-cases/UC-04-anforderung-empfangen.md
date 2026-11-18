# UC-04 Anforderung empfangen (Lieferant)

**Primärakteur:** System (Empfangsendpunkt hinter dem eigenen EDC); sekundär QV-L
**Ziel:** Eingehende Anforderungen erscheinen zuverlässig, korrekt zugeordnet im Arbeitsvorrat.

## Vorbedingungen
- Eigener EDC bietet das Notification-Receive-Asset an; Access Policy erlaubt dem Kunden den Zugriff.

## Hauptablauf
1. Partner-Connector stellt `PpapRequest` über die Data Plane des eigenen EDC zu (Muster „Push über Notifications“).
2. Empfangsendpunkt prüft: Herkunft (vom EDC weitergereichte BPN des Consumers bzw. Header/Agreement – siehe ADR-0002), Schema-Validität, Idempotenz (Message-ID bereits verarbeitet → 200 ohne Wirkung).
3. Nachricht wird zuerst unverändert in der **Inbox** gespeichert (Payload + SHA-256), danach asynchron verarbeitet.
4. Zuordnung: Kunden-BPNL → Partner; Kundensachnummer → `PartMapping` → eigenes Teil.
5. System legt lokalen `ApprovalCase` (Rolle: Lieferant, Status `ANGEFORDERT`) an, verknüpft mit der Fremd-Vorgangs-ID des Kunden.
6. UI-Benachrichtigung (SSE) und Eintrag im Arbeitsvorrat.

## Alternativabläufe
- **A1 Teil nicht zuordenbar:** Vorgang wird angelegt, aber in die **Klärungsliste** gestellt; QV-L ordnet manuell zu (und kann das Mapping speichern).
- **A2 Absender unbekannt / keine Beziehung:** Nachricht wird in der Inbox als `REJECTED_UNKNOWN_PARTNER` abgelegt, CA sieht sie im Cockpit; Antwort an Sender 2xx (Zustellung technisch erfolgt), fachlich nicht übernommen. `TODO(EG)`: fachliche Negativ-Quittung standardisieren.
- **A3 Schema ungültig:** Antwort 400 mit Fehlerliste; Inbox-Eintrag `INVALID`.

## Akzeptanzkriterien
- AK-04-1 Doppelte Zustellung derselben Message-ID erzeugt genau einen Vorgang.
- AK-04-2 Nicht zuordenbare Anforderungen landen in der Klärungsliste, nicht im Nirwana.
- AK-04-3 Empfang ist nur über die Data Plane des eigenen EDC möglich (Endpunkt nicht öffentlich exponiert, Netzwerksegmentierung + Shared-Secret/Header des Data-Plane-Proxys).
- AK-04-4 UI aktualisiert sich ohne Reload (SSE) innerhalb 2 s nach Verarbeitung.
