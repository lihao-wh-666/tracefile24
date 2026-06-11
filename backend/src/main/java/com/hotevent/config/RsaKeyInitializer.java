package com.hotevent.config;

import cn.hutool.core.codec.Base64;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class RsaKeyInitializer {

    private static final String KEYS_FILE_PATH = "./config/rsa-keys.json";

    @Autowired
    private RsaProperties rsaProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() throws IOException {
        String existingPrivateKey = rsaProperties.getPrivateKey();
        String existingPublicKey = rsaProperties.getPublicKey();

        if (existingPrivateKey != null && !existingPrivateKey.isEmpty()
                && existingPublicKey != null && !existingPublicKey.isEmpty()) {
            log.info("RSA密钥已在配置文件中配置，跳过自动生成");
            return;
        }

        Map<String, String> keysFromFile = loadKeysFromFile();
        if (keysFromFile != null) {
            rsaProperties.setPrivateKey(keysFromFile.get("privateKey"));
            rsaProperties.setPublicKey(keysFromFile.get("publicKey"));
            log.info("RSA密钥已从本地文件加载: {}", new File(KEYS_FILE_PATH).getAbsolutePath());
            return;
        }

        log.info("未检测到RSA密钥配置，开始生成新的密钥对...");
        Map<String, String> keys = generateKeyPair();
        String privateKey = keys.get("privateKey");
        String publicKey = keys.get("publicKey");

        rsaProperties.setPrivateKey(privateKey);
        rsaProperties.setPublicKey(publicKey);

        saveKeysToFile(privateKey, publicKey);
        log.info("RSA密钥对生成并保存完成，私钥文件路径: {}", new File(KEYS_FILE_PATH).getAbsolutePath());
    }

    private Map<String, String> generateKeyPair() {
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

    private Map<String, String> loadKeysFromFile() {
        try {
            Path path = Paths.get(KEYS_FILE_PATH);
            if (!Files.exists(path)) {
                return null;
            }
            String content = Files.readString(path);
            Map<String, String> keys = objectMapper.readValue(content, Map.class);
            if (keys.get("privateKey") != null && keys.get("publicKey") != null) {
                return keys;
            }
            return null;
        } catch (Exception e) {
            log.warn("从本地文件加载RSA密钥失败: {}", e.getMessage());
            return null;
        }
    }

    private void saveKeysToFile(String privateKey, String publicKey) throws IOException {
        Map<String, String> keys = new HashMap<>();
        keys.put("privateKey", privateKey);
        keys.put("publicKey", publicKey);

        Path path = Paths.get(KEYS_FILE_PATH);
        Path parentDir = path.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(keys);
        Files.writeString(path, json);
    }
}
