#!/usr/bin/env bash
# 用途：验证 startup smoke 会枚举全部应用、拒绝缺失/重复映射，并拒绝 Maven 零测试假通过。
# 输入：无；测试在临时多模块目录中构造两个 Spring Boot application 和 fake Maven。
# 输出/副作用：仅创建并删除临时目录，成功时输出通过信息。
# 依赖：待测 harness-startup.sh、Ruby、rg 和基础 Shell 工具。
# 退出码：任一 startup 契约不满足时返回非 0，全部 fixture 通过时返回 0。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE_ROOT="$(mktemp -d)"
trap 'rm -rf "$FIXTURE_ROOT"' EXIT

mkdir -p "$FIXTURE_ROOT/scripts" "$FIXTURE_ROOT/bin"
cp "$ROOT_DIR/scripts/harness-startup.sh" "$FIXTURE_ROOT/scripts/harness-startup.sh"
chmod +x "$FIXTURE_ROOT/scripts/harness-startup.sh"

# 参数依次为模块路径和应用类名；同时创建唯一、显式隔离外部发现的 startup smoke。
write_application() {
  local module="$1"
  local application="$2"
  mkdir -p "$FIXTURE_ROOT/$module/src/main/java/example" \
    "$FIXTURE_ROOT/$module/src/test/java/example"
  printf '<project/>\n' > "$FIXTURE_ROOT/$module/pom.xml"
  cat > "$FIXTURE_ROOT/$module/src/main/java/example/$application.java" <<EOF
package example;
@org.springframework.boot.autoconfigure.SpringBootApplication
public class $application {
}
EOF
  cat > "$FIXTURE_ROOT/$module/src/test/java/example/${application}Test.java" <<EOF
package example;
@org.junit.jupiter.api.Tag("startup-smoke")
@org.springframework.boot.test.context.SpringBootTest(classes = $application.class, properties = {
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.cloud.nacos.config.enabled=false"
})
class ${application}Test {
  @org.junit.jupiter.api.Test void contextLoads() {}
}
EOF
}

write_application module-a AlphaApplication
write_application module-b BetaApplication

cat > "$FIXTURE_ROOT/bin/mvn" <<'EOF'
#!/usr/bin/env bash
printf 'mvn %s\n' "$*" >> "$STARTUP_FIXTURE_TRACE"
if [[ " $* " == *" -Dgroups=startup-smoke "* && "${STARTUP_FIXTURE_ZERO_TESTS:-0}" != "1" ]]; then
  for module in module-a module-b; do
    application="AlphaApplication"
    [[ "$module" == "module-b" ]] && application="BetaApplication"
    mkdir -p "$STARTUP_FIXTURE_ROOT/$module/target/surefire-reports"
    cat > "$STARTUP_FIXTURE_ROOT/$module/target/surefire-reports/TEST-example.${application}Test.xml" <<XML
<testsuite name="example.${application}Test" tests="1" failures="0" errors="0" skipped="0"/>
XML
  done
fi
EOF
chmod +x "$FIXTURE_ROOT/bin/mvn"

trace="$FIXTURE_ROOT/startup-trace.log"
if ! startup_output="$(HARNESS_ROOT_DIR="$FIXTURE_ROOT" STARTUP_FIXTURE_ROOT="$FIXTURE_ROOT" \
    STARTUP_FIXTURE_TRACE="$trace" PATH="$FIXTURE_ROOT/bin:$PATH" \
    "$FIXTURE_ROOT/scripts/harness-startup.sh" 2>&1)"; then
  printf 'Startup fixture with two mapped applications should pass.\n%s\n' \
    "$startup_output" >&2
  exit 1
fi
if [[ "$(<"$trace")" != *'-Dgroups=startup-smoke test'* ]]; then
  printf 'Startup fixture did not execute the dedicated JUnit group.\n' >&2
  exit 1
fi

# 移除一个 Tag 后，枚举检查必须在 Maven 前失败并指出缺失映射。
sed -i.bak '/startup-smoke/d' \
  "$FIXTURE_ROOT/module-b/src/test/java/example/BetaApplicationTest.java"
rm "$FIXTURE_ROOT/module-b/src/test/java/example/BetaApplicationTest.java.bak"
if missing_output="$(HARNESS_ROOT_DIR="$FIXTURE_ROOT" \
    STARTUP_FIXTURE_ROOT="$FIXTURE_ROOT" STARTUP_FIXTURE_TRACE="$trace" \
    PATH="$FIXTURE_ROOT/bin:$PATH" "$FIXTURE_ROOT/scripts/harness-startup.sh" 2>&1)"; then
  printf 'Application without startup-smoke mapping should fail.\n' >&2
  exit 1
fi
for expected in 'WHAT:' 'WHY:' 'FIX:' 'BetaApplication'; do
  if ! rg -Fq "$expected" <<<"$missing_output"; then
    printf 'Missing startup mapping diagnostic lacks %s.\n%s\n' "$expected" "$missing_output" >&2
    exit 1
  fi
done
ruby -e '
  path = ARGV.fetch(0)
  source = File.read(path)
  File.write(path, source.sub("@org.springframework.boot.test.context.SpringBootTest",
    "@org.junit.jupiter.api.Tag(\"startup-smoke\")\n@org.springframework.boot.test.context.SpringBootTest"))
' "$FIXTURE_ROOT/module-b/src/test/java/example/BetaApplicationTest.java"

# Maven 返回成功但没有生成本次报告时必须失败，防止错误的 Tag 配置假通过。
find "$FIXTURE_ROOT/module-a/target" "$FIXTURE_ROOT/module-b/target" \
  -type f -name 'TEST-*.xml' -delete
if zero_output="$(HARNESS_ROOT_DIR="$FIXTURE_ROOT" \
    STARTUP_FIXTURE_ZERO_TESTS=1 STARTUP_FIXTURE_ROOT="$FIXTURE_ROOT" \
    STARTUP_FIXTURE_TRACE="$trace" PATH="$FIXTURE_ROOT/bin:$PATH" \
    "$FIXTURE_ROOT/scripts/harness-startup.sh" 2>&1)"; then
  printf 'Startup smoke with zero Surefire reports should fail.\n' >&2
  exit 1
fi
if ! rg -Fq '没有产生测试报告' <<<"$zero_output"; then
  printf 'Zero-test startup diagnostic is not actionable.\n%s\n' "$zero_output" >&2
  exit 1
fi

printf 'Startup Harness tests passed.\n'
