package ru.sfedu.teamselection.mapper.track;

import java.time.LocalDate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.sfedu.teamselection.domain.Track;
import ru.sfedu.teamselection.dto.track.TrackCreationDto;
import ru.sfedu.teamselection.enums.TrackType;

public class TrackCreationDtoMapperTest {
    private final TrackCreationDtoMapper underTest = new TrackCreationDtoMapper();

    @Test
    void mapToEntity() {
        Track expected = Track.builder()
                .id(null)
                .name("1")
                .about("some text about")
                .startDate(LocalDate.of(2025, 9, 12))
                .endDate(LocalDate.of(2026, 9, 11))
                .type(TrackType.master)
                .firstYearTarget(2)
                .secondYearTarget(4)
                .build();

        TrackCreationDto dto = TrackCreationDto.builder()
                .name("1")
                .about("some text about")
                .startDate(LocalDate.of(2025, 9, 12))
                .endDate(LocalDate.of(2026, 9, 11))
                .type("master")
                .firstYearTarget(2)
                .secondYearTarget(4)
                .build();

        Track actual = underTest.mapToEntity(dto);
        Assertions.assertEquals(expected.getId(), actual.getId());
        Assertions.assertEquals(expected.getName(), actual.getName());
        Assertions.assertEquals(expected.getAbout(), actual.getAbout());
        Assertions.assertEquals(expected.getType(), actual.getType());
        Assertions.assertEquals(expected.getStartDate(), actual.getStartDate());
        Assertions.assertEquals(expected.getEndDate(), actual.getEndDate());
        Assertions.assertEquals(expected.getFirstYearTarget(), actual.getFirstYearTarget());
        Assertions.assertEquals(expected.getSecondYearTarget(), actual.getSecondYearTarget());
        Assertions.assertTrue(actual.getCurrentTeams().isEmpty());
    }

    @Test
    void mapNullToEntity() {
        Track actual = underTest.mapToEntity(null);
        Assertions.assertNull(actual);
    }

    @Test
    void mapToDto() {
        Track entity = Track.builder()
                .id(1L)
                .name("1")
                .about("some text about")
                .startDate(LocalDate.of(2025, 9, 12))
                .endDate(LocalDate.of(2026, 9, 11))
                .type(TrackType.master)
                .firstYearTarget(2)
                .secondYearTarget(4)
                .build();

        TrackCreationDto expected = TrackCreationDto.builder()
                .name("1")
                .about("some text about")
                .startDate(LocalDate.of(2025, 9, 12))
                .endDate(LocalDate.of(2026, 9, 11))
                .type("master")
                .firstYearTarget(2)
                .secondYearTarget(4)
                .build();
        TrackCreationDto actual = underTest.mapToDto(entity);

        Assertions.assertEquals(expected.getName(), actual.getName());
        Assertions.assertEquals(expected.getAbout(), actual.getAbout());
        Assertions.assertEquals(expected.getType(), actual.getType());
        Assertions.assertEquals(expected.getStartDate(), actual.getStartDate());
        Assertions.assertEquals(expected.getEndDate(), actual.getEndDate());
        Assertions.assertEquals(expected.getFirstYearTarget(), actual.getFirstYearTarget());
        Assertions.assertEquals(expected.getSecondYearTarget(), actual.getSecondYearTarget());
    }

    @Test
    void mapNullToDto() {
        TrackCreationDto actual = underTest.mapToDto(null);
        // then
        Assertions.assertNull(actual);
    }
}
