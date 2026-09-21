package ru.sfedu.teamselection.service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sfedu.teamselection.domain.Student;
import ru.sfedu.teamselection.domain.TeamComposition;
import ru.sfedu.teamselection.domain.Track;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.dto.TechnologyDto;
import ru.sfedu.teamselection.dto.StudentUpdateDto;
import ru.sfedu.teamselection.dto.student.StudentCreationDto;
import ru.sfedu.teamselection.dto.student.StudentDto;
import ru.sfedu.teamselection.dto.student.StudentSearchOptionsDto;
import ru.sfedu.teamselection.enums.TrackType;
import ru.sfedu.teamselection.exception.BusinessException;
import ru.sfedu.teamselection.exception.ForbiddenException;
import ru.sfedu.teamselection.exception.NotFoundException;
import ru.sfedu.teamselection.mapper.TechnologyMapper;
import ru.sfedu.teamselection.mapper.student.StudentCreationDtoMapper;
import ru.sfedu.teamselection.mapper.student.StudentDtoMapper;
import ru.sfedu.teamselection.repository.RoleRepository;
import ru.sfedu.teamselection.repository.StudentRepository;
import ru.sfedu.teamselection.repository.TechnologyRepository;
import ru.sfedu.teamselection.repository.specification.StudentSpecification;
import ru.sfedu.teamselection.service.security.PermissionLevelUpdate;
import ru.sfedu.teamselection.service.student.update.StudentUpdateFactory;

@Slf4j
@RequiredArgsConstructor
@Service
public class StudentService {
    private final StudentRepository studentRepository;
    private final TechnologyRepository technologyRepository;
    private final RoleRepository roleRepository;

    private final StudentUpdateFactory studentUpdateFactory;
    private final TrackService trackService;
    private final ActivityService activityService;

    @Lazy
    @Autowired
    private UserService userService;

    private final StudentCreationDtoMapper studentCreationDtoMapper;
    private final TechnologyMapper technologyDtoMapper;


    @Autowired
    @Lazy
    private TeamService teamService;

    @Autowired
    private StudentDtoMapper studentDtoMapper;

    /**
     * Find Student entity by id
     * @param id student id
     * @return entity with given id
     * @throws ru.sfedu.teamselection.exception.NotFoundException in case there is no student with such id
     */
    @Transactional(readOnly = true)
    public Student findByIdOrElseThrow(Long id) throws NotFoundException {
        return studentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Студент не найден, id=" + id));
    }

    @Transactional(readOnly = true)
    public List<Student> findAllByTrack(Long trackId, Sort sort) {
        Specification<Student> spec = StudentSpecification.byTrack(trackId);
        return studentRepository.findAll(spec, sort);
    }

    /**
     * Find all students
     * @return page of students
     */
    @Transactional(readOnly = true)
    public List<Student> findAll() {
        return studentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<Student> search(String like,
                                Long trackId,
                                List<Integer> course,
                                List<Integer> groupNumber,
                                Boolean hasTeam,
                                Boolean isCaptain,
                                List<Long> technologies,
                                Long teamId,
                                Pageable pageable) {

        Specification<Student> spec = (root, query, cb) -> cb.conjunction();

        if (like != null) {
            spec = spec.and(StudentSpecification.like(like));
        }
        if (trackId != null) {
            spec = spec.and(StudentSpecification.byTrack(trackId));
        }
        if (course != null && !course.isEmpty()) {
            spec = spec.and(StudentSpecification.byCourse(course));
        }
        if (groupNumber != null && !groupNumber.isEmpty()) {
            spec = spec.and(StudentSpecification.byGroup(groupNumber));
        }
        if (hasTeam != null) {
            spec = spec.and(StudentSpecification.byHasTeam(hasTeam));
        }
        if (isCaptain != null) {
            spec = spec.and(StudentSpecification.byIsCaptain(isCaptain));
        }
        if (technologies != null && !technologies.isEmpty()) {
            spec = spec.and(StudentSpecification.hasTechnologies(technologies));
        }

        Sort sort = pageable.getSort();
        for (Sort.Order order : sort) {
            if ("name".equals(order.getProperty())) {
                pageable = PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        Sort.by(order.getDirection(), "user.fio")
                );
                break;
            }
        }

        return studentRepository.findAll(spec, pageable);
    }


    /**
     * Registers the user for the current selection. The students row is lifetime (one per user), so a student
     * returning for a new selection gets their row updated and moved to it, without last year's team.
     * @param dto DTO containing student data; its track id is ignored
     * @param sender the caller; the questionnaire can only be filled for oneself
     * @return created or updated student
     */
    @Transactional
    public Student create(StudentCreationDto dto, User sender) {
        if (!Objects.equals(dto.getUserId(), sender.getId())) {
            throw new ForbiddenException("Анкету участника можно заполнить только за себя");
        }
        User user = userService.findByIdOrElseThrow(dto.getUserId());
        if ("ADMIN".equals(user.getRole().getName())) {
            // registering would silently replace the ADMIN role with STUDENT
            throw new BusinessException("Администратор не участвует в наборе, анкета участника не нужна");
        }
        Track active = trackService.getActive();
        trackService.assertNotHandedOver(active);
        var role = roleRepository.findByName("STUDENT")
                .orElseThrow(() -> new NotFoundException("Роль STUDENT не найдена"));
        user.setRole(role);
        List<Long> technologyIds = dto.getTechnologies().stream().map(TechnologyDto::getId).toList();

        if (!studentRepository.existsByUserId(user.getId())) {
            Student student = studentCreationDtoMapper.mapToEntity(dto);
            student.setUser(user);
            student.setCurrentTrack(active);
            student.setTechnologies(technologyRepository.findAllByIdIn(technologyIds));
            Student registered = studentRepository.save(student);
            activityService.questionnaireFilled(registered, sender);
            return registered;
        }

        Student student = studentRepository.findByUserId(user.getId());
        student.setCourse(dto.getCourse());
        student.setGroupNumber(dto.getGroupNumber());
        student.setAboutSelf(dto.getAboutSelf());
        student.setContacts(dto.getContacts());
        student.setTechnologies(technologyRepository.findAllByIdIn(technologyIds));
        if (student.getCurrentTrack() == null || !Objects.equals(student.getCurrentTrack().getId(), active.getId())) {
            // the old team stays in its read-only selection; membership history is kept in teams_students
            student.setHasTeam(false);
            student.setCurrentTeam(null);
            student.setIsCaptain(false);
            student.setCurrentTrack(active);
        }
        activityService.questionnaireFilled(student, sender);
        return studentRepository.save(student);
    }

    /**
     * Deletes student entity
     * @param id student id
     * @throws NotFoundException in case there is no student with such id
     */
    @Transactional
    public void delete(Long id, User actor) {
        Student st = findByIdOrElseThrow(id);
        assertRosterEditable(st);
        activityService.studentDeleted(st, actor);
        if (Boolean.TRUE.equals(st.getHasTeam())) {
            teamService.removeStudentFromTeam(st.getCurrentTeam(), st);
        }
        // Аккаунт остаётся (#38), а он в этой же сессии по-прежнему ссылается на анкету: без разрыва
        // обратной стороны связи Hibernate на flush увидит, что живой User держит удалённого Student.
        st.getUser().setStudent(null);
        studentRepository.delete(st);
    }

    /**
     * Updates student by id using given data.
     * @param id id of the user.
     * @param dto DTO containing new data.
     * @param permission regulates if update is limited to safe fields
     * @return updated student
     */
    @Transactional
    public Student update(Long id, StudentUpdateDto dto, PermissionLevelUpdate permission, User actor) {
        Student student = findByIdOrElseThrow(id);
        assertRosterEditable(student);

        studentUpdateFactory.getHandler(permission).update(student, dto);

        activityService.studentUpdated(student, actor);
        return studentRepository.save(student);
    }

    /** Студент переданного набора — уже часть состава в core: ни правки, ни удаления здесь (#15). */
    private void assertRosterEditable(Student student) {
        if (student.getCurrentTrack() != null) {
            trackService.assertNotHandedOver(student.getCurrentTrack());
        }
    }

    /**
     * Сообщение о переборе, если команда этого студента вышла за целевой состав по курсам.
     *
     * <p>Проверяется после правки, а не до: курс меняют потому, что он такой на самом деле, и
     * отказ загнал бы администратора в тупик — чтобы исправить данные, пришлось бы сперва
     * развалить команду. Перебор остаётся видимым, а не тихим, что issue и требует.
     *
     * <p>Читает студента заново в своей транзакции: состав команды ленивый, а вызывают этот метод
     * уже после того, как правка закоммичена, — иначе ленивая коллекция не подгрузится.
     *
     * @return текст предупреждения либо {@code null}, если всё в пределах целевого состава
     */
    @Transactional(readOnly = true)
    public String compositionWarning(Long studentId) {
        Student student = findByIdOrElseThrow(studentId);
        if (student.getCurrentTeam() == null) {
            return null;
        }
        TeamComposition composition = TeamComposition.of(student.getCurrentTeam());
        if (composition.firstYears() > composition.firstYearTarget()) {
            return "В команде «%s» теперь %d первокурсников при цели %d".formatted(
                    student.getCurrentTeam().getName(), composition.firstYears(), composition.firstYearTarget());
        }
        if (composition.secondYears() > composition.secondYearTarget()) {
            return "В команде «%s» теперь %d старшекурсников при цели %d".formatted(
                    student.getCurrentTeam().getName(), composition.secondYears(), composition.secondYearTarget());
        }
        return null;
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    public TrackType typeOfStudentTrack(Student student) {
        return switch (student.getCourse()) {
            case 1, 2 -> TrackType.bachelor;
            case 5 -> TrackType.master;
            default -> null;
        };
    }

    /**
     * Reads without an explicit track show the current selection; an explicit id browses history.
     */
    public Long resolveTrackId(Long trackId) {
        return trackId != null ? trackId : trackService.getActive().getId();
    }

    /**
     * Returns DTO containing all possible filter options for searching for students
     * @return new DTO {@link StudentSearchOptionsDto}
     */
    @Transactional(readOnly = true)
    public StudentSearchOptionsDto getSearchOptionsStudents(Long trackId) {
        var students = search(null, trackId, null, null, null, null, null, Pageable.unpaged());

        StudentSearchOptionsDto studentSearchOptionsDto = new StudentSearchOptionsDto();
        for (Student student : students) {
            studentSearchOptionsDto.getCourses().add(student.getCourse());
            studentSearchOptionsDto.getGroups().add(student.getGroupNumber());
            studentSearchOptionsDto.getTechnologies().addAll(
                    student.getTechnologies()
                    .stream()
                    .map(technologyDtoMapper::mapToDto)
                    .toList()
            );
        }
        return studentSearchOptionsDto;
    }

    /**
     * Returns the student id corresponding to the current (authenticated) user.
     * @return id of the student or null if user is not a student
     */
    @Transactional(readOnly = true)
    public Long getCurrentStudent() {
        User currentUser = userService.getCurrentUser();
        if (studentRepository.existsByUserId(currentUser.getId())) {
            return studentRepository.findByUserId(currentUser.getId()).getId();
        }
        return null;
    }

    /**
     * Возвращает студентов для редактирования состава команды:
     * – тех, кто уже в команде (currentTeam.id = teamId)
     * – и свободных на заданном треке (currentTrack.id = trackId && hasTeam = false)
     */
    @Transactional(readOnly = true)
    public List<StudentDto> findFreeOrInTeam(Long trackId, Long teamId) {
        List<Student> list = studentRepository.findFreeOrInTeam(trackId, teamId);
        return list.stream()
                .map(x -> studentDtoMapper.mapToDto(x))
                .collect(Collectors.toList());
    }
}
