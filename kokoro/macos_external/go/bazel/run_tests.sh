#!/bin/bash
# Copyright 2020 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
################################################################################

set -euo pipefail

if [[ -n "${KOKORO_ROOT:-}" ]]; then
  cd "${KOKORO_ARTIFACTS_DIR}/git/tink"
fi
./kokoro/testutils/copy_credentials.sh "go/testdata"
# Sourcing required to update callers environment.
source ./kokoro/testutils/install_go.sh

echo "Using go binary from $(which go): $(go version)"


if [[ -n "${KOKORO_ROOT:-}" ]]; then
  use_bazel.sh "$(cat go/.bazelversion)"
fi

# TODO(b/219879042): Add tests that build files are up-to-date.
# We already do these tests in gcp_ubuntu_per_language/go/bazel/run_tests.sh.
# Since macos uses an older version of bash, these tests don't work here yet.
# We either need to use a different version of bash, or replace readarray with
# while loops.

declare -a MANUAL_TARGETS
# Run manual tests which rely on test data only available via Bazel.
if [[ -n "${KOKORO_ROOT:-}" ]]; then
  MANUAL_TARGETS=(
    "//integration/gcpkms:gcpkms_test"
  )
fi
readonly MANUAL_TARGETS

./kokoro/testutils/run_bazel_tests.sh go "${MANUAL_TARGETS[@]}"
