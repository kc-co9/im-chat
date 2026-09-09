#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHECKER="$ROOT_DIR/scripts/check-sql.sh"
fixture_root="$(mktemp -d)"
trap 'rm -rf "$fixture_root"' EXIT

reset_fixture() {
  rm -rf "$fixture_root"
  mkdir -p "$fixture_root/module/sql" \
    "$fixture_root/module/src/main/resources/mapper" \
    "$fixture_root/module/src/main/java/example/infrastructure/mapper"
  cat > "$fixture_root/module/sql/schema.sql" <<'EOF'
-- Documentation example only: DROP DATABASE ignored_db;
/* Documentation example only: TRUNCATE TABLE ignored_table; */
CREATE TABLE `sample_table` (
  `id` BIGINT NOT NULL,
  `display_name` VARCHAR(32),
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_display_name` (`display_name`),
  UNIQUE KEY `uk_display_name` (`display_name`)
) ENGINE = InnoDB COMMENT = 'sample';
EOF
  cat > "$fixture_root/module/src/main/resources/mapper/SampleMapper.xml" <<'EOF'
<mapper namespace="example.SampleMapper">
  <select id="all">SELECT id, display_name FROM sample_table</select>
  <select id="count">SELECT COUNT(*) FROM sample_table</select>
  <update id="rename"><![CDATA[UPDATE sample_table SET display_name = #{name} WHERE id = #{id}]]></update>
  <delete id="remove">DELETE FROM sample_table WHERE id = #{id}</delete>
  <!-- Dynamic and referenced fragments are review-only for WHERE completeness. -->
  <update id="dynamic">UPDATE sample_table SET display_name = #{name}<where><if test="id != null">id = #{id}</if></where></update>
  <update id="included">UPDATE sample_table SET display_name = #{name}<include refid="byId"/></update>
</mapper>
EOF
  cat > "$fixture_root/module/src/main/java/example/infrastructure/mapper/SampleMapper.java" <<'EOF'
package example.infrastructure.mapper;
interface SampleMapper {
  // ${comment_is_not_sql}
  // @Update("UPDATE ignored SET value = ${comment_value}")
  String NOTE = "${ordinary_string}";
  String EXAMPLE = "@Delete(\"DELETE FROM ignored\")";
  @Select("SELECT id, display_name FROM sample_table WHERE id = #{id}") Object find();
  @Select(value = "SELECT id, display_name FROM sample_table WHERE id = #{id} AND explicit = 1") Object findExplicit();
  @Update("UPDATE sample_table SET display_name = #{name} WHERE id = #{id}") void update();
  @Update(value = "UPDATE sample_table SET display_name = #{name} WHERE id = #{id} AND explicit = 1") void updateExplicit();
  @Update(/* ${annotation_comment} */ "UPDATE sample_table SET display_name = #{name} WHERE id = #{id}") void updateCommented();
  @Delete("""
      DELETE FROM sample_table
      WHERE id = #{id}
      """) void delete();
}
EOF
  cat > "$fixture_root/module/sql/ddl.sql" <<'EOF'
DROP TABLE IF EXISTS `old_table`;
EOF
  cat > "$fixture_root/module/sql/unquoted.sql" <<'EOF'
CREATE TABLE unquoted_table (
  id BIGINT NOT NULL PRIMARY KEY,
  display_name VARCHAR(32),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted BIGINT NOT NULL DEFAULT 0,
  KEY idx_display_name (display_name),
  UNIQUE KEY uk_display_name (display_name)
) ENGINE = InnoDB COMMENT = 'unquoted identifiers and inline primary key are valid';
EOF
}

expect_pass() {
  local name="$1"
  local output
  if ! output="$(SQL_HARNESS_ROOT="$fixture_root" "$CHECKER" 2>&1)"; then
    printf 'Expected PASS for %s, got:\n%s\n' "$name" "$output" >&2
    exit 1
  fi
}

expect_fail() {
  local name="$1"
  local expected="$2"
  local output
  if output="$(SQL_HARNESS_ROOT="$fixture_root" "$CHECKER" 2>&1)"; then
    printf 'Expected FAIL for %s.\n' "$name" >&2
    exit 1
  fi
  if ! grep -Fq "$expected" <<<"$output"; then
    printf 'Expected diagnostic "%s" for %s, got:\n%s\n' "$expected" "$name" "$output" >&2
    exit 1
  fi
}

reset_fixture
cat >> "$fixture_root/module/src/main/java/example/infrastructure/mapper/SampleMapper.java" <<'EOF'
interface UnsupportedForms {
  @Update({"UPDATE sample_table", "SET display_name = #{name}"}) void arrayForm();
  @Delete(SQL_CONSTANT) void constantForm();
  @Update("UPDATE sample_table " + "SET display_name = #{name}") void concatForm();
}
EOF
expect_pass "valid DDL, XML, annotations, COUNT star, allowed drop, and review-only forms"

for case_name in resource_sql root_sql module_bad_table missing_template_fields missing_primary missing_engine missing_comment bad_table bad_column bad_index bad_unique if_not_exists_bypass truncate drop_database bare_ddl_drop other_root_drop xml_select_star xml_qualified_select_star annotation_select_star xml_drop xml_cdata_drop long_header_xml_substitution annotation_drop annotation_value_drop annotation_text_drop qualified_annotation invalid_location xml_substitution xml_fragment_substitution annotation_substitution xml_update xml_update_where_literal xml_update_where_comment xml_delete annotation_update annotation_update_where_literal annotation_update_where_comment annotation_value_update annotation_delete unsupported_substitution; do
  reset_fixture
  expected=""
  case "$case_name" in
    resource_sql) mkdir -p "$fixture_root/module/src/main/resources/db"; echo 'SELECT 1;' > "$fixture_root/module/src/main/resources/db/schema.sql"; expected="Production resource SQL" ;;
    root_sql) mkdir -p "$fixture_root/sql"; cp "$fixture_root/module/sql/schema.sql" "$fixture_root/sql/schema.sql"; expected="owning Server module" ;;
    module_bad_table) cat > "$fixture_root/module/sql/schema.sql" <<'EOF'
CREATE TABLE `BadModuleTable` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` BIGINT NOT NULL DEFAULT 0
) ENGINE = InnoDB COMMENT = 'bad';
EOF
      expected="lower snake_case table" ;;
    missing_template_fields) sed -i.bak '/`update_time`/d' "$fixture_root/module/sql/schema.sql"; expected="standard template columns" ;;
    missing_primary) sed -i.bak '/PRIMARY KEY/d' "$fixture_root/module/sql/schema.sql"; expected="explicit PRIMARY KEY" ;;
    missing_engine) sed -i.bak 's/ENGINE = InnoDB/ENGINE = Other/' "$fixture_root/module/sql/schema.sql"; expected="ENGINE=InnoDB" ;;
    missing_comment) sed -i.bak "s/ COMMENT = 'sample'//" "$fixture_root/module/sql/schema.sql"; expected="table COMMENT" ;;
    bad_table) sed -i.bak 's/`sample_table`/`SampleTable`/' "$fixture_root/module/sql/schema.sql"; expected="lower snake_case table" ;;
    bad_column) sed -i.bak 's/`display_name` VARCHAR/`displayName` VARCHAR/' "$fixture_root/module/sql/schema.sql"; expected="lower snake_case column" ;;
    bad_index) sed -i.bak 's/`idx_display_name`/`display_name`/' "$fixture_root/module/sql/schema.sql"; expected="idx_" ;;
    bad_unique) sed -i.bak 's/`uk_display_name`/`unique_display_name`/' "$fixture_root/module/sql/schema.sql"; expected="uk_" ;;
    if_not_exists_bypass) cat > "$fixture_root/module/sql/schema.sql" <<'EOF'
CREATE TABLE IF NOT EXISTS `BadTable` (`BadColumn` BIGINT) ENGINE = Other;
EOF
      expected="lower snake_case table" ;;
    truncate) echo 'TrUnCaTe TABLE sample_table;' >> "$fixture_root/module/sql/schema.sql"; expected="TRUNCATE" ;;
    drop_database) printf 'DROP\n DATABASE sample;\n' >> "$fixture_root/module/sql/schema.sql"; expected="DROP DATABASE" ;;
    bare_ddl_drop) printf 'DrOp\n TABLE `old_table`;\n' > "$fixture_root/module/sql/ddl.sql"; expected="DROP TABLE IF EXISTS" ;;
    other_root_drop) echo 'DROP TABLE IF EXISTS `old_table`;' >> "$fixture_root/module/sql/schema.sql"; expected="DROP TABLE is only allowed" ;;
    xml_select_star) sed -i.bak 's/SELECT id, display_name FROM sample_table/SELECT * FROM sample_table/' "$fixture_root/module/src/main/resources/mapper/SampleMapper.xml"; expected="SELECT wildcard" ;;
    xml_qualified_select_star) sed -i.bak 's/SELECT id, display_name FROM sample_table/SELECT sample_table.* FROM sample_table/' "$fixture_root/module/src/main/resources/mapper/SampleMapper.xml"; expected="SELECT wildcard" ;;
    annotation_select_star) sed -i.bak 's/SELECT id, display_name FROM sample_table WHERE id = #{id}/SELECT * FROM sample_table WHERE id = #{id}/' "$fixture_root/module/src/main/java/example/infrastructure/mapper/SampleMapper.java"; expected="SELECT wildcard" ;;
    xml_drop) sed -i.bak 's/DELETE FROM sample_table WHERE id = #{id}/DROP TABLE sample_table/' "$fixture_root/module/src/main/resources/mapper/SampleMapper.xml"; expected="DROP TABLE" ;;
    xml_cdata_drop) sed -i.bak 's/UPDATE sample_table SET display_name = #{name} WHERE id = #{id}/DROP TABLE sample_table/' "$fixture_root/module/src/main/resources/mapper/SampleMapper.xml"; expected="DROP TABLE" ;;
    long_header_xml_substitution) sed -i.bak 's/#{name}/${name}/' "$fixture_root/module/src/main/resources/mapper/SampleMapper.xml"; ruby -e 'path = ARGV.fetch(0); File.write(path, "<!--" + ("x" * 5000) + "-->\n" + File.read(path))' "$fixture_root/module/src/main/resources/mapper/SampleMapper.xml"; expected='${...}' ;;
    annotation_drop) sed -i.bak 's/SELECT id, display_name FROM sample_table WHERE id = #{id}/DROP TABLE sample_table/' "$fixture_root/module/src/main/java/example/infrastructure/mapper/SampleMapper.java"; expected="DROP TABLE" ;;
    annotation_value_drop) sed -i.bak 's/SELECT id, display_name FROM sample_table WHERE id = #{id} AND explicit = 1/DROP TABLE sample_table/' "$fixture_root/module/src/main/java/example/infrastructure/mapper/SampleMapper.java"; expected="DROP TABLE" ;;
    annotation_text_drop) sed -i.bak 's/DELETE FROM sample_table/DROP TABLE sample_table/' "$fixture_root/module/src/main/java/example/infrastructure/mapper/SampleMapper.java"; expected="DROP TABLE" ;;
    qualified_annotation) cat >> "$fixture_root/module/src/main/java/example/infrastructure/mapper/SampleMapper.java" <<'EOF'
interface QualifiedUnsafe { @org.apache.ibatis.annotations.Update("UPDATE sample_table SET name = ${name}") void run(); }
EOF
      expected='${...}' ;;
    invalid_location) mkdir -p "$fixture_root/module/src/main/java/example/domain"; cp "$fixture_root/module/src/main/java/example/infrastructure/mapper/SampleMapper.java" "$fixture_root/module/src/main/java/example/domain/Bad.java"; sed -i.bak 's/package example.infrastructure.mapper/package example.domain/' "$fixture_root/module/src/main/java/example/domain/Bad.java"; expected="infrastructure mapper" ;;
    xml_substitution) sed -i.bak 's/#{name}/${name}/' "$fixture_root/module/src/main/resources/mapper/SampleMapper.xml"; expected='${...}' ;;
    xml_fragment_substitution) sed -i.bak 's@</mapper>@  <sql id="unsafeFragment">${column}</sql></mapper>@' "$fixture_root/module/src/main/resources/mapper/SampleMapper.xml"; expected='${...}' ;;
    annotation_substitution) sed -i.bak 's/#{id}/${id}/' "$fixture_root/module/src/main/java/example/infrastructure/mapper/SampleMapper.java"; expected='${...}' ;;
    xml_update) sed -i.bak 's/ WHERE id = #{id}//' "$fixture_root/module/src/main/resources/mapper/SampleMapper.xml"; expected="UPDATE without WHERE" ;;
    xml_update_where_literal) sed -i.bak 's/ WHERE id = #{id}/, note = '\''WHERE'\''/' "$fixture_root/module/src/main/resources/mapper/SampleMapper.xml"; expected="UPDATE without WHERE" ;;
    xml_update_where_comment) sed -i.bak 's/ WHERE id = #{id}/ \/\* WHERE id = #{id} \*\//' "$fixture_root/module/src/main/resources/mapper/SampleMapper.xml"; expected="UPDATE without WHERE" ;;
    xml_delete) sed -i.bak 's/DELETE FROM sample_table WHERE id = #{id}/DELETE FROM sample_table/' "$fixture_root/module/src/main/resources/mapper/SampleMapper.xml"; expected="DELETE without WHERE" ;;
    annotation_update) sed -i.bak 's/ WHERE id = #{id}//' "$fixture_root/module/src/main/java/example/infrastructure/mapper/SampleMapper.java"; expected="UPDATE without WHERE" ;;
    annotation_update_where_literal) sed -i.bak 's/ WHERE id = #{id}/, note = '\''WHERE'\''/' "$fixture_root/module/src/main/java/example/infrastructure/mapper/SampleMapper.java"; expected="UPDATE without WHERE" ;;
    annotation_update_where_comment) sed -i.bak 's/ WHERE id = #{id}/ \/\* WHERE id = #{id} \*\//' "$fixture_root/module/src/main/java/example/infrastructure/mapper/SampleMapper.java"; expected="UPDATE without WHERE" ;;
    annotation_value_update) sed -i.bak 's/ WHERE id = #{id} AND explicit = 1//' "$fixture_root/module/src/main/java/example/infrastructure/mapper/SampleMapper.java"; expected="UPDATE without WHERE" ;;
    annotation_delete) sed -i.bak '/WHERE id = #{id}/d' "$fixture_root/module/src/main/java/example/infrastructure/mapper/SampleMapper.java"; expected="DELETE without WHERE" ;;
    unsupported_substitution) cat >> "$fixture_root/module/src/main/java/example/infrastructure/mapper/SampleMapper.java" <<'EOF'
interface UnsafeUnsupported { @Update({"UPDATE sample_table", "SET name = ${name}"}) void run(); }
EOF
      expected='${...}' ;;
  esac
  expect_fail "$case_name" "$expected"
done

printf 'SQL Harness tests passed.\n'
