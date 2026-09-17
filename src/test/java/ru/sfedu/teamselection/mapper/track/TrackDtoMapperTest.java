package ru.sfedu.teamselection.mapper.track;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import ru.sfedu.teamselection.domain.Track;
import ru.sfedu.teamselection.enums.SelectionWindowState;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет, что маппер действительно считает состояние окна по своим часам, а не отдаёт null.
 * Сами границы окна разобраны в SelectionWindowServiceTest.
 */
class TrackDtoMapperTest {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private static TrackDtoMapper mapperAt(LocalDate today) {
        return new TrackDtoMapper(Clock.fixed(today.atStartOfDay(MOSCOW).toInstant(), MOSCOW));
    }

    private static Track track(LocalDate startDate, LocalDate endDate) {
        return Track.builder().id(1L).name("Набор 2026").startDate(startDate).endDate(endDate).build();
    }

    @Test
    void anOpenSelectionIsMappedAsOpen() {
        var dto = mapperAt(LocalDate.of(2026, 10, 15))
                .mapToDtoWithoutTeams(track(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));

        assertThat(dto.getWindowState()).isEqualTo(SelectionWindowState.OPEN);
    }

    @Test
    void aFinishedSelectionIsMappedAsClosed() {
        var dto = mapperAt(LocalDate.of(2026, 11, 1))
                .mapToDtoWithoutTeams(track(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));

        assertThat(dto.getWindowState()).isEqualTo(SelectionWindowState.CLOSED);
    }

    @Test
    void aSelectionThatHasNotStartedIsMappedAsNotOpen() {
        var dto = mapperAt(LocalDate.of(2026, 9, 30))
                .mapToDtoWithoutTeams(track(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));

        assertThat(dto.getWindowState()).isEqualTo(SelectionWindowState.NOT_OPEN);
    }
}
