package ru.sfedu.teamselection.mapper.track;


import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.sfedu.teamselection.domain.Track;
import ru.sfedu.teamselection.dto.track.TrackDto;
import ru.sfedu.teamselection.enums.SelectionWindowState;
import ru.sfedu.teamselection.enums.TrackType;
import ru.sfedu.teamselection.mapper.DtoMapper;
import ru.sfedu.teamselection.mapper.team.TeamDtoMapper;

@Component
@RequiredArgsConstructor
public class TrackDtoMapper implements DtoMapper<TrackDto, Track> {

    @Autowired
    private TeamDtoMapper teamDtoMapper;
    private final Clock clock;


    @Override
    public Track mapToEntity(TrackDto dto) {
        return Track.builder()
                .id(dto.getId())
                .name(dto.getName())
                .about(dto.getAbout())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .type(TrackType.valueOf(dto.getType()))
                .firstYearTarget(dto.getFirstYearTarget())
                .secondYearTarget(dto.getSecondYearTarget())
                .currentTeams(dto.getCurrentTeams().stream().map(x->teamDtoMapper.mapToEntity(x)).toList())
                .build();
    }

    public TrackDto mapToDtoWithoutTeams(Track entity) {
        return TrackDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .about(entity.getAbout())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .type(entity.getType() != null ? entity.getType().name() : null)
                .firstYearTarget(entity.getFirstYearTarget())
                .secondYearTarget(entity.getSecondYearTarget())
                .active(entity.getActive())
                .windowState(windowStateOf(entity))
                .handedOverAt(entity.getHandedOverAt())
                .build();
    }

    @Override
    public TrackDto mapToDto(Track entity) {
        return TrackDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .about(entity.getAbout())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .type(entity.getType() != null ? entity.getType().name() : null)
                .firstYearTarget(entity.getFirstYearTarget())
                .secondYearTarget(entity.getSecondYearTarget())
                .active(entity.getActive())
                .windowState(windowStateOf(entity))
                .handedOverAt(entity.getHandedOverAt())
                .currentTeams(entity.getCurrentTeams().stream().map(x->teamDtoMapper.mapToDto(x)).toList())
                .build();
    }

    /**
     * Дублирует SelectionWindowService.stateOf намеренно: тот сервис зависит от TrackService, а
     * TrackService — от этого маппера. Инжектить сюда сервис значит замкнуть цикл в конструкторах,
     * поэтому маппер считает состояние сам, из общей на двоих функции в SelectionWindowState.
     */
    private SelectionWindowState windowStateOf(Track entity) {
        return SelectionWindowState.of(entity.getStartDate(), entity.getEndDate(), LocalDate.now(clock));
    }

}
