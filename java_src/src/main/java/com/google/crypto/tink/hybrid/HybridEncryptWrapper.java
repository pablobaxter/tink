// Copyright 2017 Google Inc.
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
package com.google.crypto.tink.hybrid;

import com.google.crypto.tink.HybridEncrypt;
import com.google.crypto.tink.PrimitiveSet;
import com.google.crypto.tink.PrimitiveWrapper;
import com.google.crypto.tink.Registry;
import com.google.crypto.tink.subtle.Bytes;
import java.security.GeneralSecurityException;

/**
 * The implementation of {@code PrimitiveWrapper<HybridEncrypt>}.
 *
 * <p>The returned primitive works with a keyset (rather than a single key). To encrypt a plaintext,
 * it uses the primary key in the keyset, and prepends to the ciphertext a certain prefix associated
 * with the primary key.
 */
public class HybridEncryptWrapper implements PrimitiveWrapper<HybridEncrypt, HybridEncrypt> {
  private static class WrappedHybridEncrypt implements HybridEncrypt {
    final PrimitiveSet<HybridEncrypt> primitives;

    public WrappedHybridEncrypt(final PrimitiveSet<HybridEncrypt> primitives) {
      this.primitives = primitives;
    }

    @Override
    public byte[] encrypt(final byte[] plaintext, final byte[] contextInfo)
        throws GeneralSecurityException {
      if (primitives.getPrimary() == null) {
        throw new GeneralSecurityException("keyset without primary key");
      }
      return Bytes.concat(
          primitives.getPrimary().getIdentifier(),
          primitives.getPrimary().getPrimitive().encrypt(plaintext, contextInfo));
    }
  }

  HybridEncryptWrapper() {}

  @Override
  public HybridEncrypt wrap(final PrimitiveSet<HybridEncrypt> primitives) {
    return new WrappedHybridEncrypt(primitives);
  }

  @Override
  public Class<HybridEncrypt> getPrimitiveClass() {
    return HybridEncrypt.class;
  }

  @Override
  public Class<HybridEncrypt> getInputPrimitiveClass() {
    return HybridEncrypt.class;
  }

  /**
   * Register the wrapper within the registry.
   *
   * <p>This is required for calls to {@link Keyset.getPrimitive} with a {@link HybridEncrypt}
   * argument.
   */
  public static void register() throws GeneralSecurityException {
    Registry.registerPrimitiveWrapper(new HybridEncryptWrapper());
  }
}
