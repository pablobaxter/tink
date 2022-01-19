// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////
package com.google.crypto.tink.jwt;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.crypto.tink.KeyTypeManager;
import com.google.crypto.tink.proto.JwtRsaSsaPkcs1Algorithm;
import com.google.crypto.tink.proto.JwtRsaSsaPkcs1KeyFormat;
import com.google.crypto.tink.proto.JwtRsaSsaPkcs1PrivateKey;
import com.google.crypto.tink.proto.JwtRsaSsaPkcs1PublicKey;
import com.google.crypto.tink.proto.KeyData.KeyMaterialType;
import com.google.crypto.tink.testing.TestUtil;
import com.google.protobuf.ByteString;
import java.security.GeneralSecurityException;
import java.security.spec.RSAKeyGenParameterSpec;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for RsaSsaPkcs1VerifyKeyManager. */
@RunWith(JUnitParamsRunner.class)
public final class JwtRsaSsaPkcs1VerifyKeyManagerTest {
  private final JwtRsaSsaPkcs1SignKeyManager signManager = new JwtRsaSsaPkcs1SignKeyManager();
  private final KeyTypeManager.KeyFactory<JwtRsaSsaPkcs1KeyFormat, JwtRsaSsaPkcs1PrivateKey>
      factory = signManager.keyFactory();
  private final JwtRsaSsaPkcs1VerifyKeyManager verifyManager = new JwtRsaSsaPkcs1VerifyKeyManager();

  private static Object[] parametersAlgoAndSize() {
    return new Object[] {
      new Object[] {JwtRsaSsaPkcs1Algorithm.RS256, 2048},
      new Object[] {JwtRsaSsaPkcs1Algorithm.RS256, 3072},
      new Object[] {JwtRsaSsaPkcs1Algorithm.RS256, 4096},
      new Object[] {JwtRsaSsaPkcs1Algorithm.RS384, 2048},
      new Object[] {JwtRsaSsaPkcs1Algorithm.RS384, 3072},
      new Object[] {JwtRsaSsaPkcs1Algorithm.RS384, 4096},
      new Object[] {JwtRsaSsaPkcs1Algorithm.RS512, 2048},
      new Object[] {JwtRsaSsaPkcs1Algorithm.RS512, 3072},
      new Object[] {JwtRsaSsaPkcs1Algorithm.RS512, 4096},
    };
  }

  @Test
  public void basics() throws Exception {
    assertThat(verifyManager.getKeyType())
        .isEqualTo("type.googleapis.com/google.crypto.tink.JwtRsaSsaPkcs1PublicKey");
    assertThat(verifyManager.getVersion()).isEqualTo(0);
    assertThat(verifyManager.keyMaterialType()).isEqualTo(KeyMaterialType.ASYMMETRIC_PUBLIC);
  }

  @Test
  public void validateKey_empty_throw() throws Exception {
    assertThrows(
        GeneralSecurityException.class,
        () -> verifyManager.validateKey(JwtRsaSsaPkcs1PublicKey.getDefaultInstance()));
  }

  @Test
  @Parameters(method = "parametersAlgoAndSize")
  public void validateKey_ok(JwtRsaSsaPkcs1Algorithm algorithm, int keySize) throws Exception {
    if (TestUtil.isTsan()) {
      // factory.createKey is too slow in Tsan.
      return;
    }
    JwtRsaSsaPkcs1KeyFormat keyFormat =
        JwtRsaSsaPkcs1KeyFormat.newBuilder()
            .setAlgorithm(algorithm)
            .setModulusSizeInBits(keySize)
            .setPublicExponent(ByteString.copyFrom(RSAKeyGenParameterSpec.F4.toByteArray()))
            .build();
    JwtRsaSsaPkcs1PrivateKey privateKey = factory.createKey(keyFormat);
    JwtRsaSsaPkcs1PublicKey publicKey = signManager.getPublicKey(privateKey);
    verifyManager.validateKey(publicKey);
  }
}
