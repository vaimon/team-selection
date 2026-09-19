package ru.sfedu.teamselection.smoke;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * The smoke profile signs anyone in by email. A misconfigured prod start with it would open every
 * account to anyone who can reach the API, so that start fails instead.
 */
@Slf4j
@Configuration
@Profile(SmokeProfileGuard.PROFILE)
public class SmokeProfileGuard {
    public static final String PROFILE = "smoke";

    public SmokeProfileGuard(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "The smoke profile signs anyone in by email and must never run together with prod");
        }
        log.warn("Smoke profile active: sign-in by email is enabled at {}", SmokeLoginController.LOGIN_PATH);
    }
}
