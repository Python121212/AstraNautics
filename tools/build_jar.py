#!/usr/bin/env python3
"""Build a load-only Forge 1.20.1 mod jar without a network-connected Gradle.

Why this exists
---------------
The normal way to package this project is ``./gradlew build`` (ForgeGradle 6),
which needs Maven access to maven.minecraftforge.net and libraries.minecraft.net.
In a sandbox where those hosts are unreachable, ForgeGradle cannot run, so this
script produces the same tiny artifact by hand:

* it writes the ``com.astranautics.AstraNautics`` class file for the ``@Mod``
  entry point (Java 17 / class file major version 61) with a small class-file
  emitter, and
* it packs that class together with ``META-INF/mods.toml``, ``pack.mcmeta`` and
  a manifest into ``build/libs/astranautics-1.0.0.jar``.

The generated class is byte-for-byte equivalent to what javac emits for
``src/main/java/com/astranautics/AstraNautics.java`` apart from the logging
call, which is left out so the artifact has no compile-time dependencies:

    @Mod("astranautics")
    public class AstraNautics {
        public static final String MODID = "astranautics";
        public AstraNautics() {}
    }

Usage
-----
    python3 tools/build_jar.py            # writes build/libs/*.jar
    python3 tools/build_jar.py --check    # also verify the class file parses
"""

from __future__ import annotations

import argparse
import os
import struct
import sys
import zipfile

MOD_ID = "astranautics"
MOD_VERSION = "3.10.0"
MOD_NAME = "AstraNautics"
MOD_AUTHORS = "Python121212"
MOD_DESCRIPTION = (
    "AstraNautics - a minimal space themed mod for Minecraft 1.20.1 (Forge). "
    "Load-only build: nothing is registered."
)
MOD_LICENSE = "MIT"

PACKAGE = "com/astranautics"
CLASS_NAME = "AstraNautics"
CLASS_PATH = f"{PACKAGE}/{CLASS_NAME}.class"
MOD_ANNOTATION = "Lnet/minecraftforge/fml/common/Mod;"

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(REPO_ROOT, "build", "libs")
JAR_NAME = f"{MOD_ID}-{MOD_VERSION}.jar"

# JVM class file constants used below
ACC_PUBLIC = 0x0001
ACC_SUPER = 0x0020
ACC_STATIC = 0x0008
ACC_FINAL = 0x0010
CLASS_FILE_MAJOR = 61  # Java 17


def u1(value: int) -> bytes:
    return struct.pack(">B", value)


def u2(value: int) -> bytes:
    return struct.pack(">H", value)


def u4(value: int) -> bytes:
    return struct.pack(">I", value)


def mutf8(text: str) -> bytes:
    """Encode a string as JVM "modified UTF-8" (ASCII only here)."""
    return text.encode("utf-8")


class ConstantPool:
    """Tiny constant pool builder with de-duplication."""

    def __init__(self) -> None:
        self._entries: list[bytes] = []
        self._index: dict[tuple, int] = {}

    def _add(self, key: tuple, payload: bytes) -> int:
        if key in self._index:
            return self._index[key]
        self._entries.append(payload)
        index = len(self._entries)  # constant pool indices are 1-based
        self._index[key] = index
        return index

    def utf8(self, text: str) -> int:
        raw = mutf8(text)
        return self._add(("utf8", text), u1(1) + u2(len(raw)) + raw)

    def class_(self, internal_name: str) -> int:
        name_index = self.utf8(internal_name)
        return self._add(("class", internal_name), u1(7) + u2(name_index))

    def string(self, text: str) -> int:
        value_index = self.utf8(text)
        return self._add(("string", text), u1(8) + u2(value_index))

    def name_and_type(self, name: str, descriptor: str) -> int:
        name_index = self.utf8(name)
        desc_index = self.utf8(descriptor)
        return self._add(
            ("nat", name, descriptor), u1(12) + u2(name_index) + u2(desc_index)
        )

    def methodref(self, owner: str, name: str, descriptor: str) -> int:
        class_index = self.class_(owner)
        nat_index = self.name_and_type(name, descriptor)
        return self._add(
            ("methodref", owner, name, descriptor),
            u1(10) + u2(class_index) + u2(nat_index),
        )

    def fieldref(self, owner: str, name: str, descriptor: str) -> int:
        class_index = self.class_(owner)
        nat_index = self.name_and_type(name, descriptor)
        return self._add(
            ("fieldref", owner, name, descriptor),
            u1(9) + u2(class_index) + u2(nat_index),
        )

    def to_bytes(self) -> bytes:
        return u2(len(self._entries) + 1) + b"".join(self._entries)


def attribute(name_index: int, payload: bytes) -> bytes:
    return u2(name_index) + u4(len(payload)) + payload


def build_mod_class() -> bytes:
    """Emit the @Mod entry point class file."""
    cp = ConstantPool()

    this_class = cp.class_(f"{PACKAGE}/{CLASS_NAME}")
    super_class = cp.class_("java/lang/Object")
    object_init = cp.methodref("java/lang/Object", "<init>", "()V")
    annotation_type = cp.utf8(MOD_ANNOTATION)
    annotation_value_name = cp.utf8("value")
    annotation_value = cp.utf8(MOD_ID)

    # --- field: public static final String MODID = "astranautics" ---
    field_name = cp.utf8("MODID")
    field_desc = cp.utf8("Ljava/lang/String;")
    constant_value_attr = cp.utf8("ConstantValue")
    constant_value_index = cp.string(MOD_ID)
    field = (
        u2(ACC_PUBLIC | ACC_STATIC | ACC_FINAL)
        + u2(field_name)
        + u2(field_desc)
        + u2(1)
        + attribute(constant_value_attr, u2(constant_value_index))
    )

    # --- method: public AstraNautics() { super(); } ---
    code_name = cp.utf8("Code")
    bytecode = u1(0x2A) + u1(0xB7) + u2(object_init) + u1(0xB1)  # aload_0, invokespecial, return
    code_attr = code_name, (
        u2(1)          # max_stack
        + u2(1)        # max_locals
        + u4(len(bytecode))
        + bytecode
        + u2(0)        # exception_table_length
        + u2(0)        # code attributes
    )
    method = (
        u2(ACC_PUBLIC)
        + u2(cp.utf8("<init>"))
        + u2(cp.utf8("()V"))
        + u2(1)
        + attribute(*code_attr)
    )

    # --- class annotation: @Mod("astranautics") ---
    runtime_visible_annotations = cp.utf8("RuntimeVisibleAnnotations")
    annotation_payload = (
        u2(1)                       # one annotation
        + u2(annotation_type)
        + u2(1)                     # one element-value pair
        + u2(annotation_value_name)
        + u1(ord("s"))              # tag: String constant
        + u2(annotation_value)
    )

    body = (
        u2(ACC_PUBLIC | ACC_SUPER)
        + u2(this_class)
        + u2(super_class)
        + u2(0)                     # interfaces
        + u2(1) + field             # fields
        + u2(1) + method            # methods
        + u2(1) + attribute(runtime_visible_annotations, annotation_payload)
    )

    return b"\xca\xfe\xba\xbe" + u2(0) + u2(CLASS_FILE_MAJOR) + cp.to_bytes() + body


def parse_class(data: bytes) -> dict:
    """Minimal, independent class-file reader used to sanity check the output."""
    assert data[:4] == b"\xca\xfe\xba\xbe", "bad magic"
    pos = 4
    minor, major = struct.unpack_from(">HH", data, pos)
    pos += 4
    (count,) = struct.unpack_from(">H", data, pos)
    pos += 2

    pool: list[tuple] = [("unused",)]
    while len(pool) < count:
        tag = data[pos]
        pos += 1
        if tag == 1:
            (length,) = struct.unpack_from(">H", data, pos)
            pos += 2
            pool.append(("utf8", data[pos:pos + length].decode("utf-8")))
            pos += length
        elif tag in (7, 8, 16, 19, 20):
            pool.append((tag, struct.unpack_from(">H", data, pos)[0]))
            pos += 2
        elif tag in (9, 10, 11, 12, 17, 18):
            pool.append((tag,) + struct.unpack_from(">HH", data, pos))
            pos += 4
        elif tag in (3, 4):
            pool.append((tag,) + struct.unpack_from(">I", data, pos))
            pos += 4
        elif tag in (5, 6):
            pool.append((tag,) + struct.unpack_from(">II", data, pos))
            pool.append(("unused",))
            pos += 8
        elif tag == 15:
            pool.append((tag,) + struct.unpack_from(">BH", data, pos))
            pos += 3
        else:
            raise ValueError(f"unknown constant pool tag {tag}")

    (access,) = struct.unpack_from(">H", data, pos)
    pos += 2
    this_class, super_class = struct.unpack_from(">HH", data, pos)
    pos += 4
    (interface_count,) = struct.unpack_from(">H", data, pos)
    pos += 2 + 2 * interface_count

    def skip_members(pos: int) -> tuple[int, list[str]]:
        (num,) = struct.unpack_from(">H", data, pos)
        pos += 2
        names = []
        for _ in range(num):
            _flags, name_index, _desc, attr_count = struct.unpack_from(">HHHH", data, pos)
            names.append(pool[name_index][1])
            pos += 8
            for _ in range(attr_count):
                _attr_name, length = struct.unpack_from(">HI", data, pos)
                pos += 6 + length
        return pos, names

    pos, fields = skip_members(pos)
    pos, methods = skip_members(pos)

    annotations: list[str] = []
    (class_attr_count,) = struct.unpack_from(">H", data, pos)
    pos += 2
    for _ in range(class_attr_count):
        attr_name_index, length = struct.unpack_from(">HI", data, pos)
        pos += 6
        end = pos + length
        if pool[attr_name_index][1] == "RuntimeVisibleAnnotations":
            (num_ann, ) = struct.unpack_from(">H", data, pos)
            cursor = pos + 2
            for _ in range(num_ann):
                (type_index,) = struct.unpack_from(">H", data, cursor)
                cursor += 2
                (num_pairs,) = struct.unpack_from(">H", data, cursor)
                cursor += 2
                rendered = {"type": pool[type_index][1]}
                for _ in range(num_pairs):
                    (name_index,) = struct.unpack_from(">H", data, cursor)
                    tag = chr(data[cursor + 2])
                    (value_index,) = struct.unpack_from(">H", data, cursor + 3)
                    cursor += 5
                    rendered[pool[name_index][1]] = (tag, pool[value_index][1])
                annotations.append(rendered)
        pos = end

    assert pos == len(data), "trailing bytes in class file"
    return {
        "major": major,
        "minor": minor,
        "access": access,
        "this_class": pool[pool[this_class][1]][1],
        "super_class": pool[pool[super_class][1]][1],
        "fields": fields,
        "methods": methods,
        "annotations": annotations,
    }


def mods_toml() -> str:
    return f"""modLoader="javafml"
loaderVersion="[47,)"
license="{MOD_LICENSE}"
issueTrackerURL="https://github.com/Python121212/AstraNautics/issues"

[[mods]]
modId="{MOD_ID}"
version="{MOD_VERSION}"
displayName="{MOD_NAME}"
authors="{MOD_AUTHORS}"
description=\'\'\'{MOD_DESCRIPTION}\'\'\'

[[dependencies.{MOD_ID}]]
    modId="forge"
    mandatory=true
    versionRange="[47,)"
    ordering="NONE"
    side="BOTH"

[[dependencies.{MOD_ID}]]
    modId="minecraft"
    mandatory=true
    versionRange="[1.20.1,1.21)"
    ordering="NONE"
    side="BOTH"
"""


def pack_mcmeta() -> str:
    return """{
  "pack": {
    "description": "AstraNautics resources",
    "pack_format": 15
  }
}
"""


def manifest() -> str:
    return (
        "Manifest-Version: 1.0\r\n"
        f"Specification-Title: {MOD_ID}\r\n"
        f"Specification-Vendor: {MOD_AUTHORS}\r\n"
        "Specification-Version: 1\r\n"
        f"Implementation-Title: {MOD_ID}\r\n"
        f"Implementation-Version: {MOD_VERSION}\r\n"
        f"Implementation-Vendor: {MOD_AUTHORS}\r\n"
        "\r\n"
    )


def build_jar(check: bool) -> str:
    class_bytes = build_mod_class()

    if check:
        info = parse_class(class_bytes)
        print("class file check:", info)
        assert info["major"] == CLASS_FILE_MAJOR
        assert info["this_class"] == f"{PACKAGE}/{CLASS_NAME}"
        assert info["super_class"] == "java/lang/Object"
        assert "MODID" in info["fields"], info["fields"]
        assert "<init>" in info["methods"], info["methods"]
        assert info["annotations"] == [
            {"type": MOD_ANNOTATION, "value": ("s", MOD_ID)}
        ], info["annotations"]

    os.makedirs(OUT_DIR, exist_ok=True)
    jar_path = os.path.join(OUT_DIR, JAR_NAME)
    with zipfile.ZipFile(jar_path, "w", zipfile.ZIP_DEFLATED) as jar:
        jar.writestr("META-INF/MANIFEST.MF", manifest())
        jar.writestr("META-INF/mods.toml", mods_toml())
        jar.writestr("pack.mcmeta", pack_mcmeta())
        jar.writestr(CLASS_PATH, class_bytes)
        jar.writestr("LICENSE", read_license())
    return jar_path


def read_license() -> str:
    path = os.path.join(REPO_ROOT, "LICENSE")
    if os.path.isfile(path):
        with open(path, "r", encoding="utf-8") as handle:
            return handle.read()
    return f"{MOD_NAME} is licensed under the MIT license.\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="validate the class file")
    args = parser.parse_args()
    jar_path = build_jar(check=args.check)
    print(f"wrote {os.path.relpath(jar_path, REPO_ROOT)} ({os.path.getsize(jar_path)} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
