#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
certs_dir="$script_dir/certs"
oid4vp_client_id="${OID4VP_CLIENT_ID:-localhost}"
keystore_password="${KEYSTORE_PASSWORD:-changeit}"

for certificate_file in trust-root-ca.crt trust-root-ca.der issuer.p12 oid4vp-verifier.p12; do
    if [ -e "$certs_dir/$certificate_file" ]; then
        echo "Refusing to overwrite existing trust certificates in $certs_dir" >&2
        exit 1
    fi
done

temporary_dir=$(mktemp -d)
trap 'rm -rf "$temporary_dir"' EXIT HUP INT TERM
mkdir -p "$certs_dir"

create_root() {
    openssl ecparam -name prime256v1 -genkey -noout -out "$temporary_dir/root-ca.key"

    openssl req -x509 -new -sha256 \
        -key "$temporary_dir/root-ca.key" \
        -out "$certs_dir/trust-root-ca.crt" \
        -days 3650 \
        -subj "/CN=EUDI Golf Demo" \
        -addext "basicConstraints=critical,CA:TRUE" \
        -addext "keyUsage=critical,keyCertSign,cRLSign" \
        -addext "subjectKeyIdentifier=hash"

    openssl x509 -in "$certs_dir/trust-root-ca.crt" -outform DER \
        -out "$certs_dir/trust-root-ca.der"
}

create_identity() (
    identity_name="$1"
    common_name="$2"
    key_alias="$3"
    subject_alt_name="${4:-}"

    openssl ecparam -name prime256v1 -genkey -noout \
        -out "$temporary_dir/$identity_name.key"

    openssl req -new -sha256 \
        -key "$temporary_dir/$identity_name.key" \
        -out "$temporary_dir/$identity_name.csr" \
        -subj "/CN=$common_name"

    printf '%s\n' \
        'basicConstraints=critical,CA:FALSE' \
        'keyUsage=critical,digitalSignature' \
        'subjectKeyIdentifier=hash' \
        'authorityKeyIdentifier=keyid,issuer' > "$temporary_dir/$identity_name.ext"

    if [ -n "$subject_alt_name" ]; then
        printf 'subjectAltName=%s\n' "$subject_alt_name" \
            >> "$temporary_dir/$identity_name.ext"
    fi

    openssl x509 -req -sha256 \
        -in "$temporary_dir/$identity_name.csr" \
        -CA "$certs_dir/trust-root-ca.crt" \
        -CAkey "$temporary_dir/root-ca.key" \
        -CAcreateserial \
        -out "$temporary_dir/$identity_name.crt" \
        -days 825 \
        -extfile "$temporary_dir/$identity_name.ext"

    openssl pkcs12 -export \
        -name "$key_alias" \
        -inkey "$temporary_dir/$identity_name.key" \
        -in "$temporary_dir/$identity_name.crt" \
        -certfile "$certs_dir/trust-root-ca.crt" \
        -out "$certs_dir/$identity_name.p12" \
        -passout "pass:$keystore_password"
)

create_root
create_identity issuer "National Golf Association" issuer
create_identity oid4vp-verifier "Golf Booking Platform" oid4vp-verifier "DNS:$oid4vp_client_id"

echo "Certificates written to $certs_dir"
