package com.github.seanroy.utils;

import com.amazonaws.encryptionsdk.AwsCrypto;
import com.amazonaws.encryptionsdk.CryptoResult;
import com.amazonaws.encryptionsdk.kms.KmsMasterKey;
import com.amazonaws.encryptionsdk.kms.KmsMasterKeyProvider;

/**
 * A simple encryption module that allows for the encryption/decryption of strings using AWS KMS 
 * encryption keys. This code is mostly taken from amazon example code.
 * @author sean
 *
 */
public class AWSEncryption {
    private String keyArn;
    
    public AWSEncryption(String keyArn) {
        this.keyArn = keyArn;
    }
    
    public String encryptString(String data) {
        // Instantiate the SDK
        final AwsCrypto crypto = new AwsCrypto();

        // Set up the KmsMasterKeyProvider backed by the default credentials        
        final KmsMasterKeyProvider prov = new KmsMasterKeyProvider(keyArn);

        return crypto.encryptString(prov, data).getResult();
    }
    
    public String decryptString(String cipherText) {
        // Instantiate the SDK
        final AwsCrypto crypto = new AwsCrypto();
        
        // Set up the KmsMasterKeyProvider backed by the default credentials        
        final KmsMasterKeyProvider prov = new KmsMasterKeyProvider(keyArn);
        
        // Decrypt the data
        final CryptoResult<String, KmsMasterKey> decryptResult = crypto.decryptString(prov, cipherText);
        
        // Before returning the plaintext, verify that the customer master key that
        // was used in the encryption operation was the one supplied to the master key provider.  
        if (!decryptResult.getMasterKeyIds().get(0).equals(keyArn)) {
            throw new IllegalStateException("Wrong encryption key ARN!");
        }

        return decryptResult.getResult();
    }
}
