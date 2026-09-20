package ru.sfedu.teamselection.mapper.student;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import ru.sfedu.teamselection.domain.Student;
import ru.sfedu.teamselection.domain.Track;
import ru.sfedu.teamselection.dto.student.StudentDto;
import ru.sfedu.teamselection.dto.student.StudentTrackDto;
import ru.sfedu.teamselection.dto.track.TrackCreationDto;
import ru.sfedu.teamselection.mapper.DtoMapper;
import ru.sfedu.teamselection.mapper.TechnologyMapper;
import ru.sfedu.teamselection.mapper.application.ApplicationDtoMapper;
import ru.sfedu.teamselection.mapper.team.TeamDtoMapper;
import ru.sfedu.teamselection.mapper.track.TrackCreationDtoMapper;
import ru.sfedu.teamselection.mapper.user.UserMapper;

@Component
@RequiredArgsConstructor
public class StudentDtoMapper implements DtoMapper<StudentDto, Student> {
    private final TechnologyMapper technologyDtoMapper;
    private final ApplicationDtoMapper applicationDtoMapper;
    private final UserMapper userMapper;
    @Lazy
    @Autowired
    private TeamDtoMapper teamDtoMapper;

    @Autowired
    private TrackCreationDtoMapper trackCreationDtoMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public Student mapToEntity(StudentDto dto) {
        return Student.builder()
                .id(dto.getId())
                .course(dto.getCourse())
                .groupNumber(dto.getGroupNumber())
                .aboutSelf(dto.getAboutSelf())
                .contacts(dto.getContacts())
                .hasTeam(dto.getHasTeam())
                .isCaptain(dto.getIsCaptain())
                .currentTeam(teamDtoMapper.mapToEntity(dto.getCurrentTeam()))
                .teams(dto.getTeams().stream().map(x -> teamDtoMapper.mapToEntity(x)).toList())
                //.user(dto.getUser())
                .technologies(dto.getTechnologies().stream().map(technologyDtoMapper::mapToEntity).toList())
                .applications(dto.getApplications().stream().map(applicationDtoMapper::mapToEntity).toList())
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StudentDto mapToDto(Student entity) {
        if (entity == null) {
            return null;
        }
        return StudentDto.builder()
                .id(entity.getId())
                .course(entity.getCourse())
                .groupNumber(entity.getGroupNumber())
                .aboutSelf(entity.getAboutSelf())
                .contacts(entity.getContacts())
                .track(trackOf(entity.getCurrentTrack()))
                .hasTeam(entity.getHasTeam())
                .isCaptain(entity.getIsCaptain())
                .currentTeam(teamDtoMapper.mapToDtoWithoutStudents(entity.getCurrentTeam()))
                .teams(entity.getTeams().stream().map(x -> teamDtoMapper.mapToDtoWithoutStudents(x)).toList())
                .user(userMapper.mapToDto(entity.getUser()))
                .technologies(entity.getTechnologies().stream().map(technologyDtoMapper::mapToDto).toList())
                .applications(entity.getApplications().stream().map(applicationDtoMapper::mapToDto).toList())
                .build();
    }


    /**
     * Набор студента. Пусто у того, кто анкету не заполнял: роль STUDENT выдаётся и без неё, а такой
     * студент попадает в каталог, когда поиск идёт без фильтра по набору.
     */
    private static StudentTrackDto trackOf(Track track) {
        if (track == null) {
            return null;
        }
        return StudentTrackDto.builder()
                .id(track.getId())
                .name(track.getName())
                .about(track.getAbout())
                .startDate(track.getStartDate())
                .endDate(track.getEndDate())
                .type(track.getType() != null ? track.getType().toString() : null)
                .firstYearTarget(track.getFirstYearTarget())
                .secondYearTarget(track.getSecondYearTarget())
                .build();
    }

    public StudentDto mapToDtoWithoutTeam(Student entity) {
        if (entity == null) {
            return null;
        }
        return StudentDto.builder()
                .id(entity.getId())
                .course(entity.getCourse())
                .groupNumber(entity.getGroupNumber())
                .aboutSelf(entity.getAboutSelf())
                .contacts(entity.getContacts())
                .hasTeam(entity.getHasTeam())
                .isCaptain(entity.getIsCaptain())
                .user(userMapper.mapToDto(entity.getUser()))
                .technologies(entity.getTechnologies().stream().map(technologyDtoMapper::mapToDto).toList())
                .applications(entity.getApplications().stream().map(applicationDtoMapper::mapToDto).toList())
                .build();
    }
}
