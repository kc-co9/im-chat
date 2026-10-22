"""Recipe 契约测试：仅使用临时 fixture，不执行真实 Maven 或网络操作。"""
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import patch
import zipfile

HERE = Path(__file__).parent


def recipe_module():
    spec = importlib.util.spec_from_file_location("jraft_build", HERE / "build.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class BuildAdmissionTest(unittest.TestCase):
    def test_unverified_source_pin_rejects_before_maven(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in ("build.sh", "build.py"):
                if (HERE / name).exists():
                    (root / name).write_bytes((HERE / name).read_bytes())
            (root / "provenance.yml").write_text('{"sourceCommit": null}')
            sentinel = root / "called"
            (root / "mvn").write_text('#!/bin/sh\ntouch "' + str(sentinel) + '"\nexit 99\n')
            (root / "mvn").chmod(0o755)
            environment = dict(os.environ, PATH=str(root) + os.pathsep + os.environ["PATH"])
            environment.pop("JRAFT_SOURCE_ARCHIVE", None)
            result = subprocess.run(["bash", str(root / "build.sh")], env=environment,
                                    capture_output=True, text=True, timeout=10)
            self.assertEqual(65, result.returncode, result.stderr)
            self.assertIn("SOURCE_PROVENANCE_MISSING", result.stderr)
            self.assertFalse(sentinel.exists())
            self.assertNotIn("BUILD SUCCESS", result.stdout)

    def test_tar_rejects_unsafe_entries(self):
        build = recipe_module()
        for name, kind in (("../escape", tarfile.REGTYPE), ("/absolute", tarfile.REGTYPE),
                           ("link", tarfile.SYMTYPE), ("device", tarfile.CHRTYPE)):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                archive = Path(directory) / "input.tar"
                with tarfile.open(archive, "w") as output:
                    entry = tarfile.TarInfo(name)
                    entry.type = kind
                    output.addfile(entry, io.BytesIO())
                with self.assertRaises(build.Rejected):
                    build.extract_source(archive, Path(directory) / "source")

    def test_source_inventory_includes_storage_log_package(self):
        build = recipe_module()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / "jraft-core/src/main/java/com/alipay/sofa/jraft/storage/log/SegmentFile.java"
            path.parent.mkdir(parents=True)
            path.write_bytes(b"upstream source")
            self.assertEqual({path.relative_to(root).as_posix(): build.sha(b"upstream source")},
                             build.source_inventory(root))

    def test_tar_rejects_duplicates(self):
        build = recipe_module()
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "input.tar"
            with tarfile.open(archive, "w") as output:
                for _ in range(2):
                    output.addfile(tarfile.TarInfo("same"), io.BytesIO())
            with self.assertRaises(build.Rejected):
                build.extract_source(archive, Path(directory) / "source")

    def test_preflight_rejects_hash_scope_environment_and_nonempty_output_before_maven(self):
        build = recipe_module()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = {"sourceCommit": "a" * 40, "sourceArchiveSha256": "b" * 64}
            with self.assertRaises(build.Rejected):
                build.preflight(root, manifest, {"MAVEN_OPTS": "-DskipTests"})
            with self.assertRaises(build.Rejected):
                build.preflight(root, manifest, {"JRAFT_SOURCE_ARCHIVE": str(root / "missing")})
            archive = root / "source.tar"
            archive.write_bytes(b"wrong")
            output = root / "out"
            output.mkdir()
            (output / "existing").write_text("preserve")
            env = {"JRAFT_SOURCE_ARCHIVE": str(archive), "JRAFT_BUILD_OUTPUT": str(output)}
            with self.assertRaises(build.Rejected):
                build.preflight(root, manifest, env)
            self.assertEqual("preserve", (output / "existing").read_text())
            (output / "existing").unlink()
            with self.assertRaises(build.Rejected):
                build.preflight(root, manifest, env)
            with self.assertRaises(build.Rejected):
                build.patch_paths(b"diff --git a/outside b/outside\n", ["pom.xml"])


class InputVersionTest(unittest.TestCase):
    def test_fixed_versions_use_maven_snapshot_semantics(self):
        build = recipe_module()
        for version in ("snapshot-20080530", "1.0", "1.0-snapshot-extra"):
            with self.subTest(version=version):
                self.assertEqual(version, build.fixed_version(version))
        for version in ("1.0-SNAPSHOT", "1.0snapshot", "1.0-SnApShOt", "1.0-20261016.123456-1",
                        "", "LATEST", "RELEASE", "${version}", "[1,2)", "(1,2]", "1,+"):
            with self.subTest(version=version), self.assertRaises(build.Rejected):
                build.fixed_version(version)

    def test_fixed_versions_reject_only_exact_dynamic_tokens(self):
        build = recipe_module()
        for version in ("1.5.16.RELEASE", "1.0-release", "1.0-LATEST", "latest-1.0"):
            with self.subTest(version=version):
                self.assertEqual(version, build.fixed_version(version))
        for version in ("LATEST", "latest", "LaTeSt", "RELEASE", "release", "ReLeAsE",
                        "${version}", "1.0-${qualifier}", "[1,2)", "(1,2]", "1.0-SNAPSHOT"):
            with self.subTest(version=version), self.assertRaises(build.Rejected):
                build.fixed_version(version)

    def test_inventory_accepts_release_suffix_and_dynamic_words_in_paths(self):
        build = recipe_module()
        for group, artifact, version in (("org/springframework/boot", "spring-boot-dependencies", "1.5.16.RELEASE"),
                                         ("com/LATEST/example", "latest-library", "1.0"),
                                         ("com/release/example", "LATEST", "1.0")):
            with self.subTest(group=group, artifact=artifact, version=version), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                expected = self.inventory(root, build, group, artifact, version)
                self.assertEqual(expected, build.input_inventory(root))

    def test_inventory_rejects_dynamic_unresolved_and_range_versions(self):
        build = recipe_module()
        for version in ("LATEST", "latest", "LaTeSt", "RELEASE", "release", "ReLeAsE",
                        "${version}", "[1,2)", "(1,2]"):
            with self.subTest(version=version), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self.inventory(root, build, version=version)
                with self.assertRaisesRegex(build.Rejected, "DYNAMIC_OR_UNRESOLVED_VERSION"):
                    build.input_inventory(root)

    def test_inventory_ignores_configuration_properties_and_foreign_versions(self):
        build = recipe_module()
        body = """<version>1.0</version>
          <build><pluginManagement><plugins><plugin><version>1.4.1</version>
            <executions><execution><configuration><rules><requireJavaVersion>
              <version>[1.7,)</version></requireJavaVersion></rules>
              <dependencies><dependency><version>LATEST</version></dependency></dependencies>
            </configuration></execution></executions>
          </plugin></plugins></pluginManagement></build>
          <properties><version>[1,2)</version></properties>
          <foreign:dependencies xmlns:foreign="urn:not-maven"><foreign:dependency>
            <foreign:version>RELEASE</foreign:version></foreign:dependency></foreign:dependencies>
          <dependencies><dependency xmlns="urn:not-maven"><version>1.0-SNAPSHOT</version>
          </dependency></dependencies>"""
        for namespace in ("", ' xmlns="http://maven.apache.org/POM/4.0.0"'):
            with self.subTest(namespace=namespace), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                expected = self.inventory(root, build)
                pom = next(root.rglob("*.pom"))
                pom.write_text("<project" + namespace + ">" + body + "</project>")
                expected[pom.relative_to(root).as_posix()] = build.file_sha(pom)
                self.assertEqual(expected, build.input_inventory(root))

    def test_inventory_audits_raw_versions_at_maven_coordinate_paths(self):
        build = recipe_module()
        paths = ["version", "parent/version"]
        for context in ("", "profiles/profile/"):
            paths.extend(context + path for path in (
                "dependencies/dependency/version", "dependencyManagement/dependencies/dependency/version",
                "build/plugins/plugin/version", "build/pluginManagement/plugins/plugin/version",
                "build/plugins/plugin/dependencies/dependency/version",
                "build/pluginManagement/plugins/plugin/dependencies/dependency/version",
                "reporting/plugins/plugin/version", "build/extensions/extension/version"))
        for namespace in ("", ' xmlns="http://maven.apache.org/POM/4.0.0"'):
            for path in paths:
                for version in ("[1.7,)", "1.0-SNAPSHOT", "1.0snapshot", "1.0-20261016.123456-1", "LATEST", "RELEASE"):
                    with self.subTest(namespace=namespace, path=path, version=version), tempfile.TemporaryDirectory() as directory:
                        root = Path(directory)
                        self.inventory(root, build)
                        elements = path.split("/")
                        body = version
                        for element in reversed(elements):
                            body = "<" + element + ">" + body + "</" + element + ">"
                        next(root.rglob("*.pom")).write_text("<project" + namespace + ">" + body + "</project>")
                        audit = []
                        inputs = build.input_inventory(root, audit)
                        self.assertEqual(build.file_sha(next(root.rglob("*.pom"))),
                                         inputs[next(root.rglob("*.pom")).relative_to(root).as_posix()])
                        self.assertTrue(any(item["version"] == version and item["classification"] == "dynamic-or-snapshot-declaration"
                                            for item in audit))

    def test_inventory_rejects_invalid_pom_root_and_malformed_xml(self):
        build = recipe_module()
        for payload in ('<other><version>1</version></other>', '<project xmlns="urn:not-maven"/>', '<project>'):
            with self.subTest(payload=payload), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self.inventory(root, build)
                next(root.rglob("*.pom")).write_text(payload)
                with self.assertRaises((build.Rejected, build.ET.ParseError)):
                    build.input_inventory(root)

    def test_inventory_maven_entities_preserve_raw_bytes_and_sha_offline(self):
        build = recipe_module()
        payload = b"""<project><version>1.0</version><contributors><contributor>
          <name>Trygve Laugst&oslash;l &amp; &#248; &#xF8; &lt; &gt; &quot; &apos;</name>
          </contributor></contributors></project>"""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            expected = self.inventory(root, build)
            pom = next(root.rglob("*.pom"))
            pom.write_bytes(payload)
            expected[pom.relative_to(root).as_posix()] = build.sha(payload)
            with patch("socket.socket", side_effect=AssertionError("network forbidden")), \
                    patch("urllib.request.urlopen", side_effect=AssertionError("network forbidden")), \
                    patch.object(build, "command", side_effect=AssertionError("subprocess forbidden")):
                self.assertEqual(expected, build.input_inventory(root))
            self.assertEqual(payload, pom.read_bytes())
            self.assertEqual('Trygve Laugstøl & ø ø < > " \'',
                             build.input_pom_root(payload).findtext("contributors/contributor/name"))

    def test_input_pom_default_entity_map_matches_maven_extraction(self):
        build = recipe_module()
        extracted = "".join(name + "=" + str(value) + ",\n"
                            for name, value in build.MAVEN_DEFAULT_ENTITIES.items())
        self.assertEqual(248, len(build.MAVEN_DEFAULT_ENTITIES))
        self.assertEqual("dd029f4f725dca0574fb24a44e13b28206cc101ac255d1ca054c4e640ed5b7a9",
                         build.sha(extracted.encode("ascii")))
        for name, value in build.MAVEN_DEFAULT_ENTITIES.items():
            with self.subTest(name=name):
                self.assertEqual(chr(value), build.input_pom_root(
                    ("<project><name>&" + name + ";</name></project>").encode()).findtext("name"))

    def test_input_pom_entities_preserve_comments_cdata_and_xml_escaping(self):
        build = recipe_module()
        root = build.input_pom_root(b"""<project note="&oslash; &amp;oslash;">
          <!-- &oslash; &unknown; --><?ignored &oslash;?>
          <description><![CDATA[&oslash; &unknown; <!DOCTYPE literal>]]></description>
          <name>&amp;oslash;</name></project>""")
        self.assertEqual("ø &oslash;", root.attrib["note"])
        self.assertEqual("&oslash; &unknown; <!DOCTYPE literal>", root.findtext("description"))
        self.assertEqual("&oslash;", root.findtext("name"))
        for encoding in ("utf-8", "utf-16", "iso-8859-1"):
            payload = ('<?xml version="1.0" encoding="' + encoding + '"?>'
                       '<project><name>é &oslash;</name></project>').encode(encoding)
            self.assertEqual("é ø", build.input_pom_root(payload).findtext("name"))

    def test_input_pom_rejects_unknown_entities_dtd_and_oversized_input(self):
        build = recipe_module()
        for payload in (b"<project>&unknown;</project>",
                        b'<!DOCTYPE project SYSTEM "http://127.0.0.1:9/evil"><project/>',
                        b'<!DOCTYPE project [<!ENTITY x SYSTEM "file:///etc/passwd">]><project>&x;</project>',
                        b'<!DOCTYPE project [<!ENTITY x "&oslash;">]><project>&x;</project>',
                        b'<!ENTITY x "test"><project/>',
                        b"<project>&#0;</project>", b"<project>&oslash;</wrong>"):
            with self.subTest(payload=payload), patch("socket.socket", side_effect=AssertionError("network forbidden")):
                with self.assertRaises((build.Rejected, build.ET.ParseError)):
                    build.input_pom_root(payload)
        with self.assertRaisesRegex(build.Rejected, "INPUT_POM_TOO_LARGE"):
            build.input_pom_root(b" " * (build.MAX_INPUT_POM_BYTES + 1))

    def test_input_pom_repeated_unterminated_sections_reject_promptly(self):
        script = """import importlib.util, sys
spec = importlib.util.spec_from_file_location("recipe", sys.argv[1])
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)
try:
    build.input_pom_root(b"<project>" + sys.argv[2].encode() * 100000)
except (build.Rejected, build.ET.ParseError):
    pass
else:
    raise AssertionError("unterminated section accepted")
"""
        for opener in ("<!--", "<![CDATA[", "<?"):
            with self.subTest(opener=opener):
                try:
                    result = subprocess.run([sys.executable, "-c", script, str(HERE / "build.py"), opener],
                                            capture_output=True, timeout=3)
                except subprocess.TimeoutExpired:
                    self.fail("unterminated section did not reject within 3 seconds: " + opener)
                self.assertEqual(0, result.returncode, result.stderr.decode())

    def test_input_pom_utf16_dtd_rejects_with_and_without_bom(self):
        build = recipe_module()
        for encoding, bom in (("utf-16-le", b"\xff\xfe"), ("utf-16-be", b"\xfe\xff")):
            for prefix in (b"", bom):
                for declaration in ('<!DOCTYPE project SYSTEM "http://127.0.0.1:9/evil">',
                                    '<!DOCTYPE project [<!ENTITY x SYSTEM "file:///etc/passwd">]>',
                                    '<!ENTITY x "value">'):
                    with self.subTest(encoding=encoding, bom=bool(prefix), declaration=declaration):
                        payload = prefix + ('<?xml version="1.0" encoding="UTF-16"?>'
                                            + declaration + '<project/>').encode(encoding)
                        with self.assertRaisesRegex(build.Rejected, "INPUT_POM_DTD_FORBIDDEN"):
                            build.input_pom_root(payload)

    def test_inventory_decoded_coordinates_are_audited_unknown_entities_still_reject(self):
        build = recipe_module()
        for value in ("L&#65;TEST", "RELEA&#x53;E", "1.0&lbrack;", "&#91;1,2)",
                      "1.0-SNAPSH&#79;T", "1.0&oslash;SNAPSHOT"):
            with self.subTest(value=value), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self.inventory(root, build)
                next(root.rglob("*.pom")).write_text("<project><profiles><profile><dependencies>"
                    "<dependency><version>" + value + "</version></dependency>"
                    "</dependencies></profile></profiles></project>")
                if "&lbrack;" in value:
                    with self.assertRaises(build.ET.ParseError):
                        build.input_inventory(root)
                else:
                    audit = []
                    build.input_inventory(root, audit)
                    self.assertEqual("dynamic-or-snapshot-declaration", audit[0]["classification"])

    def test_dormant_m2e_snapshot_is_audited_without_changing_raw_sha(self):
        build = recipe_module()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            expected = self.inventory(root, build)
            pom = next(root.rglob("*.pom"))
            payload = b'<project><profiles><profile><id>m2e</id><activation><property><name>m2e.version</name></property></activation><build><plugins><plugin><groupId>org.maven.ide.eclipse</groupId><artifactId>lifecycle-mapping</artifactId><version>0.9.9-SNAPSHOT</version></plugin></plugins></build></profile></profiles></project>'
            pom.write_bytes(payload)
            expected[pom.relative_to(root).as_posix()] = build.sha(payload)
            audit = []
            self.assertEqual(expected, build.input_inventory(root, audit))
            self.assertEqual([{"path": pom.relative_to(root).as_posix(),
                               "modelPath": "project/profiles/profile/build/plugins/plugin/version",
                               "profileId": "m2e", "version": "0.9.9-SNAPSHOT",
                               "classification": "dynamic-or-snapshot-declaration"}], audit)
            self.assertEqual(payload, pom.read_bytes())

    def inventory(self, root, build, group="com/example", artifact="library", version="1.0"):
        files = {}
        for extension, content in (("jar", b"fixed input"),
                                   ("pom", ("<project><version>" + version + "</version></project>").encode())):
            relative = group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + "." + extension
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
            files[relative] = build.sha(content)
        return files

    def test_inventory_classifies_version_not_group_artifact_or_repository(self):
        build = recipe_module()
        for group, artifact, version in (("com/google/code/google-collections", "google-collect", "snapshot-20080530"),
                                         ("com/SNAPSHOT/example", "snapshot-library", "1.0")):
            with self.subTest(version=version), tempfile.TemporaryDirectory(prefix="snapshot-repository-") as directory:
                root = Path(directory)
                expected = self.inventory(root, build, group, artifact, version)
                self.assertEqual(expected, build.input_inventory(root))

    def test_inventory_rejects_snapshot_version_even_without_pom_version(self):
        build = recipe_module()
        for version in ("1.0-SNAPSHOT", "1.0snapshot", "1.0-sNaPsHoT", "1.0-20261016.123456-1"):
            with self.subTest(version=version), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self.inventory(root, build, version=version)
                next(root.rglob("*.pom")).write_text("<project/>")
                with self.assertRaises(build.Rejected):
                    build.input_inventory(root)

    def test_inventory_rejects_unresolved_layout_without_version_evidence(self):
        build = recipe_module()
        for name in ("loose.jar", "a/1/a-1.jar", "com/a/1/wrong-1.jar", "com/a/1/a-2.jar"):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self.inventory(root, build)
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b"unknown version")
                with self.assertRaisesRegex(build.Rejected, "INPUT_LAYOUT_INVALID"):
                    build.input_inventory(root)


class PayloadTest(unittest.TestCase):
    def test_zip_preserves_manifest_and_existing_content(self):
        build = recipe_module()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source.jar"
            manifest = b"Manifest-Version: 1.0\r\nCreated-By: exact\r\n\r\n"
            with zipfile.ZipFile(source, "w") as output:
                output.writestr("META-INF/MANIFEST.MF", manifest)
                output.writestr("data", b"original")
            target = root / "out.jar"
            build.normalize_zip(source, target, {"META-INF/LICENSE": b"license"}, 1700000000)
            with zipfile.ZipFile(target) as output:
                self.assertEqual(manifest, output.read("META-INF/MANIFEST.MF"))
                self.assertEqual(b"original", output.read("data"))
            with self.assertRaises(build.Rejected):
                build.normalize_zip(source, target, {"data": b"replacement"}, 1700000000)

    def test_zip_rejects_signatures_duplicates_and_conflicting_legal(self):
        build = recipe_module()
        for names in (("META-INF/A.SF",), ("same", "same"), ("META-INF/LICENSE",)):
            with self.subTest(names=names), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                with zipfile.ZipFile(root / "in.jar", "w") as output:
                    for name in names:
                        output.writestr(name, b"original")
                with self.assertRaises(build.Rejected):
                    build.normalize_zip(root / "in.jar", root / "out.jar",
                                        {"META-INF/LICENSE": b"different"}, 1700000000)

    def test_consumer_pom_retains_direct_dependency_semantics(self):
        build = recipe_module()
        effective = '''<project><groupId>com.co.kc.thirdparty.sofa</groupId><artifactId>jraft-core</artifactId>
        <version>1.4.1-imchat.1</version><dependencyManagement><dependencies><dependency>
        <groupId>x</groupId><artifactId>a</artifactId><type>zip</type><classifier>native</classifier><version>2.0</version>
        </dependency></dependencies></dependencyManagement><dependencies><dependency><groupId>x</groupId>
        <artifactId>a</artifactId><scope>provided</scope><optional>true</optional><type>zip</type>
        <classifier>native</classifier><exclusions><exclusion><groupId>y</groupId><artifactId>b</artifactId>
        </exclusion></exclusions></dependency></dependencies></project>'''
        payload = build.consumer_pom(effective.encode())
        for text in (b"provided", b"true", b"native", b"zip", b"2.0", b"exclusions"):
            self.assertIn(text, payload)
        self.assertNotIn(b"<parent>", payload)
        for invalid in (effective.replace("2.0", "[2,3)"),
                        effective.replace("provided", "system"),
                        effective.replace("</project>", "<profiles><profile/></profiles></project>")):
            with self.assertRaises(build.Rejected):
                build.consumer_pom(invalid.encode())

    def test_reports_require_expected_suites_counts_and_reviewed_skips(self):
        build = recipe_module()
        required = {"ExampleTest": 1}
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for content in (None, '<testsuite name="ExampleTest" tests="0"/>',
                            '<testsuite name="ExampleTest" tests="1"><testcase name="a"><failure/></testcase></testsuite>',
                            '<testsuite name="ExampleTest" tests="1" skipped="1"><testcase classname="ExampleTest" name="a"><skipped message="unknown"/></testcase></testsuite>'):
                path = root / "TEST-ExampleTest.xml"
                if content is not None:
                    path.write_text(content)
                with self.assertRaises(build.Rejected):
                    build.check_reports(root, required, [])
            path.write_text('<testsuite name="ExampleTest" tests="1"><testcase classname="ExampleTest" name="a"/></testsuite>')
            self.assertEqual(1, build.check_reports(root, required, [])["tests"])

    def test_reports_allow_reviewed_skip_to_pass_but_require_present_case(self):
        build = recipe_module()
        reviewed = [
            {"class": "ExampleTest", "method": "optional", "message": "known", "text": ""},
            {"class": "ExampleTest", "method": "historical", "message": "", "text": "conditional"},
        ]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "TEST-ExampleTest.xml").write_text(
                '<testsuite name="ExampleTest" tests="3" skipped="1">'
                '<testcase classname="ExampleTest" name="optional"/>'
                '<testcase classname="ExampleTest" name="historical"><skipped message=""'
                '>conditional</skipped></testcase>'
                '<testcase classname="ExampleTest" name="required"/>'
                '</testsuite>')
            result = build.check_reports(root, {"ExampleTest": 3}, reviewed)
            self.assertEqual(3, result["tests"])
            self.assertEqual(1, len(result["skips"]))

            (root / "TEST-ExampleTest.xml").write_text(
                '<testsuite name="ExampleTest" tests="2" skipped="2">'
                '<testcase classname="ExampleTest" name="optional"><skipped message="known"/></testcase>'
                '<testcase classname="ExampleTest" name="historical"><skipped message="">conditional</skipped></testcase>'
                '</testsuite>')
            result = build.check_reports(root, {"ExampleTest": 2}, reviewed)
            self.assertEqual(2, result["tests"])
            self.assertEqual(2, len(result["skips"]))

            (root / "TEST-ExampleTest.xml").write_text(
                '<testsuite name="ExampleTest" tests="1" skipped="1">'
                '<testcase classname="ExampleTest" name="optional"><skipped message="new"/></testcase>'
                '</testsuite>')
            with self.assertRaisesRegex(build.Rejected, "UNREVIEWED_OR_MISSING_SKIP"):
                build.check_reports(root, {"ExampleTest": 1}, reviewed)

            (root / "TEST-ExampleTest.xml").write_text(
                '<testsuite name="ExampleTest" tests="1"><testcase classname="ExampleTest" name="missing"/></testsuite>')
            with self.assertRaisesRegex(build.Rejected, "UNREVIEWED_OR_MISSING_SKIP"):
                build.check_reports(root, {"ExampleTest": 1}, reviewed)

            (root / "TEST-ExampleTest.xml").write_text(
                '<testsuite name="ExampleTest" tests="1"><testcase classname="ExampleTest" name="required"><failure/></testcase></testsuite>')
            with self.assertRaisesRegex(build.Rejected, "TEST_FAILURE"):
                build.check_reports(root, {"ExampleTest": 1}, reviewed)

    def test_sequential_failure_mismatch_and_input_difference_have_no_acceptance(self):
        build = recipe_module()
        for mode in ("first_failure", "second_failure", "payload_mismatch", "inputs_mismatch"):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                calls = []
                def run_one(number):
                    calls.append(number)
                    if (mode == "first_failure" and number == 1) or (mode == "second_failure" and number == 2):
                        raise subprocess.CalledProcessError(17, "fake-maven")
                    return {"payload": {"jar": str(number) if mode == "payload_mismatch" else "same"},
                            "inputs": {"pom": str(number) if mode == "inputs_mismatch" else "same"}}
                with self.assertRaises((build.Rejected, subprocess.CalledProcessError)):
                    build.compare_builds(root, run_one)
                self.assertEqual([1] if mode == "first_failure" else [1, 2], calls)
                self.assertFalse((root / "accepted-comparison.json").exists())


class EffectivePhaseTest(unittest.TestCase):
    def setUp(self):
        self.build = recipe_module()

    def validate(self, plugins, extra=""):
        build = self.build
        payload = ("<project><groupId>com.co.kc.thirdparty.sofa</groupId>"
                   "<artifactId>jraft-core</artifactId><version>1.4.1-imchat.1</version>"
                   "<build><plugins>" + plugins + "</plugins></build>" + extra + "</project>").encode()
        return build.validate_effective(payload, {"build": {"requiredPlugins": {}}})

    def plugin(self, artifact, version, execution_id, goal, phase):
        return ("<plugin><artifactId>" + artifact + "</artifactId><version>" + version + "</version>"
                "<executions><execution><id>" + execution_id + "</id>"
                + ("<phase>" + phase + "</phase>" if phase is not None else "")
                + "<goals><goal>" + goal + "</goal></goals></execution></executions></plugin>")

    def test_active_effective_coordinate_versions_fail_closed(self):
        paths = ("dependencies/dependency", "dependencyManagement/dependencies/dependency",
                 "build/extensions/extension", "build/pluginManagement/plugins/plugin",
                 "reporting/plugins/plugin", "reporting/plugins/plugin/dependencies/dependency",
                 "build/plugins/plugin", "build/plugins/plugin/dependencies/dependency",
                 "build/pluginManagement/plugins/plugin/dependencies/dependency",
                 "profiles/profile/build/plugins/plugin/dependencies/dependency")
        for path in paths:
            for version in ("0.9.9-SNAPSHOT", "[1,2)", "LATEST", "RELEASE", "${unresolved}"):
                body = "<groupId>x</groupId><artifactId>a</artifactId><version>" + version + "</version>"
                for element in reversed(path.split("/")):
                    body = "<" + element + ">" + body + "</" + element + ">"
                if path.startswith("profiles/"):
                    body = body.replace("<profile>", "<profile><id>java9-plus</id>")
                with self.subTest(path=path, version=version), self.assertRaisesRegex(self.build.Rejected, "DYNAMIC_OR_UNRESOLVED_VERSION"):
                    self.validate("", body)

    def test_active_effective_coordinate_objects_require_versions(self):
        paths = ("build/extensions/extension", "reporting/plugins/plugin",
                 "build/plugins/plugin", "build/pluginManagement/plugins/plugin",
                 "build/plugins/plugin/dependencies/dependency",
                 "build/pluginManagement/plugins/plugin/dependencies/dependency",
                 "reporting/plugins/plugin/dependencies/dependency")
        for path in paths:
            body = "<groupId>x</groupId><artifactId>a</artifactId>"
            for element in reversed(path.split("/")):
                body = "<" + element + ">" + body + "</" + element + ">"
            # 父插件本身版本固定，单独验证缺版本的依赖对象。
            if "/dependencies/" in path:
                body = body.replace("<plugin>", "<plugin><version>1</version>")
            with self.subTest(path=path), self.assertRaisesRegex(self.build.Rejected, "DYNAMIC_OR_UNRESOLVED_VERSION"):
                self.validate("", body)
        self.assertIn(b"jraft-core", self.validate(""))
        managed = ("<dependencyManagement><dependencies><dependency><groupId>x</groupId>"
                   "<artifactId>a</artifactId><version>1</version></dependency></dependencies>"
                   "</dependencyManagement><dependencies><dependency><groupId>x</groupId>"
                   "<artifactId>a</artifactId></dependency></dependencies>")
        self.assertIn(b"<version>1</version>", self.validate("", managed))

    def test_effective_profile_constraints_and_skip_checks_are_retained(self):
        for extra, error in (("<profiles><profile><id>external</id></profile></profiles>", "UNSUPPORTED_EFFECTIVE_PROFILE"),
                             ("<properties><skipTests>true</skipTests></properties>", "TEST_SKIP_OR_IGNORE_FORBIDDEN"),
                             ("<profiles><profile><id>java9-plus</id><properties><skipTests>true</skipTests></properties></profile></profiles>", "TEST_SKIP_OR_IGNORE_FORBIDDEN")):
            with self.subTest(extra=extra), self.assertRaisesRegex(self.build.Rejected, error):
                self.validate("", extra)

    def test_real_effective_pom_default_publication_declarations_are_not_reached(self):
        # 实际失败 effective-pom.xml:768-817 的插件版本、execution id、phase 和 goal。
        plugins = (self.plugin("maven-install-plugin", "2.4", "default-install", "install", "install")
                   + self.plugin("maven-deploy-plugin", "2.7", "default-deploy", "deploy", "deploy")
                   + self.plugin("maven-site-plugin", "3.3", "default-deploy", "deploy", "site-deploy"))
        self.assertIn(b"<artifactId>jraft-core</artifactId>", self.validate(plugins))

    def test_publication_goals_in_reachable_default_phases_fail_closed(self):
        build = self.build
        phases = ("validate", "initialize", "generate-sources", "process-sources", "generate-resources",
                  "process-resources", "compile", "process-classes", "generate-test-sources",
                  "process-test-sources", "generate-test-resources", "process-test-resources",
                  "test-compile", "process-test-classes", "test", "prepare-package", "package",
                  "pre-integration-test", "integration-test", "post-integration-test", "verify")
        for goal in ("install", "deploy", "sign"):
            for phase in phases:
                with self.subTest(goal=goal, phase=phase), self.assertRaisesRegex(build.Rejected, "PUBLISH_EXECUTION_FORBIDDEN"):
                    self.validate(self.plugin("fixture-plugin", "1", "custom", goal, phase))

    def test_publication_goals_with_missing_unknown_or_custom_phases_fail_closed(self):
        build = self.build
        for goal in ("install", "deploy", "sign"):
            for phase in (None, "", "unknown", "custom-after-verify", "${phase}", "VERIFY", "site-deploy-extra"):
                with self.subTest(goal=goal, phase=phase), self.assertRaisesRegex(build.Rejected, "PUBLISH_EXECUTION_FORBIDDEN"):
                    self.validate(self.plugin("fixture-plugin", "1", "custom", goal, phase))

    def test_unreachable_phases_are_exact_not_plugin_exemptions(self):
        for phase in ("install", "deploy", "pre-site", "site", "post-site", "site-deploy"):
            with self.subTest(phase=phase):
                self.assertIn(b"jraft-core", self.validate(self.plugin("fixture-plugin", "1", "custom", "sign", phase)))
        build = self.build
        for artifact, goal in (("maven-install-plugin", "install"), ("maven-deploy-plugin", "deploy"),
                               ("maven-site-plugin", "deploy")):
            with self.subTest(artifact=artifact), self.assertRaisesRegex(build.Rejected, "PUBLISH_EXECUTION_FORBIDDEN"):
                self.validate(self.plugin(artifact, "1", "default-deploy", goal, "verify"))


class MainOrchestrationTest(unittest.TestCase):
    """真实 launcher/main + 假工具链；假 Maven 启动器保留 mavenrc/JAVA_HOME 行为。"""
    def fixture(self, root, mode="capture-input", failure="", wrong_home=False, wrong_runtime=False, unset_home=False):
        recipe = root / "recipe"
        recipe.mkdir()
        for name in ("build.py", "build.sh"):
            (recipe / name).write_bytes((HERE / name).read_bytes())
        build = recipe_module()
        source = root / "pristine"
        source.mkdir()
        files = {"pom.xml": b"<project/>\n", "LICENSE": b"license\n",
                 "jraft-core/pom.xml": b"<project/>\n",
                 "jraft-core/src/main/java/Example.java": b"class Example {}\n",
                 "jraft-core/src/test/java/ExampleTest.java": b"class ExampleTest {}\n"}
        for name, content in files.items():
            path = source / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
        archive = root / "source.tar"
        with tarfile.open(archive, "w") as tar:
            for name in files:
                tar.add(source / name, arcname=name, recursive=False)
        patch = b"diff --git a/pom.xml b/pom.xml\n--- a/pom.xml\n+++ b/pom.xml\n@@ -1 +1 @@\n-<project/>\n+<project><!-- reviewed --></project>\n"
        (recipe / "applied-membership.patch").write_bytes(patch)
        (recipe / "LICENSE").write_bytes(b"license\n")
        (recipe / "NOTICE").write_bytes(b"local notice\n")
        home = root / "home"
        home.mkdir()
        (home / ".mavenrc").write_text('touch "$HOME/rc-executed"\nexport MAVEN_OPTS="-DskipTests"\n')
        tools = root / "tools"
        tools.mkdir()
        java_home = root / "jdk"
        (java_home / "bin").mkdir(parents=True)
        java = '#!/bin/sh\nprintf "%s\\n" \'fixture java 21.0.10\'\n'
        (tools / "java").write_text(java)
        (java_home / "bin/java").write_text(java.replace("21.0.10", "17.0.1") if wrong_home else java)
        (tools / "java").chmod(0o755)
        (java_home / "bin/java").chmod(0o755)
        # 与 Maven3.8.4 一致，非空 MAVEN_SKIP_RC 才阻止 rc；wrapper 的其余部分只模拟工具行为。
        (tools / "mvn").write_text('#!/bin/sh\nif [ -z "$MAVEN_SKIP_RC" ] && [ -f "$HOME/.mavenrc" ]; then . "$HOME/.mavenrc"; fi\nexec ' +
                                 __import__('shlex').quote(__import__('sys').executable) + ' ' +
                                 __import__('shlex').quote(str(root / "fake_maven.py")) + ' "$@"\n')
        (tools / "mvn").chmod(0o755)
        fake = '''import json, os, pathlib, sys, zipfile
root = pathlib.Path(__file__).parent
args = sys.argv[1:]
with (root / "calls.jsonl").open("a") as stream:
    stream.write(json.dumps({"args": args, "skipRc": os.environ.get("MAVEN_SKIP_RC"), "opts": os.environ.get("MAVEN_OPTS")}) + "\\n")
if "-version" in args:
    print("Fixture Maven 3.8.4")
    print("Java version: " + ("17.0.1" if WRONG_RUNTIME else "21.0.10") + ", vendor: Fixture Vendor, runtime: " + os.environ["JAVA_HOME"])
    sys.exit(0)
work = pathlib.Path.cwd()
if any("effective-pom" in arg for arg in args):
    target = pathlib.Path(next(arg.split("=", 1)[1] for arg in args if arg.startswith("-Doutput=")))
    target.write_text('<project><groupId>com.co.kc.thirdparty.sofa</groupId><artifactId>jraft-core</artifactId><version>1.4.1-imchat.1</version><dependencies/><build><plugins/></build></project>')
    sys.exit(0)
number = work.parent.name
if FAILURE == number:
    sys.exit(17)
repo = pathlib.Path(next(arg.split("=", 1)[1] for arg in args if arg.startswith("-Dmaven.repo.local=")))
(repo / "x/a/1").mkdir(parents=True)
(repo / "x/a/1/a-1.pom").write_text('<project><version>1</version></project>')
(repo / "x/a/1/a-1.jar").write_bytes(b"input")
target = work / "jraft-core/target"
(target / "surefire-reports").mkdir(parents=True)
if FAILURE != "missing-reports":
    (target / "surefire-reports/TEST-ExampleTest.xml").write_text('<testsuite name="ExampleTest" tests="1"><testcase classname="ExampleTest" name="a"/></testsuite>')
embedded = "META-INF/maven/com.co.kc.thirdparty.sofa/jraft-core/"
with zipfile.ZipFile(target / "jraft-core-1.4.1-imchat.1.jar", "w") as jar:
    jar.writestr("META-INF/MANIFEST.MF", b"Manifest-Version: 1.0\\r\\n\\r\\n")
    jar.writestr(embedded + "pom.xml", '<project><groupId>com.co.kc.thirdparty.sofa</groupId><artifactId>jraft-core</artifactId><version>1.4.1-imchat.1</version></project>')
    jar.writestr(embedded + "pom.properties", "groupId=com.co.kc.thirdparty.sofa\\nartifactId=jraft-core\\nversion=1.4.1-imchat.1\\n")
with zipfile.ZipFile(target / "jraft-core-1.4.1-imchat.1-sources.jar", "w") as jar:
    jar.writestr("Example.java", (work / "jraft-core/src/main/java/Example.java").read_bytes())
'''
        (root / "fake_maven.py").write_text("WRONG_RUNTIME = " + repr(wrong_runtime) + "\nFAILURE = " + repr(failure) + "\n" + fake)
        lock = {"x/a/1/a-1.jar": build.sha(b"input"), "x/a/1/a-1.pom": build.sha(b"<project><version>1</version></project>")}
        (recipe / "input-lock.json").write_bytes(build.json_bytes(lock))
        manifest = {"sourceCommit": "a" * 40, "sourceArchiveSha256": build.file_sha(archive),
                    "sourceFilesManifestSha256": build.sha(build.json_bytes(build.source_inventory(source))),
                    "patchSha256": build.sha(patch), "patchAllowedPaths": ["pom.xml"],
                    "licenseSha256": build.sha(b"license\n"), "noticeSha256": build.sha(b"local notice\n"),
                    "sourceMappingEvidence": "fixture", "sourceDateEpoch": 1700000000,
                    "coordinate": ":".join(build.GAV), "requiredSuites": {"ExampleTest": 1}, "reviewedSkips": [],
                    "inputLockSha256": build.file_sha(recipe / "input-lock.json"), "inputInventoryReview": "accepted",
                    "build": {"javaVersionOutput": "fixture java 21.0.10", "mavenIdentity": "Fixture Maven 3.8.4",
                              "javaRuntimeVersion": "21.0.10", "javaVendor": "Fixture Vendor",
                              "os": __import__('platform').system(), "architecture": __import__('platform').machine(),
                              "native": "fixture", "requiredPlugins": {}}}
        (recipe / "provenance.yml").write_bytes(build.json_bytes(manifest))
        environment = {k: v for k, v in os.environ.items() if k not in build.FORBIDDEN_ENV}
        environment.update(PATH=str(tools) + os.pathsep + os.environ["PATH"], HOME=str(home),
                           JAVA_HOME=str(java_home), JRAFT_SOURCE_ARCHIVE=str(archive),
                           JRAFT_BUILD_OUTPUT=str(root / "output"), JRAFT_INPUT_MODE=mode)
        if unset_home:
            # 无 JAVA_HOME 时 PATH 指向同一个 JDK 可执行文件，允许不同安装位置。
            (tools / "java").unlink()
            (tools / "java").symlink_to(java_home / "bin/java")
            environment.pop("JAVA_HOME")
        return recipe, environment

    def launch(self, root, **options):
        recipe, environment = self.fixture(root, **options)
        return subprocess.run(["bash", str(recipe / "build.sh")], env=environment, capture_output=True, text=True, timeout=20)

    def test_main_disables_mavenrc_and_captures_without_acceptance(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            result = self.launch(root)
            self.assertFalse((root / "home/rc-executed").exists(), result.stderr)
            self.assertEqual(0, result.returncode, result.stderr)
            calls = [json.loads(line) for line in (root / "calls.jsonl").read_text().splitlines()]
            self.assertTrue(all(c["skipRc"] == "1" and not c["opts"] for c in calls))
            builds = [c["args"] for c in calls if "verify" in c["args"]]
            self.assertEqual(2, len(builds))
            for args in builds:
                self.assertEqual(["-pl", "jraft-core", "-am", "verify"], args[-4:])
                self.assertEqual(["-Pjava9-plus,!deploy"], [arg for arg in args if arg.startswith("-P")])
                self.assertFalse(any(arg in ("install", "deploy", "sign", "site", "site-deploy") for arg in args))
            self.assertTrue((root / "output/compared-inputs.json").exists())
            for number in (1, 2):
                audit = json.loads((root / ("output/build-" + str(number)) / "raw-model-audit.json").read_text())
                self.assertEqual("raw-model-declarations-not-executed-graph", audit["kind"])
                self.assertEqual("1", audit["declarations"][0]["version"])
            self.assertFalse((root / "output/accepted-comparison.json").exists())

    def test_main_pins_build_metadata_user_for_every_maven_invocation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            result = self.launch(root)
            self.assertEqual(0, result.returncode, result.stderr)
            calls = [json.loads(line)["args"] for line in (root / "calls.jsonl").read_text().splitlines()]
            self.assertEqual(5, len(calls))  # version + effective POM/verify for both builds
            self.assertEqual(2, sum(any("effective-pom" in arg for arg in args) for args in calls))
            self.assertEqual(2, sum("verify" in args for args in calls))
            for args in calls:
                self.assertEqual(["-Duser.name=kc"], [arg for arg in args if arg.startswith("-Duser.name=")])

    def test_main_rejects_environment_user_override_before_maven(self):
        for name in recipe_module().FORBIDDEN_ENV:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                recipe, environment = self.fixture(root)
                environment[name] = "-Duser.name=other"
                result = subprocess.run(["bash", str(recipe / "build.sh")], env=environment,
                                        capture_output=True, text=True, timeout=20)
                self.assertEqual(65, result.returncode, result.stderr)
                self.assertIn("UNCONTROLLED_BUILD_OPTIONS", result.stderr)
                self.assertFalse((root / "calls.jsonl").exists())

    def test_main_without_java_home_uses_resolved_path_jdk(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            result = self.launch(root, unset_home=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertFalse((root / "home/rc-executed").exists())
            self.assertTrue((root / "output/compared-inputs.json").exists())
            self.assertFalse((root / "output/accepted-comparison.json").exists())

    def test_main_rejects_java_home_identity_even_with_allowed_path_java(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            result = self.launch(root, wrong_home=True)
            self.assertEqual(65, result.returncode, result.stderr)
            self.assertIn("UNSUPPORTED_JDK_IDENTITY", result.stderr)
            self.assertFalse((root / "output/build-1").exists())
            self.assertFalse((root / "output/accepted-comparison.json").exists())

    def test_main_rejects_maven_runtime_identity_mismatch(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            result = self.launch(root, wrong_runtime=True)
            self.assertEqual(65, result.returncode, result.stderr)
            self.assertIn("MAVEN_JAVA_RUNTIME_MISMATCH", result.stderr)
            self.assertFalse((root / "output/build-1").exists())

    def test_main_lock_success_and_build_failures(self):
        for failure in ("", "build-1", "build-2", "missing-reports"):
            with self.subTest(failure=failure), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                result = self.launch(root, mode="verify-lock", failure=failure)
                self.assertEqual(0 if not failure else 65 if failure == "missing-reports" else 17,
                                 result.returncode, result.stderr)
                self.assertEqual(not failure, (root / "output/accepted-comparison.json").exists())
                if failure in ("build-1", "missing-reports"):
                    self.assertFalse((root / "output/build-2").exists())


if __name__ == "__main__":
    unittest.main()
