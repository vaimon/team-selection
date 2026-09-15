package ru.sfedu.teamselection.service;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sfedu.teamselection.domain.Track;
import ru.sfedu.teamselection.dto.track.NewSelectionDto;
import ru.sfedu.teamselection.dto.track.TrackCreationDto;
import ru.sfedu.teamselection.dto.track.TrackDto;
import ru.sfedu.teamselection.enums.TrackType;
import ru.sfedu.teamselection.exception.BusinessException;
import ru.sfedu.teamselection.exception.ConstraintViolationException;
import ru.sfedu.teamselection.exception.NotFoundException;
import ru.sfedu.teamselection.mapper.track.TrackCreationDtoMapper;
import ru.sfedu.teamselection.mapper.track.TrackDtoMapper;
import ru.sfedu.teamselection.repository.TrackRepository;


@Slf4j
@RequiredArgsConstructor
@Service
public class TrackService {

    private final TrackRepository trackRepository;
    private final TrackCreationDtoMapper trackCreationDtoMapper;

    private final TrackDtoMapper trackDtoMapper;

    /**
     * Find Track entity by id
     * @param id track id
     * @return entity with given id
     * @throws NoSuchElementException in case there is no track with such id
     */
    public Track findByIdOrElseThrow(Long id) {
        return trackRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(id.toString()));
    }

    public List<TrackDto> findAll() {
        return trackRepository.findAll().stream().map(trackDtoMapper::mapToDto).toList();
    }

    /**
     * The current selection. Every write that belongs to a selection goes here, whatever track id a client sends.
     * @throws NotFoundException when no selection has been started yet
     */
    @Transactional(readOnly = true)
    public Track getActive() {
        return trackRepository.findByActiveTrue()
                .orElseThrow(() -> new NotFoundException("Отбор не настроен: нет активного отбора"));
    }

    /**
     * Previous selections are history: nothing in them changes.
     * @throws BusinessException when the track is not the current selection
     */
    public void assertWritable(Track track) {
        if (!Boolean.TRUE.equals(track.getActive())) {
            throw new BusinessException("Отбор «%s» завершён, его данные только для чтения".formatted(track.getName()));
        }
    }

    /**
     * Create a new track
     * @param dto DTO containing track data
     * @return created Track entity
     */
    @Transactional
    public Track create(TrackCreationDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("TrackCreationDto must not be null");
        }
        Track track = trackCreationDtoMapper.mapToEntity(dto);
        assertNameIsFree(track.getName(), track.getType());
        return trackRepository.save(track);
    }

    /**
     * Starts the next selection: a new active track with the current settings copied; the previous one
     * becomes read-only history.
     */
    @Transactional
    public Track startNewSelection(NewSelectionDto dto) {
        Optional<Track> previous = trackRepository.findByActiveTrue();
        LocalDate start = dto.getStartDate();
        String name = dto.getName() != null && !dto.getName().isBlank()
                ? dto.getName()
                : "Отбор " + (start != null ? start : LocalDate.now()).getYear();
        TrackType type = previous.map(Track::getType).orElse(TrackType.bachelor);
        assertNameIsFree(name, type);

        Track next = Track.builder()
                .name(name)
                .about(dto.getAbout())
                .startDate(start)
                .endDate(dto.getEndDate())
                .type(type)
                .active(true)
                .build();
        previous.ifPresent(track -> {
            next.setFirstYearTarget(track.getFirstYearTarget());
            next.setSecondYearTarget(track.getSecondYearTarget());
            track.setActive(false);
            // flushed before the insert: the partial unique index allows only one active track at a time
            trackRepository.saveAndFlush(track);
        });
        log.info("New selection '{}' started, previous track {}", name, previous.map(Track::getId).orElse(null));
        return trackRepository.save(next);
    }

    /**
     * Update an existing track. Which track is active is changed only by {@link #startNewSelection}.
     * @param id id of the track to update
     * @param trackDto DTO containing updated track data
     * @return updated Track entity
     */
    @Transactional
    public Track update(Long id, TrackDto trackDto) {
        Track existingTrack = findByIdOrElseThrow(id);
        assertWritable(existingTrack);

        existingTrack.setName(trackDto.getName());
        existingTrack.setAbout(trackDto.getAbout());
        existingTrack.setStartDate(trackDto.getStartDate());
        existingTrack.setEndDate(trackDto.getEndDate());
        existingTrack.setType(TrackType.valueOf(trackDto.getType()));
        existingTrack.setFirstYearTarget(
                Objects.requireNonNullElse(trackDto.getFirstYearTarget(), existingTrack.getFirstYearTarget()));
        existingTrack.setSecondYearTarget(
                Objects.requireNonNullElse(trackDto.getSecondYearTarget(), existingTrack.getSecondYearTarget()));

        return trackRepository.save(existingTrack);
    }

    /**
     * Delete track by id
     * @param id track id
     */
    @Transactional
    public void deleteById(Long id) {
        Track track = findByIdOrElseThrow(id);
        if (Boolean.TRUE.equals(track.getActive())) {
            throw new BusinessException("Нельзя удалить текущий отбор. Сначала начните новый.");
        }
        if (!track.getStudents().isEmpty()) {
            log.error(
                    "Track delete failed with BusinessException. Tried to delete track {}, but it has students",
                    id
            );
            throw new BusinessException(
                    "Нельзя удалить трек, в котором уже есть участники. "
                    + "Сначала нужно удалить из него всех студентов и команды."
            );
        }
        trackRepository.delete(track);
    }

    private void assertNameIsFree(String name, TrackType type) {
        trackRepository.findByNameIgnoreCaseAndType(name, type)
                .ifPresent(t -> {
                    throw new ConstraintViolationException(
                            String.format("Track with name '%s' and type '%s' already exists", name, type)
                    );
                });
    }
}
