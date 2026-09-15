#!/usr/bin/env python3
"""Compare every pinned dependency against what is actually published.

    python3 tools/check-versions.py          # everything
    python3 tools/check-versions.py --stale  # only things that are behind

Reads `gradle/libs.versions.toml`, resolves each library and plugin to its Maven
coordinates, and fetches `maven-metadata.xml` from Google's Maven or Maven Central. No
Gradle plugin and no build-time dependency: this is a read-only look at the outside world,
so it cannot break the build, and it works even when the build does not.

Two "latest" columns, because the distinction matters for this project. `stable` ignores
anything with a pre-release qualifier; `newest` includes alphas. Parks is deliberately on
the Compose **alpha** BOM — the Material 3 Expressive APIs exist nowhere else — so a naive
"is it the newest stable" check would tell you to downgrade.

Gradle, the Android SDK and the GitHub Actions pins are not in the catalogue and are
checked separately at the end.
"""

import argparse
import re
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ElementTree
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

CATALOG = Path(__file__).resolve().parent.parent / "gradle/libs.versions.toml"
WRAPPER = Path(__file__).resolve().parent.parent / "gradle/wrapper/gradle-wrapper.properties"
WORKFLOWS = Path(__file__).resolve().parent.parent / ".github/workflows"

GOOGLE_MAVEN = "https://dl.google.com/dl/android/maven2"
MAVEN_CENTRAL = "https://repo1.maven.org/maven2"
PLUGIN_PORTAL = "https://plugins.gradle.org/m2"

# Groups served by Google's Maven rather than Central.
GOOGLE_PREFIXES = ("androidx.", "com.android.", "com.google.android.", "android.arch.")

PRERELEASE = re.compile(r"-(alpha|beta|rc|dev|m|eap|snapshot)", re.IGNORECASE)


def repos_for(group):
    """Google's Maven for androidx, Central otherwise — then the Gradle Plugin Portal as a
    fallback, because some plugin markers are published only there."""
    first = GOOGLE_MAVEN if group.startswith(GOOGLE_PREFIXES) else MAVEN_CENTRAL
    return [first, PLUGIN_PORTAL] if first is MAVEN_CENTRAL else [first, MAVEN_CENTRAL, PLUGIN_PORTAL]


def fetch_versions(group, artifact):
    last = "not found"
    for repo in repos_for(group):
        url = f"{repo}/{group.replace('.', '/')}/{artifact}/maven-metadata.xml"
        request = urllib.request.Request(url, headers={"User-Agent": "Parks-app/dev version check"})
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                tree = ElementTree.fromstring(response.read())
            return [v.text for v in tree.iter("version") if v.text], None
        except (urllib.error.URLError, urllib.error.HTTPError, ElementTree.ParseError) as error:
            last = type(error).__name__
    return None, last


def rank(version):
    """Sort key that orders 1.10.0 above 1.9.0, and a release above its own pre-releases.

    The second part is easy to get backwards. Comparing `4.13.2` with `4.13-rc-2`, the
    third component is the number 2 against the qualifier "rc", and the *number* has to
    win, or a release candidate outranks the release it preceded. A first version of this
    script had it inverted and reported junit 4.13-rc-2 as newer than 4.13.2.
    """
    # Leading numeric components decide first: 1.10.0 beats 1.9.0, and 4.13.2 beats 4.13.
    # Matched as one run rather than by splitting on every separator: splitting on hyphens
    # too pulled the digits out of a suffix, so `0.8.0-0.6.x-compat` read as 0.8.0.0.6 and
    # outranked plain 0.8.0.
    leading = re.match(r"(\d+(?:\.\d+)*)", version)
    numeric = [int(n) for n in leading.group(1).split(".")] if leading else []
    remainder = (version[len(leading.group(1)):] if leading else version).lower()
    # Then a release beats any pre-release of the *same* numbers. Without this flag the
    # suffix simply made the list longer, so 2.4.20-RC3 sorted above 2.4.20.
    is_release = 0 if PRERELEASE.search(version) else 1
    # And a plain release beats a *variant* of itself. kotlinx-datetime publishes
    # `0.8.0-0.6.x-compat` alongside `0.8.0`; it carries no pre-release qualifier, so
    # without this the compatibility build was reported as the newest version there is.
    is_plain = 1 if remainder == "" else 0
    return (numeric, is_release, is_plain, remainder)


def newest(versions, allow_prerelease):
    candidates = [v for v in versions if allow_prerelease or not PRERELEASE.search(v)]
    return max(candidates, key=rank) if candidates else None


def parse_catalog(text):
    """Minimal TOML read. The catalogue format is simple and stable, and depending on a
    TOML library for one file would make this script harder to run than the thing it
    checks."""
    versions, entries = {}, []
    section = None
    for line in text.splitlines():
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        if line.startswith("["):
            section = line.strip("[]")
            continue
        if section == "versions":
            name, _, value = line.partition("=")
            versions[name.strip()] = value.strip().strip('"')
        elif section in ("libraries", "plugins"):
            name, _, value = line.partition("=")
            name = name.strip()
            group = re.search(r'group\s*=\s*"([^"]+)"', value)
            artifact = re.search(r'name\s*=\s*"([^"]+)"', value)
            plugin_id = re.search(r'id\s*=\s*"([^"]+)"', value)
            ref = re.search(r'version\.ref\s*=\s*"([^"]+)"', value)
            literal = re.search(r'version\s*=\s*"([^"]+)"', value)
            if plugin_id:
                # A Gradle plugin is published under a marker artifact.
                group, artifact = plugin_id.group(1), f"{plugin_id.group(1)}.gradle.plugin"
            elif group and artifact:
                group, artifact = group.group(1), artifact.group(1)
            else:
                continue
            entries.append(
                {
                    "name": name,
                    "group": group,
                    "artifact": artifact,
                    "ref": ref.group(1) if ref else None,
                    "literal": literal.group(1) if literal else None,
                    "kind": section,
                }
            )
    return versions, entries


# Version ordering is fiddlier than it looks and this script got it wrong three separate
# ways while being written: a release candidate outranking its release, a suffix making a
# version sort higher merely by being longer, and a compatibility build's digits being
# swallowed into the numeric prefix. Each line below is one of those bugs.
SELF_TEST = [
    (["4.13.2", "4.13-rc-2"], "4.13.2"),
    (["2.4.20", "2.4.20-RC3"], "2.4.20"),
    (["1.9.0", "1.10.0", "1.10.0-rc01"], "1.10.0"),
    (["1.2.0", "1.2.1", "1.3.0-alpha11"], "1.3.0-alpha11"),
    (["0.7.1", "0.8.0", "0.8.0-0.6.x-compat", "0.8.0-rc02"], "0.8.0"),
    (["0.7.1", "0.7.1-0.6.x-compat"], "0.7.1"),
    (["1.1.7", "1.2.0-rc01"], "1.2.0-rc01"),
    (["9.4.0", "9.5.0-alpha05"], "9.5.0-alpha05"),
]


def self_test():
    failures = 0
    for versions, expected in SELF_TEST:
        got = max(versions, key=rank)
        if got != expected:
            failures += 1
            print(f"  FAIL max({versions}) = {got}, expected {expected}")
    print(f"  {len(SELF_TEST) - failures}/{len(SELF_TEST)} ordering cases pass")
    return 1 if failures else 0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--stale", action="store_true", help="only show what is behind")
    parser.add_argument("--self-test", action="store_true", help="check version ordering only")
    args = parser.parse_args()

    if args.self_test:
        return self_test()
    # Always run it: a silent ordering bug turns this whole report into confident fiction.
    if self_test() != 0:
        print("version ordering is broken; not reporting", file=sys.stderr)
        return 1

    versions, entries = parse_catalog(CATALOG.read_text())

    # A BOM-managed library has no version of its own; report it as inherited rather than
    # pretending it is unpinned.
    checkable = []
    for entry in entries:
        pinned = entry["literal"] or (versions.get(entry["ref"]) if entry["ref"] else None)
        entry["pinned"] = pinned
        if pinned:
            checkable.append(entry)

    with ThreadPoolExecutor(max_workers=12) as pool:
        results = list(pool.map(lambda e: fetch_versions(e["group"], e["artifact"]), checkable))

    rows, behind = [], 0
    for entry, (available, error) in zip(checkable, results):
        if error:
            rows.append((entry["name"], entry["pinned"], "?", "?", f"lookup failed: {error}"))
            continue
        stable = newest(available, allow_prerelease=False) or "-"
        latest = newest(available, allow_prerelease=True) or "-"
        pinned = entry["pinned"]
        is_behind = pinned != latest
        if is_behind:
            behind += 1
        note = ""
        if pinned == latest:
            note = "current"
        elif PRERELEASE.search(pinned) and pinned != latest:
            note = "behind (on a pre-release line)"
        elif pinned != stable:
            note = "BEHIND"
        else:
            note = f"stable, newer pre-release exists ({latest})"
        if args.stale and note == "current":
            continue
        rows.append((entry["name"], pinned, stable, latest, note))

    width = max((len(r[0]) for r in rows), default=10)
    print(f"{'dependency'.ljust(width)}  {'pinned':22} {'stable':22} {'newest':22} note")
    print("-" * (width + 76))
    for name, pinned, stable, latest, note in sorted(rows, key=lambda r: r[0]):
        print(f"{name.ljust(width)}  {pinned:22} {stable:22} {latest:22} {note}")

    inherited = [e["name"] for e in entries if not e["pinned"]]
    if inherited:
        print(f"\nVersion inherited from the Compose BOM ({len(inherited)}): {', '.join(inherited)}")

    print("\n--- not in the version catalogue, check by hand ---")
    wrapper = re.search(r"gradle-([\d.]+)-bin\.zip", WRAPPER.read_text())
    print(f"  Gradle wrapper: {wrapper.group(1) if wrapper else '?'}"
          "  (latest: https://services.gradle.org/versions/current)")
    for workflow in sorted(WORKFLOWS.glob("*.yml")):
        for action in sorted(set(re.findall(r"uses:\s*([\w\-./]+@[\w.]+)", workflow.read_text()))):
            print(f"  {workflow.name}: {action}")
    print("  Android SDK: see `sdkmanager --list | grep platforms`")

    print(f"\n{behind} of {len(checkable)} pinned dependencies are not on the newest published version.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
