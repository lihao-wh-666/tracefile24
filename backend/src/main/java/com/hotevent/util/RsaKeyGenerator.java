package com.hotevent.util;

import cn.hutool.core.codec.Base64;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

public class RsaKeyGenerator {
    public static void main(String[] args) throws Exception {
        Map<String, String> keys = generateKeyPair();
        String privateKey = keys.get("privateKey");
        String publicKey = keys.get("publicKey");
        System.out.println("=== RSA PRIVATE KEY ===");
        System.out.println(privateKey);
        System.out.println("=== RSA PUBLIC KEY ===");
        System.out.println(publicKey);
    }

    private static Map<String, String> generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            PublicKey publicKey = keyPair.getPublic();
            PrivateKey privateKey = keyPair.getPrivate();

            Map<String, String> keys = new HashMap<>();
            keys.put("publicKey", Base64.encode(publicKey.getEncoded()));
            keys.put("privateKey", Base64.encode(privateKey.getEncoded()));
            return keys;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("生成RSA密钥对失败", e);
        }
    }
}
