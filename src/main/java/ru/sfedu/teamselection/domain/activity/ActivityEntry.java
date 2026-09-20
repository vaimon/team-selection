package ru.sfedu.teamselection.domain.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.sfedu.teamselection.enums.ActivityAction;

/**
 * Запись истории (#16). Имена скопированы на момент действия: команда может быть переименована
 * или распущена, а запись должна читаться так же.
 */
@Entity
@Table(name = "activity_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityAction action;

    /** Пусто, если действие пришло не от человека: например, передача состава по вызову из core. */
    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_name")
    private String actorName;

    @Column(name = "actor_email")
    private String actorEmail;

    @Column(name = "track_id")
    private Long trackId;

    @Column(name = "team_id")
    private Long teamId;

    /** Вторая команда перемещения: откуда, если team_id — куда. */
    @Column(name = "related_team_id")
    private Long relatedTeamId;

    @Column(name = "student_id")
    private Long studentId;

    @Column(nullable = false)
    private String summary;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
