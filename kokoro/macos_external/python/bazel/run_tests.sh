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
cd ${KOKORO_ARTIFACTS_DIR}/git/tink

./kokoro/copy_credentials.sh

# Install protobuf pip packages
pip3 install protobuf --user

run_bazel_tests() {
  (
    cd python
    use_bazel.sh $(cat .bazelversion)

    time bazel build -- ...
    time bazel test --test_output=errors -- ...

    # Run manual tests which rely on key material injected into the Kokoro
    # environement.
    if [[ -n "${KOKORO_ROOT}" ]]; then
      declare -a MANUAL_TARGETS
      MANUAL_TARGETS=(
        "//tink/integration/gcpkms:_gcp_kms_aead_test"
      )
      readonly MANUAL_TARGETS
      time bazel test --test_output=errors -- "${MANUAL_TARGETS[@]}"
    fi
  )
}

run_bazel_tests
