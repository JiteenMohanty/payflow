package com.payflow.core.common.crypto;

public interface SymmetricEncryptor {

    byte[] encrypt(byte[] plaintext);

    byte[] decrypt(byte[] payload);
}
