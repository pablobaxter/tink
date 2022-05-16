# Copyright 2017 Google Inc.
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
####################################################################################

#!/bin/bash

# Fail on any error.
set -e

# Display commands to stderr.
set -x


readonly PLATFORM="$(uname | tr '[:upper:]' '[:lower:]')"

fail_with_debug_output() {
  ls -l
  df -h /
  exit 1
}

run_linux_tests() {
  local workspace_dir="$1"
  shift 1
  local manual_targets=("$@")

  # This is needed to handle recent Chrome distributions on macOS which have
  # paths with spaces.
  #
  # Context:
  # https://github.com/bazelbuild/bazel/issues/4327#issuecomment-627422865
  local -a BAZEL_FLAGS
  if [[ "${PLATFORM}" == 'darwin' && "${workspace_dir}" == 'javascript' ]]; then
    BAZEL_FLAGS+=( --experimental_inprocess_symlink_creation )
  fi
  readonly BAZEL_FLAGS

  local -a TEST_FLAGS=( --strategy=TestRunner=standalone --test_output=all )
  if [[ "${PLATFORM}" == 'darwin' ]]; then
    TEST_FLAGS+=( --jvmopt="-Djava.net.preferIPv6Addresses=true" )
  fi
  readonly TEST_FLAGS
  (
    cd "${workspace_dir}"
    time bazel build "${BAZEL_FLAGS[@]}" -- ... || fail_with_debug_output
    time bazel test "${BAZEL_FLAGS[@]}" "${TEST_FLAGS[@]}" -- ... \
      || fail_with_debug_output
    if (( ${#manual_targets[@]} > 0 )); then
      time bazel test "${TEST_FLAGS[@]}"  -- "${manual_targets[@]}" \
        || fail_with_debug_output
    fi
  )
}

run_all_linux_tests() {
  # TODO(b/228529710): Update using an easier to maintain approach to test
  # parity.
  if [[ "${KOKORO_JOB_NAME:-}" =~ ^tink/github ]]; then
    run_linux_tests "cc"
    run_linux_tests "java_src"
    run_linux_tests "go"
    run_linux_tests "python"
    run_linux_tests "tools"
    run_linux_tests "apps"
  fi

  run_linux_tests "javascript"
  run_linux_tests "examples/cc"

  local -a MANUAL_EXAMPLE_JAVA_TARGETS
  if [[ -n "${KOKORO_ROOT}" ]]; then
    MANUAL_EXAMPLE_JAVA_TARGETS=(
      "//gcs:gcs_envelope_aead_example_test"
      "//encryptedkeyset:encrypted_keyset_example_test"
      "//envelopeaead:envelope_aead_example_test"
    )
  fi
  readonly MANUAL_EXAMPLE_JAVA_TARGETS
  run_linux_tests "examples/java_src" "${MANUAL_EXAMPLE_JAVA_TARGETS[@]}"

  ## Install Tink and its dependencies via pip for the examples/python tests.
  install_tink_via_pip

  local -a MANUAL_EXAMPLE_PYTHON_TARGETS
  if [[ -n "${KOKORO_ROOT}" ]]; then
    MANUAL_EXAMPLE_PYTHON_TARGETS=(
      "//gcs:gcs_envelope_aead_test_package"
      "//gcs:gcs_envelope_aead_test"
      "//envelope_aead:envelope_test_package"
      "//envelope_aead:envelope_test"
      "//encrypted_keyset:encrypted_keyset_test_package"
      "//encrypted_keyset:encrypted_keyset_test"
    )
  fi
  readonly MANUAL_EXAMPLE_PYTHON_TARGETS
  run_linux_tests "examples/python" "${MANUAL_EXAMPLE_PYTHON_TARGETS[@]}"
}

run_macos_tests() {
  local -a BAZEL_FLAGS=(
    --compilation_mode=dbg --dynamic_mode=off --cpu=ios_x86_64
    --ios_cpu=x86_64 --experimental_enable_objc_cc_deps
    --ios_sdk_version="${IOS_SDK_VERSION}"
    --xcode_version="${XCODE_VERSION}" --verbose_failures
    --test_output=all
  )
  readonly BAZEL_FLAGS

  (
    cd objc

    # Build the iOS targets.
    time bazel build "${BAZEL_FLAGS[@]}" ... || fail_with_debug_output

    # Run the iOS tests.
    time bazel test "${BAZEL_FLAGS[@]}" :TinkTests || fail_with_debug_output
  )
}

install_tink_via_pip() {
  local -a PIP_FLAGS
  if [[ "${PLATFORM}" == 'darwin' ]]; then
    PIP_FLAGS=( --user )
  fi
  readonly PIP_FLAGS

  # Set path to Tink base folder
  export TINK_PYTHON_SETUPTOOLS_OVERRIDE_BASE_PATH="${PWD}"

  # Check if we can build Tink python package.
  pip3 install "${PIP_FLAGS[@]}" --upgrade pip
  # TODO(b/219813176): Remove once Kokoro environment is compatible.
  pip3 install "${PIP_FLAGS[@]}" --upgrade 'setuptools==60.9.0'
  pip3 install "${PIP_FLAGS[@]}" ./python

  # Install dependencies for the examples/python tests
  pip3 install "${PIP_FLAGS[@]}" google-cloud-storage
}

install_temp_protoc() {
  local protoc_version='3.19.3'
  local protoc_platform
  case "${PLATFORM}" in
    'linux')
      protoc_platform='linux-x86_64'
      ;;
    'darwin')
      protoc_platform='osx-x86_64'
      ;;
    *)
      echo "Unsupported platform, unable to install protoc."
      exit 1
      ;;
  esac
  local protoc_zip="protoc-${protoc_version}-${protoc_platform}.zip"
  local protoc_url="https://github.com/protocolbuffers/protobuf/releases/download/v${protoc_version}/${protoc_zip}"
  local -r protoc_tmpdir=$(mktemp -dt tink-protoc.XXXXXX)
  (
    cd "${protoc_tmpdir}"
    curl -OLsS "${protoc_url}"
    unzip ${protoc_zip} bin/protoc
  )
  export PATH="${protoc_tmpdir}/bin:${PATH}"
}

#######################################
# Test Maven packages.
# Globals:
#   PLATFORM
#   KOKORO_JOB_NAME
# Arguments:
#   None
#######################################
test_maven_packages() {
  # Only test in the Ubuntu environment.
  if [[ "${PLATFORM}" != "linux" ]]; then
    return
  fi

  local -a maven_script_flags
  if [[ "${KOKORO_JOB_NAME:-}" != "tink/github/gcp_ubuntu/continuous" ]]; then
    # Unless running the GitHub continuous job, deploy and test Maven packages
    # locally.
    #
    # Otherwise, snapshots will be published to the Maven Central repository.
    maven_script_flags+=( -l )
  fi
  readonly maven_script_flags

  ./maven/publish_snapshot.sh "${maven_script_flags[@]}"
  ./maven/test_snapshot.sh "${maven_script_flags[@]}"
}

main() {
  # Initialization for Kokoro environments.
  if [[ -n "${KOKORO_ROOT}" ]]; then
    cd "${KOKORO_ARTIFACTS_DIR}"/git*/tink*

    use_bazel.sh $(cat .bazelversion)

    # Install protoc into a temporary directory.
    install_temp_protoc

    if [[ "${PLATFORM}" == 'linux' ]]; then
      # Install a more recent Python. Sourcing required to update callers
      # environment.
      source ./kokoro/testutils/install_python3.sh
    fi

    if [[ "${PLATFORM}" == 'darwin' ]]; then
      # Default values for iOS SDK and Xcode. Can be overriden by another script.
      : "${IOS_SDK_VERSION:=13.2}"
      : "${XCODE_VERSION:=11.3}"

      export DEVELOPER_DIR="/Applications/Xcode_${XCODE_VERSION}.app/Contents/Developer"
      export ANDROID_HOME="/Users/kbuilder/Library/Android/sdk"
      export COURSIER_OPTS="-Djava.net.preferIPv6Addresses=true"

      # TODO(b/155225382): Avoid modifying the sytem Python installation.
      pip3 install --user protobuf
    fi

    ./kokoro/testutils/copy_credentials.sh "examples/java_src/testdata"
    ./kokoro/testutils/copy_credentials.sh "examples/python/testdata"
    ./kokoro/testutils/copy_credentials.sh "go/testdata"
    ./kokoro/testutils/copy_credentials.sh "java_src/testdata"
    ./kokoro/testutils/copy_credentials.sh "python/testdata"
    ./kokoro/testutils/copy_credentials.sh "tools/testdata"

    ./kokoro/testutils/update_android_sdk.sh
    # Sourcing required to update callers environment.
    source ./kokoro/testutils/install_go.sh
  fi

  # Verify required environment variables.

  # Required for building Java binaries.
  if [[ -z "${ANDROID_HOME}" ]]; then
    echo "The ANDROID_HOME environment variable must be set."
    exit 4
  fi

  if [[ -z "${TMP}" ]]; then
    echo "The TMP environment variable must be set."
    exit 4
  fi

  echo "using bazel binary: $(which bazel)"
  bazel version

  echo "using java binary: $(which java)"
  java -version

  echo "Using go binary from $(which go): $(go version)"

  echo "using python: $(which python)"
  python --version

  echo "using python3: $(which python3)"
  python3 --version

  echo "using pip3: $(which pip3)"
  pip3 --version
  pip3 list

  echo "using protoc: $(which protoc)"
  protoc --version

  run_all_linux_tests

  if [[ "${PLATFORM}" == 'darwin' ]]; then
    # TODO(b/155060426): re-enable after ObjC WORKSPACE is added.
    # run_macos_tests
    echo "*** ObjC tests not enabled yet."
  fi

  test_maven_packages
}

main "$@"
