package com.hotevent.util;

import cn.hutool.crypto.asymmetric.KeyType;
import cn.hutool.crypto.asymmetric.RSA;
import com.hotevent.config.RsaProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RsaUtil {

    @Autowired
    private RsaProperties rsaProperties;

    private RSA rsa;

    private RSA getRsa() {
        if (rsa == null) {
            rsa = new RSA(rsaProperties.getPrivateKey(), rsaProperties.getPublicKey());
        }
        return rsa;
    }

    public String decrypt(String encryptedData) {
        try {
            return getRsa().decryptStr(encryptedData, KeyType.PrivateKey);
        } catch (Exception e) {
            log.error("RSA解密失败", e);
            throw new RuntimeException("密码解密失败，请刷新页面重试");
        }
    }

    public String decryptIfNeeded(String data) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        try {
            return decrypt(data);
        } catch (Exception e) {
            log.warn("RSA解密失败，可能是明文，直接返回原文: {}", e.getMessage());
            return data;
        }
    }

    public String getPublicKey() {
        return rsaProperties.getPublicKey();
    }
}
