#!/usr/bin/env bash
# Gera os pares RSA 2048 de assinatura JWT (dev e prod) em keys/ — idempotente.
# Decisao do dono (MVP): chaves commitadas; ver docs/proposta-auth-onboarding.md C.1.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p keys
gen() {
  local env="$1"
  local priv="keys/jwt-private-${env}.pem"
  local pub="keys/jwt-public-${env}.pem"
  if [ -f "$priv" ] && [ -f "$pub" ]; then
    echo "keys ${env} ja existem — nada a fazer"
    return 0
  fi
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$priv"
  openssl pkey -in "$priv" -pubout -out "$pub"
  echo "geradas ${priv} / ${pub}"
}
gen dev
gen prod
