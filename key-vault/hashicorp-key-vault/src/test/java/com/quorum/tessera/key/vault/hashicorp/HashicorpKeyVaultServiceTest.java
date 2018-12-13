package com.quorum.tessera.key.vault.hashicorp;

import com.quorum.tessera.config.vault.data.GetSecretData;
import com.quorum.tessera.config.vault.data.HashicorpGetSecretData;
import com.quorum.tessera.config.vault.data.HashicorpSetSecretData;
import com.quorum.tessera.config.vault.data.SetSecretData;
import com.quorum.tessera.key.vault.KeyVaultException;
import org.junit.Before;
import org.junit.Test;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.Versioned;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HashicorpKeyVaultServiceTest {

    private HashicorpKeyVaultService keyVaultService;

    private VaultOperations vaultOperations;

    private HashicorpKeyVaultServiceDelegate delegate;

    @Before
    public void setUp() {
        this.vaultOperations = mock(VaultOperations.class);
        this.delegate = mock(HashicorpKeyVaultServiceDelegate.class);
        this.keyVaultService = new HashicorpKeyVaultService(vaultOperations, delegate);
    }

    @Test
    public void getSecret() {
        HashicorpGetSecretData getSecretData = mock(HashicorpGetSecretData.class);

        when(getSecretData.getSecretEngineName()).thenReturn("secretEngine");
        when(getSecretData.getSecretName()).thenReturn("secretName");
        when(getSecretData.getValueId()).thenReturn("keyId");

        Versioned versionedResponse = mock(Versioned.class);

        when(delegate.get(any(VaultOperations.class), any(HashicorpGetSecretData.class))).thenReturn(versionedResponse);

        when(versionedResponse.hasData()).thenReturn(true);

        Map responseData = mock(Map.class);
        when(versionedResponse.getData()).thenReturn(responseData);
        when(responseData.containsKey("keyId")).thenReturn(true);
        String keyValue = "keyvalue";
        when(responseData.get("keyId")).thenReturn(keyValue);

        String result = keyVaultService.getSecret(getSecretData);

        assertThat(result).isEqualTo(keyValue);
    }

    @Test
    public void getSecretThrowsExceptionIfProvidedDataIsNotCorrectType() {
        GetSecretData getSecretData = mock(GetSecretData.class);
        when(getSecretData.getType()).thenReturn(null);

        Throwable ex = catchThrowable(() -> keyVaultService.getSecret(getSecretData));

        assertThat(ex).isExactlyInstanceOf(KeyVaultException.class);
        assertThat(ex).hasMessage("Incorrect data type passed to HashicorpKeyVaultService.  Type was null");
    }

    @Test
    public void getSecretThrowsExceptionIfNullRetrievedFromVault() {
        HashicorpGetSecretData getSecretData = new HashicorpGetSecretData("engine", "secretName", "id");

        when(delegate.get(vaultOperations, getSecretData)).thenReturn(null);

        Throwable ex = catchThrowable(() -> keyVaultService.getSecret(getSecretData));

        assertThat(ex).isExactlyInstanceOf(HashicorpVaultException.class);
        assertThat(ex).hasMessage("No data found at engine/secretName");
    }

    @Test
    public void getSecretThrowsExceptionIfNoDataRetrievedFromVault() {
        HashicorpGetSecretData getSecretData = new HashicorpGetSecretData("engine", "secretName", "id");

        Versioned versionedResponse = mock(Versioned.class);
        when(versionedResponse.hasData()).thenReturn(false);

        when(delegate.get(vaultOperations, getSecretData)).thenReturn(versionedResponse);

        Throwable ex = catchThrowable(() -> keyVaultService.getSecret(getSecretData));

        assertThat(ex).isExactlyInstanceOf(HashicorpVaultException.class);
        assertThat(ex).hasMessage("No data found at engine/secretName");
    }


    @Test
    public void getSecretThrowsExceptionIfValueNotFoundForGivenId() {
        HashicorpGetSecretData getSecretData = new HashicorpGetSecretData("engine", "secretName", "id");

        Versioned versionedResponse = mock(Versioned.class);
        when(versionedResponse.hasData()).thenReturn(true);

        Map responseData = mock(Map.class);
        when(versionedResponse.getData()).thenReturn(responseData);
        when(responseData.containsKey("id")).thenReturn(false);

        when(delegate.get(vaultOperations, getSecretData)).thenReturn(versionedResponse);

        Throwable ex = catchThrowable(() -> keyVaultService.getSecret(getSecretData));

        assertThat(ex).isExactlyInstanceOf(HashicorpVaultException.class);
        assertThat(ex).hasMessage("No value with id id found at engine/secretName");
    }


    @Test
    public void setSecretThrowsExceptionIfProvidedDataIsNotCorrectType() {
        SetSecretData setSecretData = mock(SetSecretData.class);
        when(setSecretData.getType()).thenReturn(null);

        Throwable ex = catchThrowable(() -> keyVaultService.setSecret(setSecretData));

        assertThat(ex).isExactlyInstanceOf(KeyVaultException.class);
        assertThat(ex).hasMessage("Incorrect data type passed to HashicorpKeyVaultService.  Type was null");
    }


    @Test
    public void setSecretReturnsMetadataObject() {
        HashicorpSetSecretData setSecretData = new HashicorpSetSecretData("engine", "name", Collections.emptyMap());

        Versioned.Metadata metadata = mock(Versioned.Metadata.class);
        when(delegate.set(vaultOperations, setSecretData)).thenReturn(metadata);

        Object result = keyVaultService.setSecret(setSecretData);

        assertThat(result).isInstanceOf(Versioned.Metadata.class);
        assertThat(result).isEqualTo(metadata);
    }

}
