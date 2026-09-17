package ru.sfedu.teamselection.dto.team;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.sfedu.teamselection.enums.JoinRefusalReason;

/**
 * Что видно по ссылке-приглашению до входа в команду (#13).
 *
 * <p>Отдаётся любому вошедшему в систему, в том числе тому, кто ещё не заполнил анкету: иначе
 * ссылка из чата упирается в 403 и человек не понимает, куда попал. Поэтому имя тимлида здесь
 * заполняется только для участников набора — это единственные персональные данные в ответе, и для
 * решения «регистрироваться или нет» они не нужны.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamJoinPreviewDto {
    private Long teamId;
    private String teamName;
    private String captainName;

    private int firstYears;
    private int firstYearTarget;
    private int secondYears;
    private int secondYearTarget;

    private boolean canJoin;
    private JoinRefusalReason refusalReason;
}
