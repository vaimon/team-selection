package ru.sfedu.teamselection.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /**
     * Часы для всего, что зависит от «сегодня». Зона прибита к Москве, а не к зоне JVM: окно набора
     * закрывается по московской полуночи независимо от того, как настроен хост. Тесты подменяют бин
     * на {@link Clock#fixed}.
     */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Europe/Moscow"));
    }
}
