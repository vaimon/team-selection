package ru.sfedu.teamselection.dto;

/**
 * Состояние текущего набора одним ответом (#10): сколько людей зарегистрировалось, сколько уже в
 * командах, сколько команд недоукомплектовано и сколько заявок залежалось.
 *
 * @param trackId      идентификатор текущего набора
 * @param trackName    название текущего набора
 * @param students     регистрации и распределение по командам
 * @param teams        укомплектованность команд
 * @param applications незакрытые заявки и приглашения
 */
public record AdminOverviewDto(
        Long trackId,
        String trackName,
        Students students,
        Teams teams,
        Applications applications
) {

    /**
     * Второй курс здесь — это «курс 2 и старше», как и везде в подсчёте мест.
     *
     * @param total                 всего зарегистрировалось
     * @param firstYear             из них первокурсников
     * @param secondYear            из них со второго курса и старше
     * @param withTeam              уже состоят в команде
     * @param withoutTeam           ещё без команды
     * @param firstYearWithoutTeam  первокурсников без команды
     * @param secondYearWithoutTeam старшекурсников без команды
     */
    public record Students(
            int total,
            int firstYear,
            int secondYear,
            int withTeam,
            int withoutTeam,
            int firstYearWithoutTeam,
            int secondYearWithoutTeam
    ) {
    }

    /**
     * @param total      всего команд в наборе
     * @param complete   набрали целевой состав по обоим курсам
     * @param incomplete кому-то ещё не хватает людей
     */
    public record Teams(
            int total,
            int complete,
            int incomplete
    ) {
    }

    /**
     * @param pendingRequests    неотвеченные заявки от студентов
     * @param pendingInvites     неотвеченные приглашения от команд
     * @param staleRequests      из них заявок старше порога
     * @param staleInvites       из них приглашений старше порога
     * @param staleThresholdDays сам порог, с которым считали
     */
    public record Applications(
            int pendingRequests,
            int pendingInvites,
            int staleRequests,
            int staleInvites,
            int staleThresholdDays
    ) {
    }
}
