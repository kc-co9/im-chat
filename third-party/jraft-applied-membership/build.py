#!/usr/bin/env python3
"""固定 JRaft 输入的隔离 recipe；准入、双构建和 payload 比较均失败关闭。"""
import copy
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import platform
import re
import shutil
import subprocess
import sys
import tarfile
import time
import xml.etree.ElementTree as ET
import zipfile

# 确定性构建元数据标签，不表示操作系统认证用户。
MAVEN_ARGUMENTS = ["-Duser.name=kc"]
GAV = ("com.co.kc.thirdparty.sofa", "jraft-core", "1.4.1-imchat.1")
PROVENANCE_ENTRY = "META-INF/imchat-source-provenance.json"
ADDITIONS = {"META-INF/LICENSE", "META-INF/NOTICE", PROVENANCE_ENTRY}
FORBIDDEN_ENV = ("MAVEN_ARGS", "MAVEN_OPTS", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "MAVEN_PROJECTBASEDIR")


# 从 Maven 3.8.4 的 plexus-utils 3.3.0 EntityReplacementMap 实测提取；不是 HTML5 表。
MAX_INPUT_POM_BYTES = 8 * 1024 * 1024
MAVEN_DEFAULT_ENTITIES = {
    "nbsp": 160, "iexcl": 161, "cent": 162, "pound": 163, "curren": 164, "yen": 165,
    "brvbar": 166, "sect": 167, "uml": 168, "copy": 169, "ordf": 170, "laquo": 171,
    "not": 172, "shy": 173, "reg": 174, "macr": 175, "deg": 176, "plusmn": 177,
    "sup2": 178, "sup3": 179, "acute": 180, "micro": 181, "para": 182, "middot": 183,
    "cedil": 184, "sup1": 185, "ordm": 186, "raquo": 187, "frac14": 188, "frac12": 189,
    "frac34": 190, "iquest": 191, "Agrave": 192, "Aacute": 193, "Acirc": 194, "Atilde": 195,
    "Auml": 196, "Aring": 197, "AElig": 198, "Ccedil": 199, "Egrave": 200, "Eacute": 201,
    "Ecirc": 202, "Euml": 203, "Igrave": 204, "Iacute": 205, "Icirc": 206, "Iuml": 207,
    "ETH": 208, "Ntilde": 209, "Ograve": 210, "Oacute": 211, "Ocirc": 212, "Otilde": 213,
    "Ouml": 214, "times": 215, "Oslash": 216, "Ugrave": 217, "Uacute": 218, "Ucirc": 219,
    "Uuml": 220, "Yacute": 221, "THORN": 222, "szlig": 223, "agrave": 224, "aacute": 225,
    "acirc": 226, "atilde": 227, "auml": 228, "aring": 229, "aelig": 230, "ccedil": 231,
    "egrave": 232, "eacute": 233, "ecirc": 234, "euml": 235, "igrave": 236, "iacute": 237,
    "icirc": 238, "iuml": 239, "eth": 240, "ntilde": 241, "ograve": 242, "oacute": 243,
    "ocirc": 244, "otilde": 245, "ouml": 246, "divide": 247, "oslash": 248, "ugrave": 249,
    "uacute": 250, "ucirc": 251, "uuml": 252, "yacute": 253, "thorn": 254, "yuml": 255,
    "OElig": 338, "oelig": 339, "Scaron": 352, "scaron": 353, "Yuml": 376, "circ": 710,
    "tilde": 732, "ensp": 8194, "emsp": 8195, "thinsp": 8201, "zwnj": 8204, "zwj": 8205,
    "lrm": 8206, "rlm": 8207, "ndash": 8211, "mdash": 8212, "lsquo": 8216, "rsquo": 8217,
    "sbquo": 8218, "ldquo": 8220, "rdquo": 8221, "bdquo": 8222, "dagger": 8224, "Dagger": 8225,
    "permil": 8240, "lsaquo": 8249, "rsaquo": 8250, "euro": 8364, "fnof": 402, "Alpha": 913,
    "Beta": 914, "Gamma": 915, "Delta": 916, "Epsilon": 917, "Zeta": 918, "Eta": 919,
    "Theta": 920, "Iota": 921, "Kappa": 922, "Lambda": 923, "Mu": 924, "Nu": 925,
    "Xi": 926, "Omicron": 927, "Pi": 928, "Rho": 929, "Sigma": 931, "Tau": 932,
    "Upsilon": 933, "Phi": 934, "Chi": 935, "Psi": 936, "Omega": 937, "alpha": 945,
    "beta": 946, "gamma": 947, "delta": 948, "epsilon": 949, "zeta": 950, "eta": 951,
    "theta": 952, "iota": 953, "kappa": 954, "lambda": 955, "mu": 956, "nu": 957,
    "xi": 958, "omicron": 959, "pi": 960, "rho": 961, "sigmaf": 962, "sigma": 963,
    "tau": 964, "upsilon": 965, "phi": 966, "chi": 967, "psi": 968, "omega": 969,
    "thetasym": 977, "upsih": 978, "piv": 982, "bull": 8226, "hellip": 8230, "prime": 8242,
    "Prime": 8243, "oline": 8254, "frasl": 8260, "weierp": 8472, "image": 8465, "real": 8476,
    "trade": 8482, "alefsym": 8501, "larr": 8592, "uarr": 8593, "rarr": 8594, "darr": 8595,
    "harr": 8596, "crarr": 8629, "lArr": 8656, "uArr": 8657, "rArr": 8658, "dArr": 8659,
    "hArr": 8660, "forall": 8704, "part": 8706, "exist": 8707, "empty": 8709, "nabla": 8711,
    "isin": 8712, "notin": 8713, "ni": 8715, "prod": 8719, "sum": 8721, "minus": 8722,
    "lowast": 8727, "radic": 8730, "prop": 8733, "infin": 8734, "ang": 8736, "and": 8743,
    "or": 8744, "cap": 8745, "cup": 8746, "int": 8747, "there4": 8756, "sim": 8764,
    "cong": 8773, "asymp": 8776, "ne": 8800, "equiv": 8801, "le": 8804, "ge": 8805,
    "sub": 8834, "sup": 8835, "nsub": 8836, "sube": 8838, "supe": 8839, "oplus": 8853,
    "otimes": 8855, "perp": 8869, "sdot": 8901, "lceil": 8968, "rceil": 8969, "lfloor": 8970,
    "rfloor": 8971, "lang": 9001, "rang": 9002, "loz": 9674, "spades": 9824, "clubs": 9827,
    "hearts": 9829, "diams": 9830,
}


class Rejected(Exception):
    def __init__(self, message, code=65):
        super().__init__(message)
        self.code = code


def require(condition, message):
    if not condition:
        raise Rejected(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def file_sha(path):
    return sha(path.read_bytes())


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode()


def write_json(path, value):
    path.write_bytes(json_bytes(value))


def extract_source(archive, target):
    # 先检查全部成员，避免恶意后续条目在失败前写入部分目录。
    with tarfile.open(archive) as source:
        seen = set()
        members = source.getmembers()
        for member in members:
            path = PurePosixPath(member.name)
            require(member.name and not path.is_absolute() and ".." not in path.parts
                    and "\\" not in member.name and str(path) not in ("", "."), "UNSAFE_ARCHIVE_PATH")
            require(member.isfile() or member.isdir(), "UNSAFE_ARCHIVE_TYPE")
            require(str(path) not in seen, "DUPLICATE_ARCHIVE_ENTRY")
            seen.add(str(path))
        target.mkdir(parents=True, exist_ok=False)
        for member in members:
            output = target / member.name
            if member.isdir():
                output.mkdir(parents=True, exist_ok=True)
            else:
                output.parent.mkdir(parents=True, exist_ok=True)
                with source.extractfile(member) as stream:
                    output.write_bytes(stream.read())
                output.chmod(0o755 if member.mode & 0o111 else 0o644)


def patch_paths(patch, allowed):
    paths = re.findall(rb"^diff --git a/(\S+) b/(\S+)$", patch, re.M)
    require(paths and all(a == b and a.decode() in allowed for a, b in paths), "PATCH_SCOPE_INVALID")
    require(len(paths) == len(set(paths)), "PATCH_DUPLICATE_PATH")
    return [a.decode() for a, _ in paths]


def preflight(recipe, manifest, environment):
    require(re.fullmatch(r"[0-9a-f]{40}", manifest.get("sourceCommit") or ""),
            "SOURCE_PROVENANCE_MISSING: sourceCommit has not been verified")
    require(not any(environment.get(name) for name in FORBIDDEN_ENV), "UNCONTROLLED_BUILD_OPTIONS")
    archive = Path(environment.get("JRAFT_SOURCE_ARCHIVE", ""))
    if not archive.is_absolute() or not archive.is_file():
        raise Rejected("SOURCE_ARCHIVE_MISSING: absolute local tar required", 66)
    output = Path(environment.get("JRAFT_BUILD_OUTPUT", ""))
    require(output.is_absolute() and not output.is_symlink(), "OUTPUT_PATH_INVALID")
    require(not output.exists() or (output.is_dir() and not any(output.iterdir())), "OUTPUT_NOT_EMPTY")
    require(file_sha(archive) == manifest["sourceArchiveSha256"], "SOURCE_HASH_MISMATCH")
    for name, key in (("applied-membership.patch", "patchSha256"), ("LICENSE", "licenseSha256"),
                      ("NOTICE", "noticeSha256")):
        require(file_sha(recipe / name) == manifest[key], name + " HASH_MISMATCH")
    patch_paths((recipe / "applied-membership.patch").read_bytes(), manifest["patchAllowedPaths"])
    require(manifest.get("sourceMappingEvidence"), "SOURCE_MAPPING_EVIDENCE_MISSING")
    require(environment.get("JRAFT_INPUT_MODE", "capture-input") in ("capture-input", "verify-lock"), "INPUT_MODE_INVALID")
    if environment.get("JRAFT_INPUT_MODE") == "verify-lock":
        lock = recipe / "input-lock.json"
        require(manifest.get("inputLockSha256") and lock.is_file(), "REVIEWED_INPUT_LOCK_MISSING")
        require(file_sha(lock) == manifest["inputLockSha256"], "INPUT_LOCK_HASH_MISMATCH")
    return archive, output


def xml_root(payload):
    root = ET.fromstring(payload)
    for node in root.iter():
        node.tag = node.tag.rsplit("}", 1)[-1]
    return root


def text(node, name, default=""):
    return node.findtext(name, default=default).strip()


def snapshot_version(value):
    # Maven 3.8.4 ArtifactUtils.isSnapshot：后缀不要求连字符，时间戳版本也属于快照。
    return value.upper().endswith("SNAPSHOT") or re.fullmatch(r"(.*)-([0-9]{8}\.[0-9]{6})-([0-9]+)", value) is not None


def fixed_version(value):
    require(value and not snapshot_version(value)
            and value.upper() not in ("LATEST", "RELEASE") and "${" not in value
            and not any(c in value for c in "[],()"), "DYNAMIC_OR_UNRESOLVED_VERSION: " + value)
    return value


def dependency_key(node):
    return (text(node, "groupId"), text(node, "artifactId"), text(node, "type", "jar"), text(node, "classifier"))


def consumer_pom(payload):
    root = xml_root(payload)
    require(root.tag == "project", "MODULE_EFFECTIVE_POM_REQUIRED")
    require(tuple(text(root, key) for key in ("groupId", "artifactId", "version")) == GAV, "PRIVATE_GAV_MISMATCH")
    require(not root.findall("profiles/profile"), "UNSUPPORTED_CONSUMER_PROFILE")
    require(all(text(d, "groupId") and text(d, "artifactId") for d in root.findall("dependencies/dependency")), "DEPENDENCY_ID_MISSING")
    managed = {dependency_key(d): text(d, "version") for d in root.findall("dependencyManagement/dependencies/dependency")}
    output = ET.Element("project", xmlns="http://maven.apache.org/POM/4.0.0")
    for name, value in zip(("modelVersion", "groupId", "artifactId", "version", "packaging"), ("4.0.0", *GAV, "jar")):
        ET.SubElement(output, name).text = value
    licenses = root.find("licenses")
    if licenses is not None:
        output.append(copy.deepcopy(licenses))
    dependencies = ET.SubElement(output, "dependencies")
    for dependency in root.findall("dependencies/dependency"):
        d = copy.deepcopy(dependency)
        require(set(child.tag for child in d) <= {"groupId", "artifactId", "version", "type", "classifier", "scope", "optional", "exclusions"}, "UNSUPPORTED_DEPENDENCY_FIELD")
        require(text(d, "scope", "compile") in ("compile", "runtime", "provided", "test"), "UNSUPPORTED_DEPENDENCY_SCOPE")
        require(text(d, "artifactId") not in ("jraft-core", "jraft-parent", "jraft-test", "jraft-example")
                and not text(d, "artifactId").startswith("jraft-extension"), "SIBLING_OR_CORE_DEPENDENCY")
        version = fixed_version(text(d, "version") or managed.get(dependency_key(d), ""))
        if d.find("version") is None:
            ET.SubElement(d, "version").text = version
        require("${" not in ET.tostring(d, encoding="unicode"), "UNRESOLVED_DEPENDENCY")
        dependencies.append(d)
    ET.indent(output)
    return ET.tostring(output, encoding="utf-8", xml_declaration=True) + b"\n"


def normalize_zip(source, target, additions, epoch):
    require(set(additions) <= ADDITIONS, "UNAPPROVED_ARCHIVE_ADDITION")
    with zipfile.ZipFile(source) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), "DUPLICATE_ZIP_ENTRY")
        require(not any(re.match(r"META-INF/(?:[^/]+\.(?:SF|RSA|DSA|EC)|SIG-[^/]+)$", n, re.I) for n in names), "SIGNED_ZIP_UNSUPPORTED")
        entries = {name: archive.read(name) for name in names}
    for name, content in additions.items():
        require(name not in entries or entries[name] == content, "EXISTING_ADDITION_CONTENT_MISMATCH: " + name)
        entries[name] = content
    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name in sorted(entries):
            info = zipfile.ZipInfo(name, time.gmtime(epoch)[:6])
            info.create_system = 3
            info.external_attr = ((0o40755 if name.endswith("/") else 0o100644) << 16)
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, entries[name])


def check_reports(directory, required, reviewed_skips):
    paths = sorted(directory.glob("TEST-*.xml"))
    require(paths, "TEST_REPORTS_MISSING")
    found, skips, identities, passed = {}, [], set(), set()
    total = 0
    reviewed = {tuple(item.get(key, "") for key in ("class", "method", "message", "text"))
                for item in reviewed_skips}
    require(len(reviewed) == len(reviewed_skips), "DUPLICATE_REVIEWED_SKIP")
    for path in paths:
        root = ET.parse(path).getroot()
        name = root.get("name")
        cases = root.findall("testcase")
        count = int(root.get("tests", "0"))
        require(name and name not in found and count > 0 and count == len(cases), "EMPTY_OR_INVALID_SUITE: " + str(name))
        require(not root.findall(".//failure") and not root.findall(".//error")
                and int(root.get("failures", "0")) == 0 and int(root.get("errors", "0")) == 0, "TEST_FAILURE: " + name)
        for case in cases:
            identity = (case.get("classname") or "", case.get("name") or "")
            require(all(identity), "EMPTY_TESTCASE: " + name)
            require(identity not in identities, "DUPLICATE_TESTCASE: " + ".".join(identity))
            identities.add(identity)
            skipped = case.find("skipped")
            if skipped is not None:
                skips.append({"class": identity[0], "method": identity[1],
                              "message": skipped.get("message", ""), "text": skipped.text or ""})
            elif not case.find(".//failure") and not case.find(".//error"):
                passed.add(identity)
        require(int(root.get("skipped", "0")) == len([c for c in cases if c.find("skipped") is not None]), "SKIP_COUNT_MISMATCH")
        found[name] = count
        total += count
    require(all(found.get(name, 0) >= minimum for name, minimum in required.items()), "REQUIRED_SUITE_MISSING_OR_TOO_SMALL")
    actual = {tuple(item[key] for key in ("class", "method", "message", "text")) for item in skips}
    require(actual <= reviewed, "UNREVIEWED_OR_MISSING_SKIP")
    for item in reviewed - actual:
        require((item[0], item[1]) in passed, "UNREVIEWED_OR_MISSING_SKIP")
    return {"suites": found, "tests": total, "skips": skips}


def input_pom_root(payload):
    """仅在有界解析副本中兼容 Maven 默认实体；原始字节仍用于清单 SHA。"""
    require(len(payload) <= MAX_INPUT_POM_BYTES, "INPUT_POM_TOO_LARGE")
    # 先解码再检查声明，避免 UTF-16 的 NUL 字节绕过 DTD 检查。
    if payload.startswith((b"\xff\xfe", b"\xfe\xff")) or payload.startswith((b"<\x00?\x00", b"\x00<\x00?")):
        encoding = "utf-16" if payload.startswith((b"\xff\xfe", b"\xfe\xff")) else (
            "utf-16-le" if payload.startswith(b"<\x00") else "utf-16-be")
    else:
        declaration = re.match(rb"<\?xml\s[^?]*encoding\s*=\s*['\"]([^'\"]+)['\"]", payload)
        encoding = declaration[1].decode("ascii").lower() if declaration else "utf-8-sig"
        require(encoding in ("utf-8", "utf-8-sig", "us-ascii", "ascii", "iso-8859-1"),
                "INPUT_POM_ENCODING_UNSUPPORTED: " + encoding)
    source = payload.decode(encoding)

    # 单向扫描：遇到受保护区段仅查找一次结束符；缺失时立即拒绝，不能反复扫描后缀。
    tokens = re.compile(r"<!--|<!\[CDATA\[|<\?|<!DOCTYPE\b|<!ENTITY\b|&[A-Za-z][A-Za-z0-9]*;")
    closing = {"<!--": "-->", "<![CDATA[": "]]>", "<?": "?>"}
    chunks = []
    position = 0
    while True:
        match = tokens.search(source, position)
        if match is None:
            chunks.append(source[position:])
            break
        chunks.append(source[position:match.start()])
        token = match[0]
        if token in closing:
            end = source.find(closing[token], match.end())
            require(end != -1, "INPUT_POM_UNTERMINATED_SECTION: " + token)
            position = end + len(closing[token])
            chunks.append(source[match.start():position])
        else:
            require(not token.startswith("<!"), "INPUT_POM_DTD_FORBIDDEN")
            name = token[1:-1]
            # XML 五个预定义实体、数字引用及未知实体交给 ElementTree 严格处理。
            chunks.append("&#" + str(MAVEN_DEFAULT_ENTITIES[name]) + ";"
                          if name in MAVEN_DEFAULT_ENTITIES else token)
            position = match.end()
    normalized = "".join(chunks)
    # 最短实体也只扩成一个有界数字引用，无递归定义或外部资源读取。
    require(len(normalized) <= MAX_INPUT_POM_BYTES * 3, "INPUT_POM_EXPANSION_TOO_LARGE")
    return ET.fromstring(normalized)


def coordinate_versions(root):
    """只枚举 Maven 坐标路径；profile 声明不代表已激活或执行。"""
    namespace = root.tag[:-len("project")]
    def findall(node, path):
        return node.findall("/".join(namespace + part for part in path.split("/")))
    paths = ("dependencies/dependency/version", "dependencyManagement/dependencies/dependency/version",
             "build/plugins/plugin/version", "build/pluginManagement/plugins/plugin/version",
             "build/plugins/plugin/dependencies/dependency/version",
             "build/pluginManagement/plugins/plugin/dependencies/dependency/version",
             "reporting/plugins/plugin/version", "reporting/plugins/plugin/dependencies/dependency/version",
             "build/extensions/extension/version")
    for path in ("version", "parent/version", *paths):
        for node in findall(root, path):
            yield "project/" + path, "", (node.text or "").strip()
    for profile in findall(root, "profiles/profile"):
        profile_id = profile.findtext(namespace + "id", default="").strip()
        for path in paths:
            for node in findall(profile, path):
                yield "project/profiles/profile/" + path, profile_id, (node.text or "").strip()


def input_inventory(repository, raw_model_audit=None):
    # 全部物化 jar/POM 的路径版本及原始 SHA；不是传递图或逐项执行追踪。
    inputs = {}
    for path in sorted(repository.rglob("*")):
        if path.is_file() and path.suffix in (".jar", ".pom"):
            relative = path.relative_to(repository).as_posix()
            parts = PurePosixPath(relative).parts
            require(len(parts) >= 4, "INPUT_LAYOUT_INVALID: " + relative)
            artifact, version, filename = parts[-3:]
            stem = filename[:-len(path.suffix)]
            prefix = artifact + "-" + version
            require(stem == prefix or (stem.startswith(prefix + "-") and len(stem) > len(prefix) + 1),
                    "INPUT_LAYOUT_INVALID: " + relative)
            require(not snapshot_version(version), "SNAPSHOT_INPUT: " + relative)
            fixed_version(version)
            inputs[relative] = file_sha(path)
    require(inputs and any(n.endswith(".pom") for n in inputs) and any(n.endswith(".jar") for n in inputs), "INPUT_INVENTORY_INCOMPLETE")
    for name in inputs:
        if name.endswith(".pom"):
            root = input_pom_root((repository / name).read_bytes())
            require(root.tag in ("project", "{http://maven.apache.org/POM/4.0.0}project"),
                    "INPUT_POM_ROOT_INVALID: " + name)
            for model_path, profile_id, value in coordinate_versions(root):
                if "${" in value:
                    classification = "unresolved-property-declaration"
                else:
                    try:
                        fixed_version(value)
                        classification = "fixed-version-declaration"
                    except Rejected:
                        classification = "dynamic-or-snapshot-declaration"
                if raw_model_audit is not None:
                    raw_model_audit.append({"path": name, "modelPath": model_path, "profileId": profile_id,
                                            "version": value, "classification": classification})
    return inputs


def compare_builds(output, run_one):
    first = run_one(1)
    second = run_one(2)
    require(first["payload"] == second["payload"], "PAYLOAD_MISMATCH")
    require(first["inputs"] == second["inputs"], "RESOLVED_INPUTS_MISMATCH")
    result = {"payload": first["payload"], "inputs": first["inputs"], "productionAdoption": "blocked",
              "inventoryAssurance": "conservative isolated-repository jar/POM capture; not an executed-input trace or permanent dependency lock"}
    write_json(output / "compared-inputs.json", result)
    return result


def command(argv, cwd, environment, log):
    with log.open("ab") as stream:
        subprocess.run(argv, cwd=cwd, env=environment, stdout=stream, stderr=subprocess.STDOUT, check=True)


def validate_source(source, manifest):
    for name in ("pom.xml", "jraft-core/pom.xml", "jraft-core/src/main", "jraft-core/src/test", "LICENSE"):
        require((source / name).exists(), "INCOMPLETE_SOURCE: " + name)
    require(file_sha(source / "LICENSE") == manifest["licenseSha256"], "UPSTREAM_LICENSE_MISMATCH")
    require(not (source / "NOTICE").exists(), "UPSTREAM_NOTICE_CHANGED")
    require(not list(source.rglob(".mvn")), "UNCONTROLLED_MAVEN_PROJECT_CONFIG")
    require(not list(source.rglob("target")), "STALE_BUILD_OUTPUT_IN_SOURCE")
    files = source_inventory(source)
    require(sha(json_bytes(files)) == manifest["sourceFilesManifestSha256"], "SOURCE_FILE_MANIFEST_MISMATCH")


def source_inventory(source):
    # 输入树不排除任何名称为 log 的路径；src/**/storage/log 是真实上游源码。
    return {p.relative_to(source).as_posix(): file_sha(p) for p in source.rglob("*") if p.is_file()}


def validate_effective(payload, manifest):
    root = xml_root(payload)
    require(root.tag == "project", "MODULE_EFFECTIVE_POM_REQUIRED")
    # 检查合并后的模块模型，已知 profile 的保留声明也不豁免动态版本。
    # 不能据此证明外部传递 plugin/POM 的有效模型；该证据缺口保持采用阻塞。
    for model_path, profile_id, value in coordinate_versions(root):
        fixed_version(value)
    # 合并模型中存在的坐标对象不能因缺少 version 节点而逃过检查。
    # 直接消费依赖仍由 consumer_pom 按 dependencyManagement 补全版本。
    for model_path in ("dependencyManagement/dependencies/dependency",
                       "build/plugins/plugin", "build/pluginManagement/plugins/plugin",
                       "build/plugins/plugin/dependencies/dependency",
                       "build/pluginManagement/plugins/plugin/dependencies/dependency",
                       "build/extensions/extension", "reporting/plugins/plugin",
                       "reporting/plugins/plugin/dependencies/dependency"):
        for coordinate in root.findall(model_path):
            fixed_version(text(coordinate, "version"))
    for name in ("skipTests", "maven.test.skip", "testFailureIgnore", "maven.test.failure.ignore"):
        require(all((n.text or "").strip().lower() not in ("true", "1") for n in root.iter(name)), "TEST_SKIP_OR_IGNORE_FORBIDDEN")
    # 固定 -Pjava9-plus,!deploy；仅接受这两个已知的保留 profile 声明。
    profiles = root.find("profiles")
    if profiles is not None:
        require({text(p, "id") for p in profiles} <= {"java9-plus", "deploy"}, "UNSUPPORTED_EFFECTIVE_PROFILE")
        root.remove(profiles)
    consumer = consumer_pom(ET.tostring(root))
    plugins = root.findall("build/plugins/plugin")
    versions = {text(p, "artifactId"): text(p, "version") for p in plugins}
    for name, version in manifest["build"]["requiredPlugins"].items():
        require(versions.get(name) == version, "PLUGIN_VERSION_MISMATCH: " + name)
    for plugin in plugins:
        fixed_version(text(plugin, "version"))
        require(text(plugin, "artifactId") not in ("nexus-staging-maven-plugin", "maven-gpg-plugin"), "PUBLISH_PLUGIN_FORBIDDEN")
        for execution in plugin.findall("executions/execution"):
            if any((goal.text or "").strip() in ("deploy", "install", "sign")
                   for goal in execution.findall("goals/goal")):
                # 固定命令仅执行默认 lifecycle 到 verify；只豁免明确不可达的标准 phase。
                # 缺失、未知或自定义 phase 不推断插件默认绑定，仍失败关闭。
                require(text(execution, "phase") in ("install", "deploy", "pre-site", "site", "post-site", "site-deploy"),
                        "PUBLISH_EXECUTION_FORBIDDEN")
    return consumer


def run_build(number, recipe, archive, output, manifest, environment):
    work = output / ("build-" + str(number))
    work.mkdir()
    source, repository = work / "source", work / "m2"
    extract_source(archive, source)
    validate_source(source, manifest)
    log = work / "build.log"
    for args in (("git", "apply", "--check", str(recipe / "applied-membership.patch")),
                 ("git", "apply", str(recipe / "applied-membership.patch"))):
        command(list(args), source, environment, log)
    settings = work / "settings.xml"
    settings.write_text('<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"/>\n')
    base = ["mvn", *MAVEN_ARGUMENTS, "-B", "-ntp", "-s", str(settings), "-gs", str(settings),
            "-Dmaven.repo.local=" + str(repository), "-Pjava9-plus,!deploy",
            "-Dproject.build.outputTimestamp=" + str(manifest["sourceDateEpoch"])]
    effective = work / "effective-pom.xml"
    command(base + ["-f", "jraft-core/pom.xml", "org.apache.maven.plugins:maven-help-plugin:3.5.1:effective-pom",
                    "-Doutput=" + str(effective)], source, environment, log)
    consumer = validate_effective(effective.read_bytes(), manifest)
    command(base + ["-pl", "jraft-core", "-am", "verify"], source, environment, log)
    reports = check_reports(source / "jraft-core/target/surefire-reports", manifest["requiredSuites"], manifest["reviewedSkips"])
    payload = work / "payload"
    payload.mkdir()
    additions = {"META-INF/LICENSE": (recipe / "LICENSE").read_bytes(), "META-INF/NOTICE": (recipe / "NOTICE").read_bytes(),
                 PROVENANCE_ENTRY: json_bytes({key: manifest[key] for key in ("sourceCommit", "sourceArchiveSha256", "patchSha256", "coordinate", "licenseSha256", "noticeSha256")})}
    artifact = "jraft-core-" + GAV[2]
    binary = source / "jraft-core/target" / (artifact + ".jar")
    sources = source / "jraft-core/target" / (artifact + "-sources.jar")
    with zipfile.ZipFile(binary) as jar:
        embedded = "META-INF/maven/" + GAV[0] + "/jraft-core/"
        model = xml_root(jar.read(embedded + "pom.xml"))
        require(tuple(text(model, name) for name in ("groupId", "artifactId", "version")) == GAV, "EMBEDDED_GAV_MISMATCH")
        properties = jar.read(embedded + "pom.properties").decode()
        require(all(name + "=" + value in properties for name, value in zip(("groupId", "artifactId", "version"), GAV)), "EMBEDDED_PROPERTIES_MISMATCH")
    with zipfile.ZipFile(sources) as jar:
        for path in (source / "jraft-core/src/main/java").rglob("*.java"):
            require(jar.read(path.relative_to(source / "jraft-core/src/main/java").as_posix()) == path.read_bytes(), "SOURCES_JAR_MISMATCH")
    for original in (binary, sources):
        normalize_zip(original, payload / original.name, additions, manifest["sourceDateEpoch"])
    (payload / (artifact + ".pom")).write_bytes(consumer)
    raw_model_audit = []
    inputs = input_inventory(repository, raw_model_audit)
    write_json(work / "raw-model-audit.json", {"kind": "raw-model-declarations-not-executed-graph",
               "declarations": raw_model_audit,
               "assurance": "raw cached POM declarations only; profile activation and transitive external plugin effective models are not proven"})
    write_json(work / "resolved-inputs.json", {"kind": "conservative-isolated-repository-inventory", "files": inputs,
               "assurance": "all downloaded jar/POM bytes; not proof of executed inputs, resolved version ranges, or complete model/plugin execution tracing"})
    result = {"payload": {p.name: file_sha(p) for p in sorted(payload.iterdir())}, "inputs": inputs, "reports": reports}
    write_json(work / "result.json", result)
    return result


def main():
    recipe = Path(__file__).resolve().parent
    manifest = json.loads((recipe / "provenance.yml").read_text())
    archive, output = preflight(recipe, manifest, os.environ)
    # 全部源码和 patch 准入先于工具链探测/Maven，检查目录本身仍不修改输入。
    import tempfile
    with tempfile.TemporaryDirectory(prefix="jraft-preflight-") as temporary:
        source = Path(temporary) / "source"
        extract_source(archive, source)
        validate_source(source, manifest)
        subprocess.run(["git", "apply", "--check", str(recipe / "applied-membership.patch")], cwd=source, check=True, capture_output=True)
    environment = {name: value for name, value in os.environ.items() if name in ("PATH", "JAVA_HOME", "HOME", "TMPDIR")}
    environment.update(TZ="UTC", LANG="en_US.UTF-8", LC_ALL="en_US.UTF-8", SOURCE_DATE_EPOCH=str(manifest["sourceDateEpoch"]), MAVEN_SKIP_RC="1")
    # Maven 优先使用 JAVA_HOME；未设置时将 PATH 中实际 java 归一到同一 JDK，所有调用共用。
    if environment.get("JAVA_HOME"):
        java_executable = Path(environment["JAVA_HOME"]) / "bin/java"
    else:
        resolved = shutil.which("java", path=environment.get("PATH"))
        require(resolved is not None, "JAVA_EXECUTABLE_MISSING")
        java_executable = Path(resolved).resolve()
        environment["JAVA_HOME"] = str(java_executable.parent.parent)
    require(java_executable.is_file() and os.access(java_executable, os.X_OK), "JAVA_EXECUTABLE_INVALID")
    java = subprocess.check_output([str(java_executable), "-version"], stderr=subprocess.STDOUT, env=environment, text=True).strip()
    require(java == manifest["build"]["javaVersionOutput"], "UNSUPPORTED_JDK_IDENTITY")
    maven = subprocess.check_output(["mvn", *MAVEN_ARGUMENTS, "-version"], stderr=subprocess.STDOUT, env=environment, text=True)
    runtime = re.search(r"^Java version: ([^,]+), vendor: (.*), runtime: (.+)$", maven, re.M)
    require(runtime is not None and runtime.group(1) == manifest["build"]["javaRuntimeVersion"]
            and runtime.group(2) == manifest["build"]["javaVendor"], "MAVEN_JAVA_RUNTIME_MISMATCH")
    reported_home = Path(runtime.group(3)).resolve()
    require(reported_home == Path(environment["JAVA_HOME"]).resolve(), "MAVEN_JAVA_HOME_MISMATCH")
    require(maven.splitlines()[0] == manifest["build"]["mavenIdentity"], "UNSUPPORTED_MAVEN_IDENTITY")
    require(platform.system() == manifest["build"]["os"] and platform.machine() == manifest["build"]["architecture"], "UNSUPPORTED_NATIVE_PLATFORM")
    output.mkdir(parents=True, exist_ok=True)
    write_json(output / "toolchain.json", {"java": java, "maven": maven, "os": platform.platform(), "native": manifest["build"]["native"]})
    result = compare_builds(output, lambda n: run_build(n, recipe, archive, output, manifest, environment))
    if os.environ.get("JRAFT_INPUT_MODE", "capture-input") == "verify-lock":
        require(result["inputs"] == json.loads((recipe / "input-lock.json").read_text()), "REVIEWED_INPUT_LOCK_MISMATCH")
        require(manifest.get("inputInventoryReview") == "accepted", "INPUT_INVENTORY_REVIEW_MISSING")
        write_json(output / "accepted-comparison.json", result)
    else:
        print("INPUTS_CAPTURED_NOT_LOCK_VERIFIED: review compared-inputs.json; no accepted manifest")


if __name__ == "__main__":
    try:
        main()
    except Rejected as error:
        print(str(error), file=sys.stderr)
        sys.exit(error.code)
    except subprocess.CalledProcessError as error:
        print("SUBPROCESS_FAILED: " + str(error), file=sys.stderr)
        sys.exit(error.returncode if error.returncode > 0 else 1)
    except (OSError, ValueError, KeyError, ET.ParseError, tarfile.TarError, zipfile.BadZipFile) as error:
        print("RECIPE_INPUT_OR_OUTPUT_INVALID: " + str(error), file=sys.stderr)
        sys.exit(65)
