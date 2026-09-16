package ru.sfedu.teamselection.service.validation;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.sfedu.teamselection.domain.Student;
import ru.sfedu.teamselection.domain.TeamComposition;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.domain.application.Application;
import ru.sfedu.teamselection.domain.application.ApplicationType;
import ru.sfedu.teamselection.domain.application.TeamRequest;
import ru.sfedu.teamselection.dto.application.ApplicationCreationDto;
import ru.sfedu.teamselection.enums.ApplicationStatus;
import ru.sfedu.teamselection.exception.BusinessException;
import ru.sfedu.teamselection.mapper.application.ApplicationMapper;
import ru.sfedu.teamselection.repository.ApplicationRepository;
import ru.sfedu.teamselection.service.StudentService;
import ru.sfedu.teamselection.service.TeamService;

@Component
@RequiredArgsConstructor
public class ApplicationValidator {
    private final StudentService studentService;
    private final TeamService teamService;

    private final ApplicationMapper applicationMapper;

    private final ApplicationRepository applicationRepository;

    @SuppressWarnings("checkstyle:ReturnCount")
    public ValidationResult validateCreate(ApplicationCreationDto dto, User sender, Boolean skipExistingCheck) {
        Application application = applicationRepository
                .findByTeamIdAndStudentId(dto.getTeamId(), dto.getStudentId())
                .orElse(null);
        if (application != null && !skipExistingCheck) {
            return new ValidationResult.Failure(
                    "Заявка или приглашение для этой команды уже существует — отправьте её повторно"
            );
        }
        var team = teamService.findByIdOrElseThrow(dto.getTeamId());
        var captain = studentService.findByIdOrElseThrow(team.getCaptainId());
        var student = studentService.findByIdOrElseThrow(dto.getStudentId());

        var applicationSender =  dto.getType().equals(ApplicationType.INVITE)
                ? captain
                : student;
        if (!applicationSender.getUser().getId().equals(sender.getId())) {
            return new ValidationResult.Forbidden("Вы не можете подать заявку от имени другого студента");
        }
        if (student.getHasTeam()) {
            return new ValidationResult.Failure("Студент уже состоит в команде");
        }


        if (!Boolean.TRUE.equals(team.getCurrentTrack().getActive())) {
            return new ValidationResult.Failure("Невозможно — набор, в котором создана эта команда, завершён");
        }
        if (student.getCurrentTrack() == null
                || !Objects.equals(student.getCurrentTrack().getId(), team.getCurrentTrack().getId())) {
            return new ValidationResult.Failure("Невозможно — студент не участвует в текущем наборе");
        }
        if (!TeamComposition.of(team).canJoin(student.getCourse())) {
            return new ValidationResult.Failure("Невозможно — " + TeamService.noPlacesMessage(student.getCourse()));
        }
        return new ValidationResult.Success();
    }

    @SuppressWarnings("checkstyle:ReturnCount")
    public ValidationResult validateUpdate(ApplicationStatus status, User requestSender, Application app) {
        if (app.status() == ApplicationStatus.ACCEPTED) {
            return new ValidationResult.Failure("Невозможно изменить статус принятой заявки");
        }

        switch (status) {
            case ACCEPTED -> {
                return validateAccept(requestSender, app);
            }
            case REJECTED -> {
                return validateReject(requestSender, app);
            }
            case CANCELLED -> {
                return validateCancel(requestSender, app);
            }
            case SENT -> {
                if (app.status() == ApplicationStatus.SENT) {
                    return new ValidationResult.Failure("Невозможно — заявка уже в статусе `Отправлена`");
                }
                return validateCreate(applicationMapper.mapToCreationDto(app), requestSender, true);
            }
            default -> throw new BusinessException("Неподдерживаемый статус заявки: " + status);
        }
    }

    private ValidationResult validateAccept(User requestSender, Application application) {
        ValidationResult senderIsTarget = validateSenderIsTarget(application, requestSender);
        if (!(senderIsTarget instanceof ValidationResult.Success)) {
            return senderIsTarget;
        }
        // Отклонённую или отменённую заявку оживляет только отправитель, повторно отправив её
        if (application.status() != ApplicationStatus.SENT) {
            return new ValidationResult.Failure("Невозможно одобрить — заявка в неподходящем статусе");
        }
        if (!TeamComposition.of(application.getTeam()).canJoin(application.getStudent().getCourse())) {
            return new ValidationResult.Failure(
                    "Невозможно одобрить — " + TeamService.noPlacesMessage(application.getStudent().getCourse()));
        }
        return new ValidationResult.Success();
    }

    private ValidationResult validateReject(User requestSender, Application application) {
        ValidationResult senderIsTarget = validateSenderIsTarget(application, requestSender);
        if (!(senderIsTarget instanceof ValidationResult.Success)) {
            return senderIsTarget;
        }
        if (application.status() != ApplicationStatus.SENT) {
            return new ValidationResult.Failure("Невозможно отклонить — заявка не статусе `Отправлена`");
        }
        return new ValidationResult.Success();
    }

    private ValidationResult validateCancel(User requestSender, Application application) {
        if (application.status() != ApplicationStatus.SENT) {
            return new ValidationResult.Failure(
                    "Заявку можно отменить только если она находится в статусе `Отправлена`"
            );
        }
        var sender = studentService.findByIdOrElseThrow(application.getSenderId());
        if (!sender.getUser().getId().equals(requestSender.getId())) {
            return new ValidationResult.Forbidden("Только отправитель может отменить заявку");
        }
        return new ValidationResult.Success();
    }

    private ValidationResult validateSenderIsTarget(Application application, User sender) {
        Student actualTarget = studentService.findByIdOrElseThrow(application.getTargetId());
        if (!actualTarget.getUser().getId().equals(sender.getId())) {
            return new ValidationResult.Forbidden(ONLY_TARGET_CAN_ACCEPT_ERROR);
        }
        // TODO возможно стоит сделать покрасивее?
        if (application instanceof TeamRequest) {
            if (actualTarget.getCurrentTeam() == null
                    || !actualTarget.getCurrentTeam().getId().equals(application.getTeam().getId())) {
                return new ValidationResult.Forbidden(ONLY_TARGET_CAN_ACCEPT_ERROR);
            }
        }
        return new ValidationResult.Success();
    }

    private static final String ONLY_TARGET_CAN_ACCEPT_ERROR = "Принять заявку может только ее адресат";
}

