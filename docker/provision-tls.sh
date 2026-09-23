#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
certs_dir="$script_dir/certs"
server_name="${SERVER_NAME:-localhost}"
keystore_password="${KEYSTORE_PASSWORD:-changeit}"

if [ -e "$certs_dir/server.p12" ] || [ -e "$certs_dir/server.crt" ]; then
    echo "Refusing to overwrite existing TLS certificates in $certs_dir" >&2
    exit 1
fi

case "$server_name" in
    *:*)
        echo "SERVER_NAME must not include a port" >&2
        exit 1
        ;;
esac

if printf '%s' "$server_name" | grep -Eq '^[0-9a-fA-F:.]+$'; then
    subject_alt_name="IP:$server_name"
else
    subject_alt_name="DNS:$server_name"
fi

temporary_dir=$(mktemp -d)
trap 'rm -rf "$temporary_dir"' EXIT HUP INT TERM
mkdir -p "$certs_dir"

openssl req -x509 -newkey rsa:2048 -sha256 -nodes \
    -keyout "$temporary_dir/server.key" \
    -out "$certs_dir/server.crt" \
    -days 825 \
    -subj "/CN=$server_name" \
    -addext "subjectAltName=$subject_alt_name" \
    -addext "keyUsage=digitalSignature,keyEncipherment" \
    -addext "extendedKeyUsage=serverAuth"

openssl pkcs12 -export \
    -name localhost \
    -inkey "$temporary_dir/server.key" \
    -in "$certs_dir/server.crt" \
    -out "$certs_dir/server.p12" \
    -passout "pass:$keystore_password"

echo "TLS certificates written to $certs_dir"
