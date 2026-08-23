#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fixture_root="$(mktemp -d)"
trap 'rm -rf "$fixture_root"' EXIT

mkdir -p "$fixture_root/module/src/main/java/example"

cat > "$fixture_root/module/src/main/java/example/AcceptedModels.java" <<'EOF'
package example;

import lombok.Getter;
import lombok.Setter;

record ImmutableSettings(String issuer, int timeout) {
}

@Getter
@Setter
class MutableSettings {
    private String issuer;
    private int timeout;
}

class ValidatedValue {
    private final String value;

    ValidatedValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        this.value = value;
    }

    String normalizedValue() {
        return value.trim();
    }
}
EOF

"$ROOT_DIR/scripts/check-java-style.sh" "$fixture_root"

mixed_package="$fixture_root/module/src/main/java/example/mixed"
mkdir -p "$mixed_package"
for type in TokenService JwtTokenCodec TokenDTO TokenClaims TokenType TokenStatus; do
  cat > "$mixed_package/$type.java" <<EOF
package example.mixed;

class $type {
}
EOF
done

if output="$("$ROOT_DIR/scripts/check-java-style.sh" "$fixture_root" 2>&1)"; then
  printf 'Expected a package mixing contracts, implementations and models to fail.\n' >&2
  exit 1
fi
if [[ "$output" != *"package mixes contracts, implementations and models"* ]]; then
  printf 'Expected package responsibility guidance, got:\n%s\n' "$output" >&2
  exit 1
fi
rm -rf "$mixed_package"

cat > "$fixture_root/module/src/main/java/example/ManualBean.java" <<'EOF'
package example;

class ManualBean {
    private String issuer;
    private int timeout;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
}
EOF

if output="$("$ROOT_DIR/scripts/check-java-style.sh" "$fixture_root" 2>&1)"; then
  printf 'Expected simple handwritten JavaBean accessors to fail.\n' >&2
  exit 1
fi
if [[ "$output" != *"use Lombok or a record"* ]]; then
  printf 'Expected Lombok guidance, got:\n%s\n' "$output" >&2
  exit 1
fi
rm "$fixture_root/module/src/main/java/example/ManualBean.java"

application_package="$fixture_root/module/src/main/java/example/application"
mkdir -p "$application_package"
cat > "$application_package/ValidAppService.java" <<'EOF'
package example.application;

class ValidAppService {
    public void signIn(SignInCmd command) {
    }

    public SignInResult refresh(RefreshTokenParams params) {
        return null;
    }
}

record SignInCmd(String email) {
}

record RefreshTokenParams(String token) {
}

record SignInResult(String token) {
}
EOF

"$ROOT_DIR/scripts/check-java-style.sh" "$fixture_root"

cat > "$application_package/InvalidAppService.java" <<'EOF'
package example.application;

class InvalidAppService {
    public SignInResult refresh(String refreshToken) {
        return null;
    }
}

record SignInResult(String token) {
}
EOF

if output="$($ROOT_DIR/scripts/check-java-style.sh "$fixture_root" 2>&1)"; then
  printf 'Expected scalar application service parameters to fail.\n' >&2
  exit 1
fi
if [[ "$output" != *"must accept CQRS command/query objects"* ]]; then
  printf 'Expected CQRS application service guidance, got:\n%s\n' "$output" >&2
  exit 1
fi

printf 'Java style Harness tests passed.\n'
