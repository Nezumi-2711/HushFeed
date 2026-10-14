"""Writes patches-bundle.json for a release the Release workflow published.

  python .github/scripts/update_bundle_index.py --version 0.71.3 --repository owner/repo
        --created 2026-10-12T05:00:00Z --commit <sha> --out patches-bundle.json

Morphe Manager reads this file to find the newest bundle, so download_url points at this
repository's release. The description says what CI checked and nothing more: there are no fixture
APKs on the runner, so it doesn't claim the patches were applied to one.
"""

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

import release_text  # noqa: E402  (tools/ is on the path only from here)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    for name in ("--version", "--repository", "--created", "--commit", "--out"):
        parser.add_argument(name, required=True)
    args = parser.parse_args()

    if not release_text.VERSION.match(args.version):
        release_text.fail(f"{args.version} isn't X.Y.Z")
    if not re.fullmatch(r"[\w.-]+/[\w.-]+", args.repository):
        release_text.fail(f"{args.repository} isn't owner/repo")

    body = release_text.section(release_text.read(release_text.CHANGELOG), args.version + " (", legacy=True)
    if body is None:
        release_text.fail(f"CHANGELOG has no published {args.version}")
    changes = [
        "* " + item[len(release_text.SCOPE):] if item.startswith(release_text.SCOPE) else item
        for item in release_text.bullets(body)
    ]
    count, targets = release_text.catalog()
    if not targets:
        release_text.fail("patches-list.json names no TikTok versions")

    description = (
        f"Hushfeed v{args.version} has {count} patches for global TikTok "
        f"{release_text.spoken(targets)} (com.zhiliaoapp.musically).\n\n"
        + "\n".join(changes) + "\n\n"
        + f"Built by GitHub Actions from commit {args.commit[:12]}, with build provenance attested. "
        + f"Needs Morphe Manager {release_text.manager_floor()} or newer. Refresh the Hushfeed source to "
        + "update. The .mpp is a patch bundle, not a TikTok APK. Keep the manager's existing signing key "
        + "when updating."
    )
    if any(dash in description for dash in release_text.DASHES):
        release_text.fail("the description has an em or en dash")

    index_path = ROOT / "patches-bundle.json"
    index = json.loads(release_text.read(index_path))
    index.update({
        # The format the index has always used: UTC, to the second, no zone suffix.
        "created_at": args.created.rstrip("Z")[:19],
        "description": description,
        "download_url": f"https://github.com/{args.repository}/releases/download/"
                        f"v{args.version}/patches-{args.version}.mpp",
        "version": args.version,
    })
    release_text.write(Path(args.out), json.dumps(index, indent=2, ensure_ascii=False) + "\n")
    print(f"[release] index points at {args.repository} v{args.version} ({count} patches)")


if __name__ == "__main__":
    main()
