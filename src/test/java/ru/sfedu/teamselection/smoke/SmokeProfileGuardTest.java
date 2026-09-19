package ru.sfedu.teamselection.smoke;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The smoke profile signs anyone in by email, so it must never come up next to prod.
 */
class SmokeProfileGuardTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SmokeProfileGuard.class);

    @Test
    void refusesToStartTogetherWithProd() {
        runner.withPropertyValues("spring.profiles.active=prod,smoke")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("prod"));
    }

    @Test
    void startsOnItsOwn() {
        runner.withPropertyValues("spring.profiles.active=smoke")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(SmokeProfileGuard.class));
    }
}
