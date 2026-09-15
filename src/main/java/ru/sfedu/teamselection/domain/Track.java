package ru.sfedu.teamselection.domain;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.sfedu.teamselection.enums.TrackType;


@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tracks")
public class Track {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    @Size(max = 255)
    private String name;

    @Column
    @Size(max = 255)
    private String about;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column
    @Enumerated(EnumType.STRING)
    private TrackType type;

    @Column(name = "first_year_target", nullable = false)
    @Builder.Default
    private Integer firstYearTarget = 3;

    @Column(name = "second_year_target", nullable = false)
    @Builder.Default
    private Integer secondYearTarget = 3;

    // Exactly one track is the current selection; the others are read-only history.
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean active = false;

    @OneToMany(mappedBy = "currentTrack", fetch = FetchType.LAZY)
    @Builder.Default
    List<Team> currentTeams = new ArrayList<>();

    @OneToMany(mappedBy = "currentTrack", fetch = FetchType.LAZY)
    @Builder.Default
    List<Student> students = new ArrayList<>();
}
