package com.msg.ppap.identity.adapter.out.signing;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECField;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Pattern;

/** PEM (PKCS#8) parsing of EC P-256 private keys and derivation of the public key. */
final class EcKeys {

    /** Order of the P-256 (secp256r1) base point. */
    static final BigInteger P256_ORDER =
            new BigInteger("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16);

    private static final Pattern PEM =
            Pattern.compile("-----BEGIN PRIVATE KEY-----([A-Za-z0-9+/=\\s]+)-----END PRIVATE KEY-----", Pattern.DOTALL);

    private EcKeys() {}

    /**
     * Parses a PEM encoded PKCS#8 EC P-256 private key.
     *
     * @throws IllegalArgumentException if the text is not such a key
     */
    static ECPrivateKey parsePkcs8Pem(String pem) {
        var matcher = PEM.matcher(pem == null ? "" : pem);
        if (!matcher.find()) {
            throw new IllegalArgumentException("expected a PEM block 'BEGIN PRIVATE KEY' (PKCS#8)");
        }
        byte[] der = Base64.getMimeDecoder().decode(matcher.group(1).replaceAll("\\s", ""));
        try {
            PrivateKey key = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
            ECPrivateKey ec = (ECPrivateKey) key;
            checkP256(ec.getParams());
            return ec;
        } catch (GeneralSecurityException | ClassCastException ex) {
            throw new IllegalArgumentException("not an EC private key in PKCS#8 format: " + ex.getMessage(), ex);
        }
    }

    /** Derives the public key {@code Q = d·G} of a private key. */
    static ECPublicKey publicKeyOf(ECPrivateKey privateKey) {
        ECParameterSpec params = privateKey.getParams();
        checkP256(params);
        BigInteger p = ((ECFieldFp) params.getCurve().getField()).getP();
        BigInteger a = params.getCurve().getA();
        ECPoint q = multiply(params.getGenerator(), privateKey.getS(), p, a);
        try {
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(q, params));
        } catch (GeneralSecurityException ex) {
            throw new IllegalArgumentException("cannot derive public key: " + ex.getMessage(), ex);
        }
    }

    static void checkP256(ECParameterSpec params) {
        ECField field = params.getCurve().getField();
        if (!(field instanceof ECFieldFp) || field.getFieldSize() != 256 || !P256_ORDER.equals(params.getOrder())) {
            throw new IllegalArgumentException("key is not on curve P-256");
        }
    }

    // Affine double-and-add; runs once on startup with the own key, no attacker-controlled input.
    private static ECPoint multiply(ECPoint point, BigInteger scalar, BigInteger p, BigInteger a) {
        ECPoint result = ECPoint.POINT_INFINITY;
        ECPoint addend = point;
        for (int i = 0; i < scalar.bitLength(); i++) {
            if (scalar.testBit(i)) {
                result = add(result, addend, p, a);
            }
            addend = add(addend, addend, p, a);
        }
        return result;
    }

    private static ECPoint add(ECPoint first, ECPoint second, BigInteger p, BigInteger a) {
        if (first.equals(ECPoint.POINT_INFINITY)) {
            return second;
        }
        if (second.equals(ECPoint.POINT_INFINITY)) {
            return first;
        }
        BigInteger x1 = first.getAffineX();
        BigInteger y1 = first.getAffineY();
        BigInteger x2 = second.getAffineX();
        BigInteger y2 = second.getAffineY();
        BigInteger lambda;
        if (x1.equals(x2)) {
            if (y1.add(y2).mod(p).signum() == 0) {
                return ECPoint.POINT_INFINITY;
            }
            BigInteger numerator = x1.pow(2).multiply(BigInteger.valueOf(3)).add(a);
            lambda = numerator.multiply(y1.shiftLeft(1).modInverse(p)).mod(p);
        } else {
            lambda = y2.subtract(y1).multiply(x2.subtract(x1).modInverse(p)).mod(p);
        }
        BigInteger x3 = lambda.pow(2).subtract(x1).subtract(x2).mod(p);
        BigInteger y3 = lambda.multiply(x1.subtract(x3)).subtract(y1).mod(p);
        return new ECPoint(x3, y3);
    }
}
