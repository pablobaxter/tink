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

import com.google.crypto.tink.internal.KeyTypeManager;
import com.google.crypto.tink.proto.JwtEcdsaAlgorithm;
import com.google.crypto.tink.proto.JwtEcdsaKeyFormat;
import com.google.crypto.tink.proto.JwtEcdsaPrivateKey;
import com.google.crypto.tink.proto.JwtEcdsaPublicKey;
import com.google.crypto.tink.proto.KeyData.KeyMaterialType;
import com.google.crypto.tink.testing.TestUtil;
import java.security.GeneralSecurityException;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

/** Unit tests for EcdsaVerifyKeyManager. */
@RunWith(Theories.class)
public final class JwtEcdsaVerifyKeyManagerTest {
  private final JwtEcdsaSignKeyManager signManager = new JwtEcdsaSignKeyManager();
  private final KeyTypeManager.KeyFactory<JwtEcdsaKeyFormat, JwtEcdsaPrivateKey> factory =
      signManager.keyFactory();
  private final JwtEcdsaVerifyKeyManager verifyManager = new JwtEcdsaVerifyKeyManager();

  @DataPoints("parametersAlgos")
  public static final JwtEcdsaAlgorithm[] PARAMETERS_ALGOS =
      new JwtEcdsaAlgorithm[] {
        JwtEcdsaAlgorithm.ES256, JwtEcdsaAlgorithm.ES384, JwtEcdsaAlgorithm.ES512
      };

  @Test
  public void basics() throws Exception {
    assertThat(verifyManager.getKeyType())
        .isEqualTo("type.googleapis.com/google.crypto.tink.JwtEcdsaPublicKey");
    assertThat(verifyManager.getVersion()).isEqualTo(0);
    assertThat(verifyManager.keyMaterialType()).isEqualTo(KeyMaterialType.ASYMMETRIC_PUBLIC);
  }

  @Test
  public void validateKey_empty_throw() throws Exception {
    assertThrows(
        GeneralSecurityException.class,
        () -> verifyManager.validateKey(JwtEcdsaPublicKey.getDefaultInstance()));
  }

  // Note: we use Theory as a parametrized test -- different from what the Theory framework intends.
  @Theory
  public void validateKey_ok(@FromDataPoints("parametersAlgos") JwtEcdsaAlgorithm algorithm)
      throws Exception {
    if (TestUtil.isTsan()) {
      // factory.createKey is too slow in Tsan.
      return;
    }
    JwtEcdsaKeyFormat keyFormat = JwtEcdsaKeyFormat.newBuilder().setAlgorithm(algorithm).build();
    JwtEcdsaPrivateKey privateKey = factory.createKey(keyFormat);
    JwtEcdsaPublicKey publicKey = signManager.getPublicKey(privateKey);
    verifyManager.validateKey(publicKey);
  }

  // Note: we use Theory as a parametrized test -- different from what the Theory framework intends.
  @Theory
  public void createPrimitive_ok(@FromDataPoints("parametersAlgos") JwtEcdsaAlgorithm algorithm)
      throws Exception {
    if (TestUtil.isTsan()) {
      // factory.createKey is too slow in Tsan.
      return;
    }
    JwtEcdsaKeyFormat keyFormat = JwtEcdsaKeyFormat.newBuilder().setAlgorithm(algorithm).build();
    JwtEcdsaPrivateKey privateKey = factory.createKey(keyFormat);
    JwtEcdsaPublicKey publicKey = signManager.getPublicKey(privateKey);
    JwtPublicKeySignInternal signer =
        signManager.getPrimitive(privateKey, JwtPublicKeySignInternal.class);
    JwtPublicKeyVerifyInternal verifier =
        verifyManager.getPrimitive(publicKey, JwtPublicKeyVerifyInternal.class);
    RawJwt token = RawJwt.newBuilder().withoutExpiration().build();
    JwtValidator validator = JwtValidator.newBuilder().allowMissingExpiration().build();
    verifier.verifyAndDecodeWithKid(
        signer.signAndEncodeWithKid(token, Optional.empty()), validator, Optional.empty());
  }

  // Note: we use Theory as a parametrized test -- different from what the Theory framework intends.
  @Theory
  public void createPrimitive_anotherKey_throw(
      @FromDataPoints("parametersAlgos") JwtEcdsaAlgorithm algorithm) throws Exception {
    if (TestUtil.isTsan()) {
      // factory.createKey is too slow in Tsan.
      return;
    }
    JwtEcdsaKeyFormat keyFormat = JwtEcdsaKeyFormat.newBuilder().setAlgorithm(algorithm).build();

    JwtEcdsaPrivateKey privateKey = factory.createKey(keyFormat);
    // Create a different key.
    JwtEcdsaPublicKey publicKey = signManager.getPublicKey(factory.createKey(keyFormat));
    JwtPublicKeySignInternal signer =
        signManager.getPrimitive(privateKey, JwtPublicKeySignInternal.class);
    JwtPublicKeyVerifyInternal verifier =
        verifyManager.getPrimitive(publicKey, JwtPublicKeyVerifyInternal.class);
    RawJwt token = RawJwt.newBuilder().withoutExpiration().build();
    JwtValidator validator = JwtValidator.newBuilder().allowMissingExpiration().build();
    assertThrows(
        GeneralSecurityException.class,
        () ->
            verifier.verifyAndDecodeWithKid(
                signer.signAndEncodeWithKid(token, Optional.empty()), validator, Optional.empty()));
  }
}
