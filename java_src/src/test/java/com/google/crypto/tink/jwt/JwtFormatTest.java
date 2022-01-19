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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.crypto.tink.proto.OutputPrefixType;
import com.google.crypto.tink.subtle.Base64;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.security.InvalidAlgorithmParameterException;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for JwtFormat */
@RunWith(JUnit4.class)
public final class JwtFormatTest {

  @Test
  public void createDecodeHeader_success() throws Exception {
    String header =
        JwtFormat.decodeHeader(JwtFormat.createHeader("RS256", Optional.empty(), Optional.empty()));
    assertThat(header).isEqualTo("{\"alg\":\"RS256\"}");
  }

  @Test
  public void createDecodeHeaderWithTyp_success() throws Exception {
    String header =
        JwtFormat.decodeHeader(
            JwtFormat.createHeader("RS256", Optional.of("JWT"), Optional.empty()));
    assertThat(header).isEqualTo("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
  }

  @Test
  public void createDecodeHeaderWithKidAndTyp_success() throws Exception {
    String header =
        JwtFormat.decodeHeader(
            JwtFormat.createHeader("RS256", Optional.of("JWT"), Optional.of("GsapRA")));
    assertThat(header).isEqualTo("{\"kid\":\"GsapRA\",\"alg\":\"RS256\",\"typ\":\"JWT\"}");
  }

  @Test
  public void createDecodeHeaderWithInvalidUtf8_fails() throws Exception {
    assertThrows(
        JwtInvalidException.class,
        () -> JwtFormat.decodeHeader("eyJhbGciOiJIUzI1NiIsICJhIjoiwiJ9"));
  }

  @Test
  public void getKidFromTinkOutputPrefixType_success() throws Exception {
    int keyId = 0x1ac6a944;
    Optional<String> kid = JwtFormat.getKid(keyId, OutputPrefixType.TINK);
    assertThat(kid.get()).isEqualTo("GsapRA");
    assertThat(JwtFormat.getKeyId(kid.get()).get()).isEqualTo(0x1ac6a944);
  }

  @Test
  public void getKidFromRawOutputPrefixType_success() throws Exception {
    int keyId = 0x1ac6a944;
    Optional<String> kid = JwtFormat.getKid(keyId, OutputPrefixType.RAW);
    assertThat(kid.isPresent()).isFalse();
  }

  @Test
  public void keyIdKidConversion_outputIsEqual() throws Exception {
    assertThat(JwtFormat.getKeyId(JwtFormat.getKid(0x12345678, OutputPrefixType.TINK).get()).get())
        .isEqualTo(0x12345678);
    assertThat(JwtFormat.getKeyId(JwtFormat.getKid(-2147483648, OutputPrefixType.TINK).get()).get())
        .isEqualTo(-2147483648);
    assertThat(JwtFormat.getKeyId(JwtFormat.getKid(-100, OutputPrefixType.TINK).get()).get())
        .isEqualTo(-100);
    assertThat(JwtFormat.getKeyId(JwtFormat.getKid(0, OutputPrefixType.TINK).get()).get())
        .isEqualTo(0);
    assertThat(JwtFormat.getKeyId(JwtFormat.getKid(100, OutputPrefixType.TINK).get()).get())
        .isEqualTo(100);
    assertThat(JwtFormat.getKeyId(JwtFormat.getKid(2147483647, OutputPrefixType.TINK).get()).get())
        .isEqualTo(2147483647);
  }

  @Test
  public void getKeyId_wrongFormat_isNotPresent() throws Exception {
    assertThat(JwtFormat.getKeyId("GsapRAA").isPresent()).isFalse();
    assertThat(JwtFormat.getKeyId("Gsap").isPresent()).isFalse();
    assertThat(JwtFormat.getKeyId("").isPresent()).isFalse();
    assertThat(JwtFormat.getKeyId("dBjftJeZ4CVP-mB92K27uhbUJU1p1r").isPresent()).isFalse();
  }

  @Test
  public void getKidFromUnsupportedOutputPrefixType_fails() throws Exception {
    int keyId = 0x1ac6a944;
    assertThrows(JwtInvalidException.class, () -> JwtFormat.getKid(keyId, OutputPrefixType.LEGACY));
  }

  @Test
  public void createHeaderWithUnknownAlgorithm_fails() throws Exception {
    assertThrows(
        InvalidAlgorithmParameterException.class,
        () -> JwtFormat.createHeader("UnknownAlgorithm", Optional.empty(), Optional.empty()));
  }

  @Test
  public void decodeHeaderA1_success() throws Exception {
    // Example from https://tools.ietf.org/html/rfc7515#appendix-A.1
    String header = JwtFormat.decodeHeader("eyJ0eXAiOiJKV1QiLA0KICJhbGciOiJIUzI1NiJ9");
    assertThat(header).isEqualTo("{\"typ\":\"JWT\",\r\n \"alg\":\"HS256\"}");
  }

  @Test
  public void decodeHeaderA2_success() throws Exception {
    // Example from https://tools.ietf.org/html/rfc7515#appendix-A.2
    String header = JwtFormat.decodeHeader("eyJhbGciOiJSUzI1NiJ9");
    assertThat(header).isEqualTo("{\"alg\":\"RS256\"}");
  }

  @Test
  public void decodeModifiedHeader_success() throws Exception {
    assertThrows(JwtInvalidException.class, () -> JwtFormat.decodeHeader("eyJhbGciOiJSUzI1NiJ9?"));
    assertThrows(JwtInvalidException.class, () -> JwtFormat.decodeHeader("eyJhbGciOiJ SUzI1NiJ9"));
    assertThrows(
        JwtInvalidException.class, () -> JwtFormat.decodeHeader("eyJhbGci\r\nOiJSUzI1NiJ9"));
  }

  @Test
  public void decodeHeader_success() throws Exception {
    String headerStr = Base64.urlSafeEncode("{\"alg\":\"RS256\"}".getBytes(UTF_8));
    String header = JwtFormat.decodeHeader(headerStr);
    assertThat(header).isEqualTo("{\"alg\":\"RS256\"}");
  }

  @Test
  public void decodeInvalidHeader_fails() throws Exception {
    assertThrows(JwtInvalidException.class, () -> JwtFormat.decodeHeader("?="));
  }

  @Test
  public void createDecodeValidateHeader_success() throws Exception {
    JwtFormat.validateHeader(
        "HS256",
        Optional.empty(), Optional.empty(),
        JsonUtil.parseJson(
            JwtFormat.decodeHeader(
                JwtFormat.createHeader("HS256", Optional.empty(), Optional.empty()))));
    JwtFormat.validateHeader(
        "HS384",
        Optional.empty(), Optional.empty(),
        JsonUtil.parseJson(
            JwtFormat.decodeHeader(
                JwtFormat.createHeader("HS384", Optional.empty(), Optional.empty()))));
    JwtFormat.validateHeader(
        "HS512",
        Optional.empty(), Optional.empty(),
        JsonUtil.parseJson(
            JwtFormat.decodeHeader(
                JwtFormat.createHeader("HS512", Optional.empty(), Optional.empty()))));
    JwtFormat.validateHeader(
        "ES256",
        Optional.empty(), Optional.empty(),
        JsonUtil.parseJson(
            JwtFormat.decodeHeader(
                JwtFormat.createHeader("ES256", Optional.empty(), Optional.empty()))));
    JwtFormat.validateHeader(
        "RS256",
        Optional.empty(), Optional.empty(),
        JsonUtil.parseJson(
            JwtFormat.decodeHeader(
                JwtFormat.createHeader("RS256", Optional.empty(), Optional.empty()))));
  }

  @Test
  public void validateHeaderWithWrongAlgorithm_fails() throws Exception {
    String header =
        JwtFormat.decodeHeader(JwtFormat.createHeader("HS256", Optional.empty(), Optional.empty()));
    assertThrows(
        InvalidAlgorithmParameterException.class,
        () ->
            JwtFormat.validateHeader(
                "HS384", Optional.empty(), Optional.empty(), JsonUtil.parseJson(header)));
  }

  @Test
  public void validateHeaderWithUnknownAlgorithm_fails() throws Exception {
    assertThrows(
        InvalidAlgorithmParameterException.class,
        () ->
            JwtFormat.validateHeader(
                "UnknownAlgorithm",
                Optional.empty(), Optional.empty(),
                JsonUtil.parseJson("{\"alg\": \"UnknownAlgorithm\"}")));
  }

  @Test
  public void validateHeaderIgnoresTyp() throws Exception {
    JwtFormat.validateHeader(
        "HS256",
        Optional.empty(), Optional.empty(),
        JsonUtil.parseJson("{\"alg\": \"HS256\", \"typ\": \"unknown\"}"));
  }

  @Test
  public void validateHeaderRejectsCrit() throws Exception {
    assertThrows(
        JwtInvalidException.class,
        () ->
            JwtFormat.validateHeader(
                "HS256",
                Optional.empty(), Optional.empty(),
                JsonUtil.parseJson(
                    "{\"alg\": \"HS256\", \"crit\":[\"http://example.invalid/UNDEFINED\"], "
                        + "\"http://example.invalid/UNDEFINED\":true}")));
  }

  @Test
  public void validateHeaderWithUnknownEntry_success() throws Exception {
    JwtFormat.validateHeader(
        "HS256",
        Optional.empty(), Optional.empty(),
        JsonUtil.parseJson("{\"alg\": \"HS256\", \"unknown\": \"header\"}"));
  }

  @Test
  public void validateEmptyHeader_fails() throws Exception {
    assertThrows(
        JwtInvalidException.class,
        () ->
            JwtFormat.validateHeader(
                "HS256", Optional.empty(), Optional.empty(), JsonUtil.parseJson("{}")));
  }

  @Test
  public void validateHeaderWithTinkKid() throws Exception {
    JwtFormat.validateHeader(
        "HS256",
        Optional.of("kid123"), Optional.empty(),
        JsonUtil.parseJson("{\"alg\": \"HS256\", \"kid\": \"kid123\"}"));
    assertThrows(
        JwtInvalidException.class,
        () ->
            JwtFormat.validateHeader(
                "HS256",
                Optional.of("kid123"),
                Optional.empty(),
                JsonUtil.parseJson("{\"alg\": \"HS256\", \"kid\": \"wrongKid\"}")));
    // If tinkKid is set, then the kid is required in the header.
    assertThrows(
        JwtInvalidException.class,
        () ->
            JwtFormat.validateHeader(
                "HS256",
                Optional.of("kid123"),
                Optional.empty(),
                JsonUtil.parseJson("{\"alg\": \"HS256\"}")));
  }

  @Test
  public void validateHeaderWithCustomKid() throws Exception {
    JwtFormat.validateHeader(
        "HS256",
        Optional.empty(), Optional.of("kid123"),
        JsonUtil.parseJson("{\"alg\": \"HS256\", \"kid\": \"kid123\"}"));
    assertThrows(
        JwtInvalidException.class,
        () ->
            JwtFormat.validateHeader(
                "HS256",
                Optional.empty(),
                Optional.of("kid123"),
                JsonUtil.parseJson("{\"alg\": \"HS256\", \"kid\": \"wrongKid\"}")));
    // If customKid is set, then the kid is not required in the header.
    JwtFormat.validateHeader(
        "HS256",
        Optional.empty(), Optional.of("kid123"),
        JsonUtil.parseJson("{\"alg\": \"HS256\"}"));
  }

  @Test
  public void validateHeaderWithBothTinkAndCustomKid_fails() throws Exception {
    assertThrows(
        JwtInvalidException.class,
        () ->
            JwtFormat.validateHeader(
                "HS256",
                Optional.of("kid123"),
                Optional.of("kid123"),
                JsonUtil.parseJson("{\"alg\": \"HS256\", \"kid\": \"kid123\"}")));
  }

  @Test
  public void encodeDecodePayload_equal() throws Exception {
    JsonObject payload = new JsonObject();
    payload.addProperty("iss", "joe");
    payload.addProperty("exp", 1300819380);
    payload.addProperty("http://example.com/is_root", true);
    String jsonPayload = payload.toString();
    String encodedPayload = JwtFormat.encodePayload(jsonPayload);
    String decodedPayload = JwtFormat.decodePayload(encodedPayload);
    assertThat(decodedPayload).isEqualTo(jsonPayload);
  }

  @Test
  public void decodePayload_success() throws Exception {
    // Example from https://tools.ietf.org/html/rfc7515#appendix-A.1
    JsonObject payload =
        JsonParser.parseString(
                JwtFormat.decodePayload(
                    "eyJpc3MiOiJqb2UiLA0KICJleHAiOjEzMDA4MTkzODAsDQogImh0dHA6Ly9leGFt"
                        + "cGxlLmNvbS9pc19yb290Ijp0cnVlfQ"))
            .getAsJsonObject();
    assertThat(payload.get("iss").getAsString()).isEqualTo("joe");
    assertThat(payload.get("exp").getAsInt()).isEqualTo(1300819380);
    assertThat(payload.get("http://example.com/is_root").getAsBoolean()).isTrue();
  }

  @Test
  public void decodeInvalidPayload_fails() throws Exception {
    assertThrows(JwtInvalidException.class, () -> JwtFormat.decodePayload("?="));
  }

  @Test
  public void createDecodePayloadWithInvalidUtf8_fails() throws Exception {
    assertThrows(JwtInvalidException.class, () -> JwtFormat.decodePayload("eyJpc3MiOiJqb2XCIn0"));
  }

  @Test
  public void signedCompactCreateSplit_success() throws Exception {
    RawJwt rawJwt = RawJwt.newBuilder().setIssuer("joe").withoutExpiration().build();
    String encodedSignature = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    byte[] signature = JwtFormat.decodeSignature(encodedSignature);
    String unsignedCompact = JwtFormat.createUnsignedCompact("RS256", Optional.empty(), rawJwt);
    String signedCompact = JwtFormat.createSignedCompact(unsignedCompact, signature);
    JwtFormat.Parts parts = JwtFormat.splitSignedCompact(signedCompact);

    JwtFormat.validateHeader(
        "RS256", Optional.empty(), Optional.empty(), JsonUtil.parseJson(parts.header));

    assertThat(unsignedCompact).isEqualTo("eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJqb2UifQ");
    assertThat(signedCompact).isEqualTo(
        "eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJqb2UifQ.dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk");
    assertThat(parts.unsignedCompact).isEqualTo(unsignedCompact);
    assertThat(parts.signatureOrMac).isEqualTo(signature);
    assertThat(parts.header).isEqualTo("{\"alg\":\"RS256\"}");
    assertThat(parts.payload).isEqualTo("{\"iss\":\"joe\"}");
  }


  @Test
  public void signedCompactCreateSplitWithTypeHeader_success() throws Exception {
    RawJwt rawJwt =
        RawJwt.newBuilder().setTypeHeader("JWT").setIssuer("joe").withoutExpiration().build();
    String encodedSignature = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    byte[] signature = JwtFormat.decodeSignature(encodedSignature);
    String unsignedCompact = JwtFormat.createUnsignedCompact("RS256", Optional.empty(), rawJwt);
    String signedCompact = JwtFormat.createSignedCompact(unsignedCompact, signature);
    JwtFormat.Parts parts = JwtFormat.splitSignedCompact(signedCompact);
    JsonObject parsedHeader = JsonUtil.parseJson(parts.header);
    JwtFormat.validateHeader("RS256", Optional.empty(), Optional.empty(), parsedHeader);
    assertThat(parsedHeader.getAsJsonPrimitive("typ").getAsString()).isEqualTo("JWT");

    assertThat(unsignedCompact)
        .isEqualTo("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJqb2UifQ");
    assertThat(parts.unsignedCompact).isEqualTo(unsignedCompact);
    assertThat(parts.signatureOrMac).isEqualTo(signature);
    assertThat(parts.header).isEqualTo("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
    assertThat(parts.payload).isEqualTo("{\"iss\":\"joe\"}");
  }

  @Test
  public void signedCompactCreateSplitWithKeyIdentifier_success() throws Exception {
    String kid = "AZxkm2U";
    RawJwt rawJwt = RawJwt.newBuilder().setIssuer("joe").withoutExpiration().build();
    String encodedSignature = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    byte[] signature = JwtFormat.decodeSignature(encodedSignature);
    String unsignedCompact = JwtFormat.createUnsignedCompact("RS256", Optional.of(kid), rawJwt);
    String signedCompact = JwtFormat.createSignedCompact(unsignedCompact, signature);
    JwtFormat.Parts parts = JwtFormat.splitSignedCompact(signedCompact);
    JsonObject parsedHeader = JsonUtil.parseJson(parts.header);
    JwtFormat.validateHeader("RS256", Optional.empty(), Optional.empty(), parsedHeader);

    assertThat(unsignedCompact)
        .isEqualTo("eyJraWQiOiJBWnhrbTJVIiwiYWxnIjoiUlMyNTYifQ.eyJpc3MiOiJqb2UifQ");
    assertThat(signedCompact)
        .isEqualTo(
            "eyJraWQiOiJBWnhrbTJVIiwiYWxnIjoiUlMyNTYifQ.eyJpc3MiOiJqb2UifQ"
                + ".dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk");
    assertThat(parts.unsignedCompact).isEqualTo(unsignedCompact);
    assertThat(parts.signatureOrMac).isEqualTo(signature);
    assertThat(parts.header).isEqualTo("{\"kid\":\"AZxkm2U\",\"alg\":\"RS256\"}");
    assertThat(parts.payload).isEqualTo("{\"iss\":\"joe\"}");
  }

  @Test
  public void splitEmptySignedCompact_success() throws Exception {
    JwtFormat.Parts parts = JwtFormat.splitSignedCompact("..");
    assertThat(parts.unsignedCompact).isEqualTo(".");
    assertThat(parts.signatureOrMac).isEmpty();
    assertThat(parts.header).isEmpty();
    assertThat(parts.payload).isEmpty();
  }

  @Test
  public void splitSignedCompactWithBadFormat_fails() throws Exception {
    assertThrows(
        JwtInvalidException.class,
        () -> JwtFormat.splitSignedCompact("e30.e30.YWJj.abc"));
    assertThrows(
        JwtInvalidException.class,
        () -> JwtFormat.splitSignedCompact("e30.e30.YWJj."));
    assertThrows(
        JwtInvalidException.class,
        () -> JwtFormat.splitSignedCompact(".e30.e30.YWJj"));
    assertThrows(
        JwtInvalidException.class,
        () -> JwtFormat.splitSignedCompact(".e30.e30."));
    assertThrows(
        JwtInvalidException.class,
        () -> JwtFormat.splitSignedCompact("e30.e30"));
    assertThrows(
        JwtInvalidException.class,
        () -> JwtFormat.splitSignedCompact("e30"));
    assertThrows(
        JwtInvalidException.class,
        () -> JwtFormat.splitSignedCompact(""));
  }

  @Test
  public void splitSignedCompactWithBadCharacters_fails() throws Exception {
    // check that unmodified token works
    JwtFormat.Parts parts = JwtFormat.splitSignedCompact("e30.e30.YWJj");
    assertThat(parts.unsignedCompact).isEqualTo("e30.e30");

    // add bad characters
    assertThrows(JwtInvalidException.class, () -> JwtFormat.splitSignedCompact("{e30.e30.YWJj"));
    assertThrows(JwtInvalidException.class, () -> JwtFormat.splitSignedCompact(" e30.e30.YWJj"));
    assertThrows(JwtInvalidException.class, () -> JwtFormat.splitSignedCompact("e30. e30.YWJj"));
    assertThrows(JwtInvalidException.class, () -> JwtFormat.splitSignedCompact("e30.e30.YWJj "));
    assertThrows(JwtInvalidException.class, () -> JwtFormat.splitSignedCompact("e30.e30.\nYWJj"));
    assertThrows(JwtInvalidException.class, () -> JwtFormat.splitSignedCompact("e30.\re30.YWJj"));
    assertThrows(JwtInvalidException.class, () -> JwtFormat.splitSignedCompact("e30$.e30.YWJj"));
    assertThrows(JwtInvalidException.class, () -> JwtFormat.splitSignedCompact("e30.$e30.YWJj"));
    assertThrows(JwtInvalidException.class, () -> JwtFormat.splitSignedCompact("e30.e30.YWJj$"));
    assertThrows(JwtInvalidException.class, () -> JwtFormat.splitSignedCompact("e30.e30.YWJj$"));
    assertThrows(
        JwtInvalidException.class, () -> JwtFormat.splitSignedCompact("e30.e30.YWJj\ud83c"));
  }

  @Test
  public void encodeDecodeSignature_success() throws Exception {
    // Example from https://tools.ietf.org/html/rfc7515#appendix-A.1
    byte[] signatureBytes =
        new byte[] {
          (byte) 116,
          (byte) 24,
          (byte) 223,
          (byte) 180,
          (byte) 151,
          (byte) 153,
          (byte) 224,
          (byte) 37,
          (byte) 79,
          (byte) 250,
          (byte) 96,
          (byte) 125,
          (byte) 216,
          (byte) 173,
          (byte) 187,
          (byte) 186,
          (byte) 22,
          (byte) 212,
          (byte) 37,
          (byte) 77,
          (byte) 105,
          (byte) 214,
          (byte) 191,
          (byte) 240,
          (byte) 91,
          (byte) 88,
          (byte) 5,
          (byte) 88,
          (byte) 83,
          (byte) 132,
          (byte) 141,
          (byte) 121
        };
    String encodeSignature = JwtFormat.encodeSignature(signatureBytes);
    assertThat(encodeSignature)
        .isEqualTo("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk");
    assertThat(JwtFormat.decodeSignature(encodeSignature))
        .isEqualTo(signatureBytes);
  }
}
