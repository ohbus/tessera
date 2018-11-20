package com.quorum.tessera.key.vault;

import com.quorum.tessera.config.Config;
import com.quorum.tessera.config.keypairs.KeyPairType;
import com.quorum.tessera.config.util.EnvironmentVariableProvider;

import java.util.*;

public interface KeyVaultServiceFactory {

    KeyVaultService create(Config config, EnvironmentVariableProvider envProvider);

    KeyPairType getType();

    static KeyVaultServiceFactory getInstance(KeyPairType keyPairType) {
        List<KeyVaultServiceFactory> providers = new ArrayList<>();
        ServiceLoader.load(KeyVaultServiceFactory.class).forEach(providers::add);

        return providers.stream()
            .filter(factory -> factory.getType() == keyPairType)
            .findFirst()
            .orElseThrow(() -> new NoKeyVaultServiceFactoryException(keyPairType + " implementation of KeyVaultServiceFactory was not found on the classpath"));
    }

}
