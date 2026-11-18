#!/usr/bin/env bash
# 用途：用最小普通/生成 Java 与 POM fixture 验证 checker 的正例、反例和误报保护。
# 输入：无位置参数；fixture 在临时目录内生成。
# 输出/副作用：打印 checker 自测结果并在退出时删除临时目录。
# 依赖：bash、mktemp、check-java-style.sh 及其 Ruby 运行环境。
# 退出码：全部规则产生预期诊断且合法样例通过时返回 0，否则返回非 0。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fixture_root="$(mktemp -d)"
trap 'rm -rf "$fixture_root"' EXIT

mkdir -p "$fixture_root/module/src/main/java/com/co/kc/imchat/example"

cat > "$fixture_root/module/src/main/java/com/co/kc/imchat/example/AcceptedModels.java" <<'EOF'
package com.co.kc.imchat.example;

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

http_package="$fixture_root/module/src/main/java/com/co/kc/imchat/example/interfaces/http"
mkdir -p "$http_package"
cat > "$http_package/AcceptedController.java" <<'EOF'
package com.co.kc.imchat.example.interfaces.http;

class AcceptedController {
    void create(@RequestBody CreateUserRequest request) {
    }
}

record CreateUserRequest(String username) {
}
EOF

"$ROOT_DIR/scripts/check-java-style.sh" "$fixture_root"

cat > "$http_package/InvalidController.java" <<'EOF'
package com.co.kc.imchat.example.interfaces.http;

class InvalidController {
    void ingest(@RequestBody AuditEvent event) {
    }
}

record AuditEvent(String auditId) {
}
EOF

if output="$("$ROOT_DIR/scripts/check-java-style.sh" "$fixture_root" 2>&1)"; then
  printf 'Expected HTTP request body without Request suffix to fail.\n' >&2
  exit 1
fi
if [[ "$output" != *"HTTP request body types must use the Request suffix"* ]]; then
  printf 'Expected HTTP Request naming guidance, got:\n%s\n' "$output" >&2
  exit 1
fi
rm -rf "$http_package"

rpc_package="$fixture_root/module/src/main/java/com/co/kc/imchat/example/sdk/rpc"
mkdir -p "$rpc_package"
cat > "$rpc_package/InvalidRpcService.java" <<'EOF'
package com.co.kc.imchat.example.sdk.rpc;

interface InvalidRpcService {
    void submit(AuditEvent event);
}

record AuditEvent(String auditId) {
}
EOF

if output="$("$ROOT_DIR/scripts/check-java-style.sh" "$fixture_root" 2>&1)"; then
  printf 'Expected SDK RPC input without Params suffix to fail.\n' >&2
  exit 1
fi
if [[ "$output" != *"RPC contract input types must use the Params suffix"* ]]; then
  printf 'Expected RPC Params naming guidance, got:\n%s\n' "$output" >&2
  exit 1
fi
rm -rf "$rpc_package"

facade_query_package="$fixture_root/sample-facade/src/main/java/com/co/kc/imchat/example/facade/query"
mkdir -p "$facade_query_package"
cat > "$facade_query_package/UserQuery.java" <<'EOF'
package com.co.kc.imchat.example.facade.query;

public record UserQuery(Long userId) {
}
EOF

if output="$($ROOT_DIR/scripts/check-java-style.sh "$fixture_root" 2>&1)"; then
  printf 'Expected Facade command/query packages to fail.\n' >&2
  exit 1
fi
if [[ "$output" != *"Facade request contracts must use a params package"* ]]; then
  printf 'Expected Facade params-package guidance, got:\n%s\n' "$output" >&2
  exit 1
fi
rm -rf "$fixture_root/sample-facade"

mixed_package="$fixture_root/module/src/main/java/com/co/kc/imchat/example/mixed"
mkdir -p "$mixed_package"
for type in TokenService JwtTokenCodec TokenDTO TokenClaims TokenType TokenStatus; do
  cat > "$mixed_package/$type.java" <<EOF
package com.co.kc.imchat.example.mixed;

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

cat > "$fixture_root/module/src/main/java/com/co/kc/imchat/example/ManualBean.java" <<'EOF'
package com.co.kc.imchat.example;

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
rm "$fixture_root/module/src/main/java/com/co/kc/imchat/example/ManualBean.java"

application_package="$fixture_root/module/src/main/java/com/co/kc/imchat/example/application"
mkdir -p "$application_package"
cat > "$application_package/ValidAppService.java" <<'EOF'
package com.co.kc.imchat.example.application;

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
package com.co.kc.imchat.example.application;

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
rm "$application_package/InvalidAppService.java"

cat > "$application_package/InvalidLockKeyAppService.java" <<'EOF'
package com.co.kc.imchat.example.application;

class InvalidLockKeyAppService {
    @DistributeLock(scene = "im:account:user:write", key = "#command.userId().toString()")
    public void update(UpdateUserCmd command) {
    }
}

record UpdateUserCmd(Long userId) {
}
EOF

if output="$($ROOT_DIR/scripts/check-java-style.sh "$fixture_root" 2>&1)"; then
  printf 'Expected redundant toString in declarative lock key to fail.\n' >&2
  exit 1
fi
if [[ "$output" != *"Declarative lock keys must rely on the lock aspect string conversion"* ]]; then
  printf 'Expected declarative lock key guidance, got:\n%s\n' "$output" >&2
  exit 1
fi
rm "$application_package/InvalidLockKeyAppService.java"

plugin_config_package="$fixture_root/im-plugin/sample/src/main/java/com/co/kc/imchat/example/config"
mkdir -p "$plugin_config_package"
cat > "$plugin_config_package/OptionalPluginConfig.java" <<'EOF'
package com.co.kc.imchat.example.config;

class OptionalPluginConfig {
    @ConditionalOnProperty(prefix = "example.plugin", name = "enabled")
    Object optionalComponent() {
        return new Object();
    }
}
EOF

"$ROOT_DIR/scripts/check-java-style.sh" "$fixture_root"

business_config_package="$fixture_root/im-service/sample/sample-server/src/main/java/com/co/kc/imchat/example/config"
mkdir -p "$business_config_package"
cat > "$fixture_root/im-service/sample/sample-server/src/main/java/com/co/kc/imchat/example/SampleApplication.java" <<'EOF'
package com.co.kc.imchat.example;

@SpringBootApplication
class SampleApplication {
}
EOF
cat > "$business_config_package/InvalidBusinessConfig.java" <<'EOF'
package com.co.kc.imchat.example.config;

@ConditionalOnProperty(prefix = "example.business", name = "enabled")
class InvalidBusinessConfig {
}
EOF

if output="$("$ROOT_DIR/scripts/check-java-style.sh" "$fixture_root" 2>&1)"; then
  printf 'Expected conditional property activation in a deployable application to fail.\n' >&2
  exit 1
fi
if [[ "$output" != *"Deployable applications must fail fast instead of conditionally disabling production beans"* ]]; then
  printf 'Expected fail-fast application bean guidance, got:\n%s\n' "$output" >&2
  exit 1
fi
rm -rf "$fixture_root/im-service"

repository_package="$fixture_root/module/src/main/java/com/co/kc/imchat/example/infrastructure/domain/repository"
mkdir -p "$repository_package"
cat > "$repository_package/InvalidUserRepository.java" <<'EOF'
package com.co.kc.imchat.example.infrastructure.domain.repository;

import com.co.kc.imchat.example.infrastructure.mybatis.mapper.DbUserMapper;

class InvalidUserRepository {
    private DbUserMapper mapper;
}
EOF

if output="$("$ROOT_DIR/scripts/check-java-style.sh" "$fixture_root" 2>&1)"; then
  printf 'Expected Repository direct Mapper dependency to fail.\n' >&2
  exit 1
fi
if [[ "$output" != *"must use MyBatis Service"* ]]; then
  printf 'Expected MyBatis Service guidance, got:\n%s\n' "$output" >&2
  exit 1
fi
rm -rf "$repository_package"

mapper_package="$fixture_root/module/src/main/java/com/co/kc/imchat/example/infrastructure/mybatis/mapper"
config_package="$fixture_root/module/src/main/java/com/co/kc/imchat/example/infrastructure/config"
mkdir -p "$mapper_package" "$config_package"
cat > "$mapper_package/DbUserMapper.java" <<'EOF'
package com.co.kc.imchat.example.infrastructure.mybatis.mapper;

@Mapper
interface DbUserMapper {
}
EOF
cat > "$config_package/RedundantMapperScanConfig.java" <<'EOF'
package com.co.kc.imchat.example.infrastructure.config;

@Configuration
@MapperScan("com.co.kc.imchat.example.infrastructure.mybatis.mapper")
class RedundantMapperScanConfig {
}
EOF

if output="$("$ROOT_DIR/scripts/check-java-style.sh" "$fixture_root" 2>&1)"; then
  printf 'Expected redundant MapperScan configuration to fail.\n' >&2
  exit 1
fi
if [[ "$output" != *"remove the redundant empty @MapperScan configuration"* ]]; then
  printf 'Expected MapperScan deduplication guidance, got:\n%s\n' "$output" >&2
  exit 1
fi
rm -rf "$mapper_package" "$config_package"

aggregate_package="$fixture_root/module/src/main/java/com/co/kc/imchat/example/domain/model"
mkdir -p "$aggregate_package"
cat > "$aggregate_package/Identification.java" <<'EOF'
package com.co.kc.imchat.example.domain.model;

class Identification {
}
EOF
cat > "$aggregate_package/AcceptedAggregate.java" <<'EOF'
package com.co.kc.imchat.example.domain.model;

class AcceptedAggregate extends Identification {
    private final Long id = 1L;
}
EOF

"$ROOT_DIR/scripts/check-java-style.sh" "$fixture_root"

cat > "$aggregate_package/InvalidAggregate.java" <<'EOF'
package com.co.kc.imchat.example.domain.model;

@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
class InvalidAggregate extends Identification {
    @EqualsAndHashCode.Include
    private final Long id = 1L;
}
EOF

if output="$("$ROOT_DIR/scripts/check-java-style.sh" "$fixture_root" 2>&1)"; then
  printf 'Expected aggregate-specific equality customization to fail.\n' >&2
  exit 1
fi
if [[ "$output" != *"Aggregates extending Identification must not define business-ID-only equality"* ]]; then
  printf 'Expected shared aggregate equality guidance, got:\n%s\n' "$output" >&2
  exit 1
fi
rm -rf "$aggregate_package"

db_entity_package="$fixture_root/module/src/main/java/com/co/kc/imchat/example/infrastructure/mybatis/entity"
mkdir -p "$db_entity_package"
cat > "$db_entity_package/AcceptedDbEvent.java" <<'EOF'
package com.co.kc.imchat.example.infrastructure.mybatis.entity;

class AcceptedDbEvent {
    private DbEventAction action;
    private String targetType;
}

enum DbEventAction {
    CREATED
}
EOF

"$ROOT_DIR/scripts/check-java-style.sh" "$fixture_root"

cat > "$db_entity_package/InvalidDbEvent.java" <<'EOF'
package com.co.kc.imchat.example.infrastructure.mybatis.entity;

class InvalidDbEvent {
    private String action;
}
EOF

if output="$("$ROOT_DIR/scripts/check-java-style.sh" "$fixture_root" 2>&1)"; then
  printf 'Expected closed-set database fields represented as String to fail.\n' >&2
  exit 1
fi
if [[ "$output" != *"Closed-set database fields must use database-layer enums"* ]]; then
  printf 'Expected database enum guidance, got:\n%s\n' "$output" >&2
  exit 1
fi
for label in 'WHAT:' 'WHY:' 'FIX:'; do
  if [[ "$output" != *"$label"* ]]; then
    printf 'Expected Java style diagnostic to include %s, got:\n%s\n' "$label" "$output" >&2
    exit 1
  fi
done
rm "$db_entity_package/InvalidDbEvent.java"

cat > "$db_entity_package/InvalidDbUser.java" <<'EOF'
package com.co.kc.imchat.example.infrastructure.mybatis.entity;

class InvalidDbUser {
    private Long id;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long isDeleted;
}
EOF

if output="$("$ROOT_DIR/scripts/check-java-style.sh" "$fixture_root" 2>&1)"; then
  printf 'Expected duplicated BaseEntity fields to fail.\n' >&2
  exit 1
fi
if [[ "$output" != *"Entities with the standard persistence fields must extend BaseEntity"* ]]; then
  printf 'Expected BaseEntity guidance, got:\n%s\n' "$output" >&2
  exit 1
fi

# 每个所有权样例独立，避免其他结构规则掩盖 namespace／词法诊断。
for scenario in accepted default wrong-path duplicate unicode module-info comment-string text-block package-info; do
  case_root="$fixture_root/ownership-$scenario"
  source_dir="$case_root/module/src/main/java/com/co/kc/imchat/sample"
  mkdir -p "$source_dir"
  case "$scenario" in
    accepted) source='package com.co.kc.imchat.sample; class Sample {}' ;;
    default) source='class Sample {}' ;;
    wrong-path) source='package com.co.kc.imchat.other; class Sample {}' ;;
    duplicate) source='package com.co.kc.imchat.sample; package com.co.kc.imchat.sample; class Sample {}' ;;
    unicode) source='package com.co.kc.imchat.sample; class Sample { String value = "package"; }' ;;
    module-info) source='module sample {}' ;;
    comment-string) source='/* package foreign; */ package com.co.kc.imchat.sample; class Sample { String value = "package foreign;"; char marker = '\''"'\''; }' ;;
    text-block) source='package com.co.kc.imchat.sample; class Sample { String value = """
package foreign;
"""; }' ;;
    package-info) source='/** package foreign; */ package com.co.kc.imchat.sample;' ;;
  esac
  filename=Sample.java
  [[ "$scenario" != module-info && "$scenario" != package-info ]] || filename="$scenario.java"
  printf '%s\n' "$source" > "$source_dir/$filename"
  if [[ "$scenario" == unicode ]]; then
    printf '// \\%s0070ackage foreign;\n' 'u' >> "$source_dir/$filename"
  fi
  case "$scenario" in
    accepted|comment-string|text-block|package-info)
      "$ROOT_DIR/scripts/check-java-style.sh" "$case_root" ;;
    *)
      if output="$("$ROOT_DIR/scripts/check-java-style.sh" "$case_root" 2>&1)"; then
        printf 'Expected ownership scenario %s to fail.\n' "$scenario" >&2
        exit 1
      fi
      [[ "$output" == *"Production source ownership"* ]] || { printf 'Missing ownership diagnostic for %s.\n' "$scenario" >&2; exit 1; } ;;
  esac
done

# 所有权反例独立于前面的结构反例，确保失败只能来自 namespace 边界。
ownership_root="$fixture_root/ownership-lookalike"
mkdir -p "$ownership_root/module/src/main/java/com/co/kc/imchatOther"
cat > "$ownership_root/module/src/main/java/com/co/kc/imchatOther/Foreign.java" <<'EOF'
package com.co.kc.imchatOther;
class Foreign {}
EOF
if output="$("$ROOT_DIR/scripts/check-java-style.sh" "$ownership_root" 2>&1)"; then
  printf 'Expected lookalike production namespace to fail.\n' >&2
  exit 1
fi
if [[ "$output" != *"Production source ownership"* ]]; then
  printf 'Expected production ownership guidance.\n' >&2
  exit 1
fi

for scenario in accepted lookalike default wrong-path duplicate unicode module-info comment-string text-block package-info; do
  case_root="$fixture_root/generated-$scenario"
  source_dir="$case_root/module/target/generated-sources/annotations/com/co/kc/imchat/sample"
  mkdir -p "$source_dir"
  case "$scenario" in
    accepted) source='package com.co.kc.imchat.sample; class Generated {}' ;;
    lookalike) source='package com.co.kc.imchatOther; class Generated {}' ;;
    default) source='class Generated {}' ;;
    wrong-path) source='package com.co.kc.imchat.other; class Generated {}' ;;
    duplicate) source='package com.co.kc.imchat.sample; package com.co.kc.imchat.sample; class Generated {}' ;;
    unicode) source='package com.co.kc.imchat.sample; class Generated {}' ;;
    module-info) source='module sample {}' ;;
    comment-string) source='/* package foreign; */ package com.co.kc.imchat.sample; class Generated { String value = "package foreign;"; }' ;;
    text-block) source='package com.co.kc.imchat.sample; class Generated { String value = """
package foreign;
"""; }' ;;
    package-info) source='/** package foreign; */ package com.co.kc.imchat.sample;' ;;
  esac
  filename=Generated.java
  [[ "$scenario" != module-info && "$scenario" != package-info ]] || filename="$scenario.java"
  printf '%s\n' "$source" > "$source_dir/$filename"
  if [[ "$scenario" == unicode ]]; then
    printf '// \\%s0070ackage foreign;\n' 'u' >> "$source_dir/$filename"
  fi
  case "$scenario" in
    accepted|comment-string|text-block|package-info)
      "$ROOT_DIR/scripts/check-java-style.sh" "$case_root" ;;
    *)
      if output="$("$ROOT_DIR/scripts/check-java-style.sh" "$case_root" 2>&1)"; then
        printf 'Expected generated ownership scenario %s to fail.\n' "$scenario" >&2
        exit 1
      fi
      [[ "$output" == *"Production source ownership"* ]] || { printf 'Missing generated ownership diagnostic for %s.\n' "$scenario" >&2; exit 1; } ;;
  esac
done

printf 'Java style Harness tests passed.\n'
