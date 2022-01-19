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

import com.google.crypto.tink.KeyTemplate;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.KeysetManager;
import com.google.crypto.tink.internal.KeyTemplateProtoConverter;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for JwtMacWrapper. */
@RunWith(JUnit4.class)
public class JwtMacWrapperTest {

  @Before
  public void setUp() throws GeneralSecurityException {
    JwtMacConfig.register();
  }

  @Test
  public void test_wrapNoPrimary_throws() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS256");
    KeysetManager manager = KeysetManager.withEmptyKeyset().add(template);
    KeysetHandle handle = manager.getKeysetHandle();
    assertThrows(GeneralSecurityException.class, () -> handle.getPrimitive(JwtMac.class));
  }

  @Test
  public void test_wrapLegacy_throws() throws Exception {
    KeyTemplate rawTemplate = KeyTemplates.get("JWT_HS256_RAW");
    // Convert the normal, raw template into a template with output prefix type LEGACY
    KeyTemplate tinkTemplate =
        KeyTemplate.create(
            rawTemplate.getTypeUrl(), rawTemplate.getValue(), KeyTemplate.OutputPrefixType.LEGACY);
    KeysetHandle handle = KeysetHandle.generateNew(tinkTemplate);
    assertThrows(GeneralSecurityException.class, () -> handle.getPrimitive(JwtMac.class));
  }

  @Test
  public void test_wrapSingleRawKey_works() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS256_RAW");
    KeysetHandle handle = KeysetHandle.generateNew(template);

    JwtMac jwtMac = handle.getPrimitive(JwtMac.class);
    RawJwt rawToken = RawJwt.newBuilder().setJwtId("id123").withoutExpiration().build();
    String signedCompact = jwtMac.computeMacAndEncode(rawToken);
    JwtValidator validator = JwtValidator.newBuilder().allowMissingExpiration().build();
    VerifiedJwt verifiedToken = jwtMac.verifyMacAndDecode(signedCompact, validator);
    assertThat(verifiedToken.getJwtId()).isEqualTo("id123");
  }

  @Test
  public void test_wrapSingleTinkKey_works() throws Exception {
    KeyTemplate tinkTemplate = KeyTemplates.get("JWT_HS256");
    KeysetHandle handle = KeysetHandle.generateNew(tinkTemplate);
    JwtMac jwtMac = handle.getPrimitive(JwtMac.class);
    RawJwt rawJwt = RawJwt.newBuilder().setJwtId("id123").withoutExpiration().build();
    String signedCompact = jwtMac.computeMacAndEncode(rawJwt);
    JwtValidator validator = JwtValidator.newBuilder().allowMissingExpiration().build();
    VerifiedJwt verifiedToken = jwtMac.verifyMacAndDecode(signedCompact, validator);
    assertThat(verifiedToken.getJwtId()).isEqualTo("id123");
  }

  @Test
  public void test_wrapMultipleKeys() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS256");

    KeysetManager manager = KeysetManager.withEmptyKeyset();
    manager.addNewKey(KeyTemplateProtoConverter.toProto(template), /*asPrimary=*/ true);
    KeysetHandle oldHandle = manager.getKeysetHandle();

    manager.addNewKey(KeyTemplateProtoConverter.toProto(template), /*asPrimary=*/ true);

    KeysetHandle newHandle = manager.getKeysetHandle();

    JwtMac oldJwtMac = oldHandle.getPrimitive(JwtMac.class);
    JwtMac newJwtMac = newHandle.getPrimitive(JwtMac.class);

    RawJwt rawToken = RawJwt.newBuilder().setJwtId("jwtId").withoutExpiration().build();
    String oldSignedCompact = oldJwtMac.computeMacAndEncode(rawToken);
    String newSignedCompact = newJwtMac.computeMacAndEncode(rawToken);

    JwtValidator validator = JwtValidator.newBuilder().allowMissingExpiration().build();
    assertThat(oldJwtMac.verifyMacAndDecode(oldSignedCompact, validator).getJwtId())
        .isEqualTo("jwtId");
    assertThat(newJwtMac.verifyMacAndDecode(oldSignedCompact, validator).getJwtId())
        .isEqualTo("jwtId");
    assertThat(newJwtMac.verifyMacAndDecode(newSignedCompact, validator).getJwtId())
        .isEqualTo("jwtId");
    assertThrows(
        GeneralSecurityException.class,
        () -> oldJwtMac.verifyMacAndDecode(newSignedCompact, validator));
  }

  @Test
  public void test_wrapMultipleTinkKeys() throws Exception {
    KeyTemplate tinkTemplate = KeyTemplates.get("JWT_HS256");

    KeysetManager manager = KeysetManager.withEmptyKeyset();
    manager.addNewKey(KeyTemplateProtoConverter.toProto(tinkTemplate), /*asPrimary=*/ true);
    KeysetHandle oldHandle = manager.getKeysetHandle();

    manager.addNewKey(KeyTemplateProtoConverter.toProto(tinkTemplate), /*asPrimary=*/ true);

    KeysetHandle newHandle = manager.getKeysetHandle();

    JwtMac oldJwtMac = oldHandle.getPrimitive(JwtMac.class);
    JwtMac newJwtMac = newHandle.getPrimitive(JwtMac.class);

    RawJwt rawToken = RawJwt.newBuilder().setJwtId("jwtId").withoutExpiration().build();
    String oldSignedCompact = oldJwtMac.computeMacAndEncode(rawToken);
    String newSignedCompact = newJwtMac.computeMacAndEncode(rawToken);

    JwtValidator validator = JwtValidator.newBuilder().allowMissingExpiration().build();
    assertThat(oldJwtMac.verifyMacAndDecode(oldSignedCompact, validator).getJwtId())
        .isEqualTo("jwtId");
    assertThat(newJwtMac.verifyMacAndDecode(oldSignedCompact, validator).getJwtId())
        .isEqualTo("jwtId");
    assertThat(newJwtMac.verifyMacAndDecode(newSignedCompact, validator).getJwtId())
        .isEqualTo("jwtId");
    assertThrows(
        GeneralSecurityException.class,
        () -> oldJwtMac.verifyMacAndDecode(newSignedCompact, validator));
  }

  @Test
  public void wrongKey_throwsInvalidSignatureException() throws Exception {
    KeysetHandle keysetHandle = KeysetHandle.generateNew(KeyTemplates.get("JWT_HS256"));
    JwtMac jwtMac = keysetHandle.getPrimitive(JwtMac.class);
    RawJwt rawJwt = RawJwt.newBuilder().withoutExpiration().build();
    String compact = jwtMac.computeMacAndEncode(rawJwt);
    JwtValidator validator = JwtValidator.newBuilder().allowMissingExpiration().build();

    KeysetHandle wrongKeysetHandle = KeysetHandle.generateNew(KeyTemplates.get("JWT_HS256"));
    JwtMac wrongJwtMac = wrongKeysetHandle.getPrimitive(JwtMac.class);
    assertThrows(
        GeneralSecurityException.class, () -> wrongJwtMac.verifyMacAndDecode(compact, validator));
  }

  @Test
  public void wrongIssuer_throwsInvalidException() throws Exception {
    KeysetHandle keysetHandle = KeysetHandle.generateNew(KeyTemplates.get("JWT_HS256"));
    JwtMac jwtMac = keysetHandle.getPrimitive(JwtMac.class);
    RawJwt rawJwt = RawJwt.newBuilder().setIssuer("Justus").withoutExpiration().build();
    String compact = jwtMac.computeMacAndEncode(rawJwt);
    JwtValidator validator =
        JwtValidator.newBuilder().allowMissingExpiration().expectIssuer("Peter").build();
    assertThrows(JwtInvalidException.class, () -> jwtMac.verifyMacAndDecode(compact, validator));
  }

  @Test
  public void expiredCompact_throwsExpiredException() throws Exception {
    KeysetHandle keysetHandle = KeysetHandle.generateNew(KeyTemplates.get("JWT_HS256"));
    JwtMac jwtMac = keysetHandle.getPrimitive(JwtMac.class);
    Instant now = Clock.systemUTC().instant().truncatedTo(ChronoUnit.SECONDS);
    RawJwt rawJwt =
        RawJwt.newBuilder()
            .setExpiration(now.minusSeconds(100)) // exipired 100 seconds ago
            .setIssuedAt(now.minusSeconds(200))
            .build();
    String compact = jwtMac.computeMacAndEncode(rawJwt);
    JwtValidator validator = JwtValidator.newBuilder().build();
    assertThrows(JwtInvalidException.class, () -> jwtMac.verifyMacAndDecode(compact, validator));
  }

  @Test
  public void notYetValidCompact_throwsNotBeforeException() throws Exception {
    KeysetHandle keysetHandle = KeysetHandle.generateNew(KeyTemplates.get("JWT_HS256"));
    JwtMac jwtMac = keysetHandle.getPrimitive(JwtMac.class);

    Instant now = Clock.systemUTC().instant().truncatedTo(ChronoUnit.SECONDS);
    RawJwt rawJwt =
        RawJwt.newBuilder()
            .setNotBefore(now.plusSeconds(3600)) // is valid in 1 hour, but not before
            .setIssuedAt(now)
            .withoutExpiration()
            .build();
    String compact = jwtMac.computeMacAndEncode(rawJwt);
    JwtValidator validator = JwtValidator.newBuilder().allowMissingExpiration().build();
    assertThrows(JwtInvalidException.class, () -> jwtMac.verifyMacAndDecode(compact, validator));
  }
}
