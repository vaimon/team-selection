package ru.sfedu.teamselection.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.sfedu.teamselection.domain.Role;
import ru.sfedu.teamselection.domain.Track;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.enums.SelectionWindowState;
import ru.sfedu.teamselection.exception.ForbiddenException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SelectionWindowServiceTest {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 10, 31);

    private final TrackService trackService = Mockito.mock(TrackService.class);

    private SelectionWindowService serviceAt(LocalDate today) {
        Clock fixed = Clock.fixed(today.atStartOfDay(MOSCOW).toInstant(), MOSCOW);
        return new SelectionWindowService(fixed, trackService);
    }

    private static Track track(LocalDate startDate, LocalDate endDate) {
        return Track.builder().name("Набор 2026").startDate(startDate).endDate(endDate).build();
    }

    private static User userWithRole(String roleName) {
        return User.builder().id(1L).role(Role.builder().name(roleName).build()).build();
    }

    @Test
    void theDayBeforeTheStartTheWindowIsNotOpenYet() {
        assertThat(serviceAt(START.minusDays(1)).stateOf(track(START, END)))
                .isEqualTo(SelectionWindowState.NOT_OPEN);
    }

    @Test
    void theWindowIsAlreadyOpenOnTheStartDateItself() {
        assertThat(serviceAt(START).stateOf(track(START, END)))
                .isEqualTo(SelectionWindowState.OPEN);
    }

    @Test
    void theWindowIsStillOpenOnTheEndDateItself() {
        assertThat(serviceAt(END).stateOf(track(START, END)))
                .isEqualTo(SelectionWindowState.OPEN);
    }

    @Test
    void theWindowIsClosedTheDayAfterTheEnd() {
        assertThat(serviceAt(END.plusDays(1)).stateOf(track(START, END)))
                .isEqualTo(SelectionWindowState.CLOSED);
    }

    @Test
    void aTrackWithoutAStartDateNeverOpens() {
        assertThat(serviceAt(END).stateOf(track(null, END)))
                .isEqualTo(SelectionWindowState.NOT_OPEN);
    }

    @Test
    void aTrackWithoutAnEndDateCountsAsClosed() {
        assertThat(serviceAt(START).stateOf(track(START, null)))
                .isEqualTo(SelectionWindowState.CLOSED);
    }

    @Test
    void aStudentMutationGoesThroughWhileTheWindowIsOpen() {
        Mockito.when(trackService.getActive()).thenReturn(track(START, END));

        assertThatCode(() -> serviceAt(START).assertStudentMutationAllowed(userWithRole("STUDENT")))
                .doesNotThrowAnyException();
    }

    @Test
    void aStudentMutationBeforeTheOpeningIsRefusedWithTheOpeningDate() {
        Mockito.when(trackService.getActive()).thenReturn(track(START, END));

        assertThatThrownBy(() ->
                serviceAt(START.minusDays(1)).assertStudentMutationAllowed(userWithRole("STUDENT")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("01.10.2026");
    }

    @Test
    void aStudentMutationAfterTheCloseIsRefusedWithTheClosingDate() {
        Mockito.when(trackService.getActive()).thenReturn(track(START, END));

        assertThatThrownBy(() ->
                serviceAt(END.plusDays(1)).assertStudentMutationAllowed(userWithRole("STUDENT")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("31.10.2026");
    }

    @Test
    void aTrackMissingItsEndDateRefusesWithoutPrintingNull() {
        Mockito.when(trackService.getActive()).thenReturn(track(START, null));

        assertThatThrownBy(() -> serviceAt(START).assertStudentMutationAllowed(userWithRole("STUDENT")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageNotContaining("null")
                .hasMessageContaining("дата окончания");
    }

    @Test
    void anAdminStillMutatesAfterTheClose() {
        assertThatCode(() -> serviceAt(END.plusDays(1)).assertStudentMutationAllowed(userWithRole("ADMIN")))
                .doesNotThrowAnyException();
        // администратор освобождён до чтения набора — за активным треком даже не ходят
        Mockito.verify(trackService, Mockito.never()).getActive();
    }
}
