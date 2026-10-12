#!/bin/bash

# Put Apache Maven 3.9.16 ahead of the runner's Maven for the rest of the job.
#
# The ubuntu-24.04 runner image 20261004 moved to Maven 3.10.0, and publishing
# to Central broke twice over: central-publishing-maven-plugin 0.11.0 bundles
# the maven-metadata-local.xml that 3.10 writes into its staging repository,
# which Central rejects ("Bundle has content that does NOT have a .pom file"),
# and 3.10 withholds the `central` server credentials from central.sonatype.com
# because the id's declared origin is repo.maven.apache.org. Drop this once the
# plugin handles 3.10 (mavenplugins/central-publishing-maven-plugin#79).
#
# GitHub Actions only: RUNNER_TEMP and GITHUB_PATH come from the runner.

set -euo pipefail

version=3.9.16
sha512=831a8591fe20c8243b1dbe7d71e3244f31d1665b0804b2e825e38cbbe5ce0cafb8338851f90780735568773e0a6cd07bbec107cda0b896b008b861075358b6f6
url="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${version}/apache-maven-${version}-bin.tar.gz"

: "${RUNNER_TEMP:?}" "${GITHUB_PATH:?}"

archive="${RUNNER_TEMP}/apache-maven-${version}-bin.tar.gz"
curl -fsSL --retry 3 -o "$archive" "$url"
echo "${sha512}  ${archive}" | shasum -a 512 -c - >/dev/null
tar -xzf "$archive" -C "$RUNNER_TEMP"

echo "${RUNNER_TEMP}/apache-maven-${version}/bin" >> "$GITHUB_PATH"
