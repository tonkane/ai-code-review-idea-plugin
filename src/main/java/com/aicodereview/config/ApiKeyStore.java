package com.aicodereview.config;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.ide.passwordSafe.PasswordSafe;
import org.jetbrains.annotations.Nullable;

/**
 * API keys are stored in the IDE credential store, never in the plugin XML.
 */
public final class ApiKeyStore {

    private static final String SERVICE_NAME = "AiGitCodeReview";
    private static final String USER_NAME = "apiKey";

    private ApiKeyStore() {
    }

    private static CredentialAttributes attributes() {
        return new CredentialAttributes(SERVICE_NAME, USER_NAME);
    }

    public static @Nullable String get() {
        String password = PasswordSafe.getInstance().getPassword(attributes());
        return password == null || password.isBlank() ? null : password;
    }

    public static boolean hasKey() {
        return get() != null;
    }

    public static void set(@Nullable String apiKey) {
        PasswordSafe.getInstance().setPassword(attributes(),
                apiKey == null || apiKey.isBlank() ? null : apiKey.trim());
    }

    public static void clear() {
        PasswordSafe.getInstance().setPassword(attributes(), null);
    }
}
