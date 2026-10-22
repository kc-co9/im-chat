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

mkdir -p "$FIXTURE_ROOT/scripts/lib" "$FIXTURE_ROOT/bin"
cp "$ROOT_DIR/scripts/lib/harness-reactor-evidence.rb" "$FIXTURE_ROOT/scripts/lib/"
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
<testsuite name="example.${application}Test" tests="1" failures="0" errors="0" skipped="0"><testcase classname="example.${application}Test" name="contextLoads" time="0.01"/></testsuite>
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

# 同轮只读入口复用刚产生的报告，Maven 调用计数不变；非法 epoch 必须失败。
trace_before="$(wc -l < "$trace")"
epoch="$(ruby -e 'puts Time.now.to_f - 60')"
HARNESS_ROOT_DIR="$FIXTURE_ROOT" STARTUP_FIXTURE_TRACE="$trace" PATH="$FIXTURE_ROOT/bin:$PATH" \
  "$FIXTURE_ROOT/scripts/harness-startup.sh" --reports-since "$epoch"
[[ "$(wc -l < "$trace")" == "$trace_before" ]]
for epoch in 0 -1 NaN 999999999999; do
  if HARNESS_ROOT_DIR="$FIXTURE_ROOT" "$FIXTURE_ROOT/scripts/harness-startup.sh" --reports-since "$epoch" >/dev/null 2>&1; then
    printf 'Invalid epoch accepted: %s\n' "$epoch" >&2
    exit 1
  fi
done

# target 符号链接必须在删除报告或调用 Maven 前拒绝，外部文件保持原样。
mkdir -p "$FIXTURE_ROOT/external/surefire-reports"
external_report="$FIXTURE_ROOT/external/surefire-reports/TEST-example.AlphaApplicationTest.xml"
printf 'external sentinel\n' > "$external_report"
mv "$FIXTURE_ROOT/module-a/target" "$FIXTURE_ROOT/module-a/target.saved"
ln -s "$FIXTURE_ROOT/external" "$FIXTURE_ROOT/module-a/target"
trace_before="$(wc -l < "$trace")"
if HARNESS_ROOT_DIR="$FIXTURE_ROOT" STARTUP_FIXTURE_ROOT="$FIXTURE_ROOT" \
    STARTUP_FIXTURE_TRACE="$trace" PATH="$FIXTURE_ROOT/bin:$PATH" \
    "$FIXTURE_ROOT/scripts/harness-startup.sh" >/dev/null 2>&1; then
  printf 'Symlink target must fail before Maven.\n' >&2
  exit 1
fi
if [[ "$(cat "$external_report")" != 'external sentinel' || "$(wc -l < "$trace")" != "$trace_before" ]]; then
  printf 'Unsafe startup cleanup changed external report or invoked Maven.\n' >&2
  exit 1
fi
rm "$FIXTURE_ROOT/module-a/target"
mv "$FIXTURE_ROOT/module-a/target.saved" "$FIXTURE_ROOT/module-a/target"

# IAM 使用同模块唯一 protocol test 回退，不依赖 ApplicationTest 命名。
beta_test="$FIXTURE_ROOT/module-b/src/test/java/example/BetaApplicationTest.java"
cp "$beta_test" "$FIXTURE_ROOT/beta.saved"
sed 's/classes = BetaApplication.class, //; s/BetaApplicationTest/IamAuthorizationServerProtocolTest/g' \
  "$beta_test" > "$FIXTURE_ROOT/module-b/src/test/java/example/IamAuthorizationServerProtocolTest.java"
rm "$beta_test"
ruby "$FIXTURE_ROOT/scripts/lib/harness-reactor-evidence.rb" startup-manifest "$FIXTURE_ROOT" "$FIXTURE_ROOT/fallback.json"
ruby -rjson -e 'abort "IAM fallback missing" unless JSON.parse(File.read(ARGV[0]))["applications"].any? { |e| e["testClass"] == "example.IamAuthorizationServerProtocolTest" }' "$FIXTURE_ROOT/fallback.json"
sed 's/IamAuthorizationServerProtocolTest/OtherProtocolTest/g' \
  "$FIXTURE_ROOT/module-b/src/test/java/example/IamAuthorizationServerProtocolTest.java" > "$FIXTURE_ROOT/module-b/src/test/java/example/OtherProtocolTest.java"
if ruby "$FIXTURE_ROOT/scripts/lib/harness-reactor-evidence.rb" startup-manifest "$FIXTURE_ROOT" "$FIXTURE_ROOT/fallback.json" >/dev/null 2>&1; then
  printf 'Ambiguous same-module fallback must fail.\n' >&2
  exit 1
fi
rm "$FIXTURE_ROOT/module-b/src/test/java/example/"*ProtocolTest.java
mv "$FIXTURE_ROOT/beta.saved" "$beta_test"

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
