package com.ecolchain.api.identity.adapter.in.web;

import com.ecolchain.api.contract.api.WellKnownApi;
import com.ecolchain.api.contract.model.JwksResponse;
import com.ecolchain.api.contract.model.JwksResponseKeysInner;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

/** JWKS público (chave de verificação dos JWTs). docs/features/auth-sessao.md */
public class JwksController implements WellKnownApi {

    @ConfigProperty(name = "mp.jwt.verify.publickey.location")
    String publicKeyLocation;
    @ConfigProperty(name = "smallrye.jwt.new-token.key-id", defaultValue = "ecolchain-dev-1")
    String kid;

    @Override
    public Response getJwks() {
        try {
            String pem = Files.readString(Path.of(publicKeyLocation))
                    .replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
            var key = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));
            var jwk = new JwksResponseKeysInner();
            jwk.setKty("RSA");
            jwk.setUse("sig");
            jwk.setAlg("RS256");
            jwk.setKid(kid);
            jwk.setN(b64u(key.getModulus()));
            jwk.setE(b64u(key.getPublicExponent()));
            var body = new JwksResponse();
            body.setKeys(List.of(jwk));
            return Response.ok(body).build();
        } catch (Exception e) {
            return Response.status(500).build();
        }
    }

    private static String b64u(BigInteger v) {
        byte[] b = v.toByteArray();
        if (b.length > 1 && b[0] == 0) {
            b = java.util.Arrays.copyOfRange(b, 1, b.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
