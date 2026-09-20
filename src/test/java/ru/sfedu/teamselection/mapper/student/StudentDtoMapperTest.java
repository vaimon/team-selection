package ru.sfedu.teamselection.mapper.student;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import ru.sfedu.teamselection.domain.Student;
import ru.sfedu.teamselection.domain.Track;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.dto.student.StudentDto;
import ru.sfedu.teamselection.enums.TrackType;
import ru.sfedu.teamselection.mapper.TechnologyMapper;
import ru.sfedu.teamselection.mapper.application.ApplicationDtoMapper;
import ru.sfedu.teamselection.mapper.team.TeamDtoMapper;
import ru.sfedu.teamselection.mapper.user.UserMapper;

/**
 * Набор в карточке студента (#33): каталог обязан говорить, к какому набору человек относится.
 */
class StudentDtoMapperTest {

    private final StudentDtoMapper underTest = new StudentDtoMapper(
            Mockito.mock(TechnologyMapper.class),
            Mockito.mock(ApplicationDtoMapper.class),
            Mockito.mock(UserMapper.class));

    // teamDtoMapper внедряется полем и @Lazy — связь с TeamDtoMapper круговая, конструктора для него нет
    @BeforeEach
    void wireTheFieldInjectedMapper() {
        ReflectionTestUtils.setField(underTest, "teamDtoMapper", Mockito.mock(TeamDtoMapper.class));
    }

    private static Student studentOf(Track track) {
        return Student.builder()
                .id(1L)
                .course(1)
                .groupNumber(1)
                .currentTrack(track)
                .user(User.builder().id(2L).fio("Иванов Иван").build())
                .technologies(List.of())
                .applications(List.of())
                .teams(List.of())
                .build();
    }

    private static Track track() {
        return Track.builder()
                .id(7L)
                .name("Набор 2026")
                .type(TrackType.bachelor)
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 10, 31))
                .firstYearTarget(3)
                .secondYearTarget(3)
                .build();
    }

    /** Без id клиент не может сказать, к какому набору относится карточка, — с этого и начался #33. */
    @Test
    void theTrackCarriesItsId() {
        StudentDto dto = underTest.mapToDto(studentOf(track()));

        Assertions.assertEquals(7L, dto.getTrack().getId());
        Assertions.assertEquals("Набор 2026", dto.getTrack().getName());
        Assertions.assertEquals("bachelor", dto.getTrack().getType());
    }

    /**
     * Студент без набора — тот, кому роль STUDENT выдали без анкеты. Он попадает в каталог, если
     * искать без фильтра по набору, и раньше ронял всю страницу.
     */
    @Test
    void aStudentWithoutASelectionMapsToAnEmptyTrackRatherThanFailing() {
        StudentDto dto = underTest.mapToDto(studentOf(null));

        Assertions.assertNull(dto.getTrack());
        Assertions.assertEquals(1L, dto.getId());
    }
}
