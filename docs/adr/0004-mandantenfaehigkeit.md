# ADR-0004 Mandantenfähigkeit und Keycloak-Realms

## Kontext
Jeder Teilnehmer (OEM, Tier-1, Tier-2) braucht eine eigene Connector-Lizenz; große Unternehmen betreiben „ihren“ Connector, kleine Zulieferer würden ihn eher gehostet nutzen (mehrere Mandanten auf einer Instanz). Im Prototyp soll der Datenraum ehrlich simuliert werden: kein gemeinsamer Datenbestand zwischen Teilnehmern.

## Entscheidung
1. **Deployment-Modell im Prototyp: eine Instanz je Teilnehmer** (eigenes Backend, eigene Datenbank, eigener EDC, gleiches Image, andere Konfiguration). Damit ist ein Datenabfluss außerhalb von EDC-Transfers architektonisch ausgeschlossen.
2. **Software mandantenfähig vorbereitet:** Jede fachliche Tabelle trägt `tenant_id`; Zugriff ausschließlich über Repositories, die den Mandanten aus dem Sicherheitskontext setzen; zusätzlich **PostgreSQL Row-Level-Security** (`app.tenant_id` Session-Variable) als zweite Verteidigungslinie. Im Prototyp gibt es genau einen Mandanten je Instanz – Multi-Mandanten-Betrieb (Hosting) ist später nur Konfiguration (L-03).
3. **Keycloak: ein Realm je Teilnehmer** (`oem`, `tier1`, `tier2`) in **einer** Keycloak-Instanz. Begründung: Teilnehmer sind unterschiedliche Unternehmen mit eigener Benutzerverwaltung/IdP-Föderation; Realm-Grenze = Vertrauensgrenze; Realm-Export je Teilnehmer ist einfach portierbar zu einem kundeneigenen IdP. Rollen als Realm-Rollen: `CUSTOMER_QUALITY_ENGINEER`, `SUPPLIER_QUALITY_MANAGER`, `MASTERDATA_MANAGER`, `CONNECTOR_ADMIN`; technischer Client (Client-Credentials) mit Scopes für SYS.
4. Beim späteren Hosting mehrerer Mandanten auf einer Instanz: Realm je Mandant, Backend validiert Token gegen mehrere Issuer (Issuer → Mandant-Mapping).

## Alternativen
- *Ein Realm, Mandant als Claim/Gruppe*: weniger Realms, aber vermischt Benutzer verschiedener Unternehmen; Föderation mit Firmen-IdPs komplizierter.
- *Eine Instanz für alle Teilnehmer im Prototyp*: spart RAM, simuliert den Datenraum aber nicht glaubhaft und verführt zu Abkürzungen am EDC vorbei.

## Konsequenzen
+ Glaubhafte Datenraum-Simulation, strikte Trennung.
− Mehr Container/RAM (3× Backend + DB-Schema je Teilnehmer; eine Postgres-Instanz mit mehreren Datenbanken mildert das).
