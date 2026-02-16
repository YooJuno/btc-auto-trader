package com.btcautotrader.upbit;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UpbitCredentials {
    private final String accessKey;
    private final String secretKey;

    public UpbitCredentials() {
        Dotenv dotenvCurrent = Dotenv.configure()
                .directory(".")
                .ignoreIfMissing()
                .load();
        Dotenv dotenvRoot = Dotenv.configure()
                .directory("..")
                .ignoreIfMissing()
                .load();
        Dotenv dotenvWorkspaceRoot = Dotenv.configure()
                .directory("../..")
                .ignoreIfMissing()
                .load();

        this.accessKey = firstNonBlank(
                dotenvCurrent.get("UPBIT_ACCESS_KEY"),
                dotenvRoot.get("UPBIT_ACCESS_KEY"),
                dotenvWorkspaceRoot.get("UPBIT_ACCESS_KEY"),
                System.getenv("UPBIT_ACCESS_KEY")
        );
        this.secretKey = firstNonBlank(
                dotenvCurrent.get("UPBIT_SECRET_KEY"),
                dotenvRoot.get("UPBIT_SECRET_KEY"),
                dotenvWorkspaceRoot.get("UPBIT_SECRET_KEY"),
                System.getenv("UPBIT_SECRET_KEY")
        );
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public boolean isConfigured() {
        return !isBlank(accessKey) && !isBlank(secretKey);
    }

    public Optional<UpbitAuthCredentials> toAuthCredentials() {
        if (!isConfigured()) {
            return Optional.empty();
        }
        return Optional.of(new UpbitAuthCredentials(accessKey.trim(), secretKey.trim()));
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
