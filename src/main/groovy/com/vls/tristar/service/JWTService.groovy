package com.vls.tristar.service;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT
import groovy.util.logging.Slf4j
import org.bouncycastle.jcajce.provider.asymmetric.RSA
import org.bouncycastle.jce.provider.BouncyCastleProvider

import java.security.KeyFactory;
import java.security.PublicKey
import java.security.Security;
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec;
import java.util.Map;

@Slf4j
class JWTService {

    PublicKey publicKey

    JWTService(String publicKey) {
        Security.addProvider(new BouncyCastleProvider());
        byte[] keyBytesPublic = Base64.getMimeDecoder().decode(publicKey.getBytes());
        this.publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytesPublic));
    }

    Map decrypt(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new RSASSAVerifier((RSAPublicKey) publicKey);
            boolean success = signedJWT.verify(verifier)
            return success ? signedJWT.getJWTClaimsSet().getAllClaims() : null;
        } catch (Exception e) {
            log.error(e.getMessage())
            return null;
        }
    }
}
