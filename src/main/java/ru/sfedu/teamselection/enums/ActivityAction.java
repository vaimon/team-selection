package ru.sfedu.teamselection.enums;

/**
 * Что произошло. Код действия нужен фронту для значка и фильтра; читаемую строку собирает
 * ActivityService в момент записи (#16).
 */
public enum ActivityAction {
    TEAM_CREATED,
    TEAM_UPDATED,
    TEAM_DISBANDED,
    MEMBER_JOINED,
    MEMBER_REMOVED,
    MEMBER_LEFT,
    MEMBER_MOVED,
    LEAD_CHANGED,
    TARGETS_CHANGED,
    APPLICATION_SENT,
    APPLICATION_ANSWERED,
    JOIN_LINK_ISSUED,
    JOIN_LINK_DISABLED,
    QUESTIONNAIRE_FILLED,
    STUDENT_UPDATED,
    STUDENT_DELETED,
    ROLE_ASSIGNED,
    USER_DEACTIVATED,
    SELECTION_SETTINGS_CHANGED,
    SELECTION_STARTED,
    HANDED_OVER,
    HANDOVER_CANCELLED
}
