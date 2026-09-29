#!/usr/bin/env bash
set -euo pipefail

destination_repository=${1:-prebuilts/tdlib-maven}
version_file=${TDLIB_VERSION_FILE:-gradle/tdlib.versions.properties}

if [[ -z "${GITHUB_REPOSITORY:-}" || -z "${GH_TOKEN:-}" ]]; then
  echo "GITHUB_REPOSITORY and GH_TOKEN are required to download the pinned Actions artifact." >&2
  exit 1
fi

# The checked-in file contains only controlled shell-safe key/value pairs.
# shellcheck disable=SC1090
source "$version_file"

abi_slug=${TDLIB_ANDROID_ABIS//,/-}
artifact_name="tdlib-android-${TDLIB_COMMIT}-${abi_slug}"
default_branch=$(gh api "/repos/${GITHUB_REPOSITORY}" --jq '.default_branch')
artifact_id=$(gh api \
  -H "Accept: application/vnd.github+json" \
  "/repos/${GITHUB_REPOSITORY}/actions/artifacts?name=${artifact_name}&per_page=100" \
  | jq -r --arg branch "$default_branch" \
    '[.artifacts[] | select(.expired == false and .workflow_run.head_branch == $branch)] | sort_by(.created_at) | reverse | .[0].id // empty')

if [[ -z "$artifact_id" ]]; then
  echo "No unexpired ${artifact_name} artifact from the default branch exists. Run the 'Build pinned TDLib' workflow there first." >&2
  exit 1
fi

work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT
gh api "/repos/${GITHUB_REPOSITORY}/actions/artifacts/${artifact_id}/zip" > "$work_dir/artifact.zip"
unzip -q "$work_dir/artifact.zip" -d "$work_dir/content"

(cd "$work_dir/content" && sha256sum --check SHA256SUMS)
mkdir -p "$destination_repository"
cp -R "$work_dir/content/maven-repository/." "$destination_repository/"
install -m 0644 "$work_dir/content/tdlib-metadata.properties" "$destination_repository/tdlib-metadata.properties"
echo "Installed pinned TDLib ${TDLIB_COMMIT} from Actions artifact ${artifact_id}."
