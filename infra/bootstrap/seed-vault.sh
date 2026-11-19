#!/bin/sh
# Seeds the secrets a Tractus-X EDC participant needs into its OpenBao/Vault (KV v2 at secret/):
#   client-secret          STS client secret (the wallet stub does not validate it)
#   tokenSignerPrivateKey  EC P-256 private key (PEM) the data plane uses to sign EDR tokens
#   tokenSignerPublicKey   matching public key (PEM)
#   ppap-signing-key       EC P-256 private key (PEM, PKCS#8) the connector app uses to sign submissions/decisions (JWS, ADR-0008)
# Idempotent: existing keys are kept so EDR tokens survive restarts.
set -eu
: "${VAULT_ADDR:?}" "${VAULT_TOKEN:?}" "${STS_CLIENT_SECRET:?}"

until curl -sf "$VAULT_ADDR/v1/sys/health" >/dev/null; do echo "waiting for $VAULT_ADDR"; sleep 1; done

exists() { curl -sf -H "X-Vault-Token: $VAULT_TOKEN" "$VAULT_ADDR/v1/secret/data/$1" >/dev/null; }
# JSON-escape a value (PEM newlines -> \n); values never contain quotes or backslashes
esc() { printf '%s' "$1" | awk 'BEGIN{ORS="\\n"} {print}' | sed 's/\\n$//'; }
put() { printf '{"data":{"content":"%s"}}' "$(esc "$2")" | curl -sf -X POST -H "X-Vault-Token: $VAULT_TOKEN" \
          -H 'Content-Type: application/json' --data @- "$VAULT_ADDR/v1/secret/data/$1" >/dev/null; echo "stored $1"; }

exists client-secret || put client-secret "$STS_CLIENT_SECRET"
if ! exists tokenSignerPrivateKey; then
  openssl ecparam -name prime256v1 -genkey -noout | openssl pkcs8 -topk8 -nocrypt -out /tmp/key.pem
  openssl ec -in /tmp/key.pem -pubout -out /tmp/pub.pem 2>/dev/null
  put tokenSignerPrivateKey "$(cat /tmp/key.pem)"
  put tokenSignerPublicKey "$(cat /tmp/pub.pem)"
fi
if ! exists ppap-signing-key; then
  openssl ecparam -name prime256v1 -genkey -noout | openssl pkcs8 -topk8 -nocrypt -out /tmp/app-key.pem
  put ppap-signing-key "$(cat /tmp/app-key.pem)"
fi
echo "vault $VAULT_ADDR seeded"
