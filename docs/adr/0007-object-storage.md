# ADR-0007 Object Storage

## Kontext
Nachweisdokumente (PDF, Bilder, XLSX) werden außerhalb der Datenbank gespeichert. Vorgabe war MinIO. Recherche (11/2026): Das MinIO-Community-Repository ist als „no longer maintained“ markiert, es werden keine Binaries/Docker-Images mehr veröffentlicht; Nachfolger „AIStor“ ist proprietär lizenziert.

## Entscheidung
- Der Code spricht **ausschließlich die S3-API** über das **AWS SDK v2** (Port `DocumentStorage`, Adapter `S3DocumentStorage`; Path-Style-Zugriff konfigurierbar).
- Lokal und in Testcontainers: **SeaweedFS** (Apache-2.0, aktiv gepflegt) mit S3-Gateway.
- Produktion/Kunde: beliebiger S3-kompatibler Speicher (AWS S3, Azure via Gateway, Ceph RGW, NetApp StorageGRID, AIStor …).
- Objekte sind inhaltsadressiert (`<tenant>/<sha256>`), unveränderlich (keine Überschreibung), Metadaten in Postgres.

## Alternativen
- *MinIO letzte Community-Version*: ungepflegt, Sicherheitsrisiko.
- *Garage*: leichtgewichtig, aber AGPL.
- *Dokumente in Postgres (bytea/Large Objects)*: einfach, skaliert schlecht, Backups groß.

## Konsequenzen
+ Herstellerneutral, keine Lizenzrisiken. − SeaweedFS-Konfiguration minimal anders als MinIO (S3-Config-Datei für Credentials).
