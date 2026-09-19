package ru.sfedu.teamselection.dto.board;

import java.util.List;

/**
 * Доска состава текущего набора (#14): все команды с участниками и пул студентов без команды.
 *
 * @param firstYearTarget  цель набора по 1 курсу; у команды может быть своя
 * @param secondYearTarget цель набора по 2 курсу и старше
 */
public record CompositionBoardDto(
        Long trackId,
        String trackName,
        int firstYearTarget,
        int secondYearTarget,
        List<TeamCard> teams,
        List<StudentCard> pool
) {

    /**
     * @param version           передаётся обратно в любое действие с командой
     * @param firstYearTarget   действующая цель: переопределение команды, иначе цель набора
     * @param firstYearOverride переопределение команды; null — действует цель набора
     */
    public record TeamCard(
            Long id,
            String name,
            Long version,
            Long leadId,
            List<StudentCard> members,
            int firstYears,
            int secondYears,
            int firstYearTarget,
            int secondYearTarget,
            Integer firstYearOverride,
            Integer secondYearOverride,
            TeamStatus status
    ) {
    }

    public record StudentCard(Long id, String name, Integer course, Integer group, boolean lead) {
    }

    /**
     * OVER_TARGET важнее INCOMPLETE: команда 4 + 1 и перебрала первокурсников, и недобрала второй
     * курс, но первое требует действия с ней самой. Недобор видно по счётчикам.
     */
    public enum TeamStatus {
        COMPLETE,
        INCOMPLETE,
        OVER_TARGET
    }
}
