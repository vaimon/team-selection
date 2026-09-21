package ru.sfedu.teamselection.service;

import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sfedu.teamselection.domain.Role;
import ru.sfedu.teamselection.domain.Student;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.dto.UserDto;
import ru.sfedu.teamselection.dto.UserSearchCriteria;
import ru.sfedu.teamselection.exception.BusinessException;
import ru.sfedu.teamselection.exception.NotFoundException;
import ru.sfedu.teamselection.mapper.UserToStudentUpdateMapper;
import ru.sfedu.teamselection.mapper.user.UserMapper;
import ru.sfedu.teamselection.repository.RoleRepository;
import ru.sfedu.teamselection.repository.StudentRepository;
import ru.sfedu.teamselection.repository.UserRepository;
import ru.sfedu.teamselection.repository.specification.UserSpecification;
import ru.sfedu.teamselection.service.security.PermissionLevelUpdate;
import ru.sfedu.teamselection.service.security.UserSessionService;
import ru.sfedu.teamselection.service.student.update.StudentUpdateFactory;


@RequiredArgsConstructor
@Service
public class UserService {
    /** Ролей две: STUDENT достаётся всякому, кто вошёл, ADMIN выдаётся отдельно. */
    private static final String STUDENT_ROLE = "STUDENT";
    private static final String ADMIN_ROLE = "ADMIN";

    /**
     * Почты будущих администраторов через запятую. Роль по списку выдаётся только при создании
     * аккаунта: организатор получает её первым входом после сброса базы, а дальше ролями
     * распоряжается админка, и список у неё ничего не отбирает. Пусто — никому.
     */
    @Value("${app.initial-admin-emails:}")
    private String initialAdminEmails;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StudentRepository studentRepository;

    private final StudentUpdateFactory studentUpdateFactory;
    private final UserToStudentUpdateMapper userToStudentUpdateMapper;

    @Autowired
    private TrackService trackService;
    private final UserSessionService userSessionService;
    private final ActivityService activityService;

    private final UserMapper userMapper;


    @Transactional(readOnly = true)
    public Page<UserDto> search(UserSearchCriteria criteria, Pageable pageable) {
        Specification<User> spec = UserSpecification.build(criteria);
        return userRepository.findAll(spec, pageable)
                .map(userMapper::mapToDto);
    }

    /**
     * Find User entity by id
     * @param id user id
     * @return user with given id
     * @throws NotFoundException in case there is no user with such id
     */
    public User findByIdOrElseThrow(Long id) throws NotFoundException {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пользователь с id `" + id + "` не найден"));
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Пользователь с почтой `" + email + "` не найден"));
    }

    public User findByUsername(String username) {
        return userRepository.findByFio(username)
                .orElseThrow(() -> new NotFoundException("Пользователь с именем `" + username + "` не найден"));
    }

    /**
     * Возвращает пользователя с этой почтой, создавая его при первом входе.
     *
     * <p>Метод намеренно без {@code @Transactional}: при двух одновременных первых входах одного
     * человека вставка падает на unique-индексе, а перечитать строку внутри той же транзакции уже
     * нельзя — она помечена rollback-only. Без внешней транзакции каждый вызов репозитория идёт
     * своей, поэтому перечитывание срабатывает.
     */
    public User findOrCreateByEmail(String email, String fio, String azureId) {
        return userRepository.findByEmailFetchRole(email)
                .orElseGet(() -> createOnFirstLogin(email, fio, azureId));
    }

    private User createOnFirstLogin(String email, String fio, String azureId) {
        try {
            return userRepository.save(User.builder()
                    .fio(fio)
                    .email(email)
                    .isEnabled(true)
                    .role(findRoleByNameOrElseThrow(isInitialAdmin(email) ? ADMIN_ROLE : STUDENT_ROLE))
                    .azureId(azureId)
                    .build());
        } catch (DataIntegrityViolationException alreadyCreated) {
            return userRepository.findByEmailFetchRole(email).orElseThrow();
        }
    }

    private boolean isInitialAdmin(String email) {
        return Arrays.stream(initialAdminEmails.split(","))
                .map(String::trim)
                .filter(listed -> !listed.isEmpty())
                .anyMatch(listed -> listed.equalsIgnoreCase(email));
    }

    public Role findRoleByNameOrElseThrow(String roleName) {
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new NotFoundException("Роль `" + roleName + "` не найдена"));
    }

    /**
     * Get current user based on security context
     * @return Authenticated user object
     */
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String principalName = auth.getName();
        var authAuthorities = auth.getAuthorities();
        if (auth.getPrincipal() instanceof OidcUser oidc) {
            return findByEmail(oidc.getEmail());
        } else {
            return findByUsername(principalName);
        }
    }

    @Transactional
    public User createOrUpdate(UserDto dto, PermissionLevelUpdate permission, User actor) {
        if (dto.getId() != null) {
            // --- обновление ---
            User existing = findByIdOrElseThrow(dto.getId());
            // Правка пользователя правит и его анкету, а ФИО и почта уже в составе, переданном в core (#15).
            if (existing.getStudent() != null && existing.getStudent().getCurrentTrack() != null) {
                trackService.assertNotHandedOver(existing.getStudent().getCurrentTrack());
            }

            if (permission == PermissionLevelUpdate.ADMIN) {
                assertNotSwitchingOffTheLastAdmin(existing, dto.getIsEnabled());
                // обновляем роль
                assignRole(existing.getId(), dto.getRole(), actor);
                // обновляем остальные поля
                existing.setFio(dto.getFio());
                existing.setEmail(dto.getEmail());
                existing.setIsEnabled(dto.getIsEnabled());
            }

            existing.setIsRemindEnabled(dto.getIsRemindEnabled());

            if (existing.getStudent() != null) {
                studentUpdateFactory.getHandler(permission).update(
                        existing.getStudent(),
                        userToStudentUpdateMapper.userDtoToStudentUpdateDto(dto)
                );
                activityService.studentUpdated(existing.getStudent(), actor);
            } else {
                // у администратора анкеты нет, но правка аккаунта — тоже изменение, и она в истории
                activityService.userUpdated(existing, actor);
            }

            return userRepository.save(existing);

        } else {
            User user = userMapper.mapToEntity(dto);
            // роль проставляем сами, чтобы нельзя было зарегистрироваться администратором
            Role role = findRoleByNameOrElseThrow(STUDENT_ROLE);
            user.setRole(role);
            if (dto.getStudent() != null) {
                Student student = Student.builder()
                        .user(user)
                        .build();
                studentRepository.save(student);
            }

            User created = userRepository.save(user);
            activityService.userCreated(created, actor);
            return created;
        }
    }


    @Transactional
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    @Transactional
    public User assignRole(Long userId, String roleName, User actor) {
        User user = findByIdOrElseThrow(userId);
        Role role = findRoleByNameOrElseThrow(roleName);
        assertNotTheLastAdmin(user, roleName);

        if (STUDENT_ROLE.equals(roleName) && !studentRepository.existsByUserId(userId)) {
            Student student = Student.builder()
                    .user(user)
                    .build();
            studentRepository.save(student);
        }

        // Правка профиля администратором всегда проходит через выдачу роли, даже когда роль та же:
        // без этой проверки история пухла бы от «роль STUDENT» на каждом сохранении чужого профиля.
        boolean roleChanged = !roleName.equals(user.getRole().getName());
        user.setRole(role);
        userSessionService.updateUserAuthorities(user.getEmail());
        if (roleChanged) {
            activityService.roleAssigned(user, roleName, actor);
        }
        return userRepository.save(user);
    }

    /**
     * Не даёт снять ADMIN с последнего администратора, который может войти.
     *
     * <p>Вернуть роль было бы нечем: список {@code app.initial-admin-emails} действует только при
     * создании аккаунта (#17), так что единственным выходом остался бы SQL на проде — ровно то,
     * ради избавления от чего админка и делается.
     *
     * <p>Считаются только включённые (#45): отключённый администратор — и демо-аккаунт из V1.002 —
     * роль держит, но войти с ней не может, и преемником не является. По той же причине снять роль с
     * того, кто и так не может войти, можно всегда: это никого не лишает доступа.
     */
    private void assertNotTheLastAdmin(User user, String newRoleName) {
        if (!ADMIN_ROLE.equals(newRoleName) && isTheLastAdminWhoCanSignIn(user)) {
            throw new BusinessException(
                    "Это последний администратор: сначала назначьте другого, иначе выдать роль будет некому");
        }
    }

    private boolean isTheLastAdminWhoCanSignIn(User user) {
        return ADMIN_ROLE.equals(user.getRole().getName())
                && Boolean.TRUE.equals(user.getIsEnabled())
                && userRepository.countEnabledByRoleName(ADMIN_ROLE) <= 1;
    }

    /**
     * Второй путь к той же запертой двери (#45): роль остаётся, а войти с ней становится некому.
     * Выключают аккаунт трижды — отключением, правкой пользователя и правкой анкеты администратором, —
     * и проверка стоит на каждом из этих путей.
     */
    public void assertNotSwitchingOffTheLastAdmin(User user, Boolean nextEnabled) {
        if (!Boolean.TRUE.equals(nextEnabled) && isTheLastAdminWhoCanSignIn(user)) {
            throw new BusinessException(
                    "Это последний администратор, который может войти: без него администрировать будет некому");
        }
    }

    @Transactional
    public void deactivateUser(Long id, User actor) {
        User user = findByIdOrElseThrow(id);
        assertNotSwitchingOffTheLastAdmin(user, false);
        user.setIsEnabled(false);
        activityService.userDeactivated(user, actor);
        userRepository.save(user);
    }

}
