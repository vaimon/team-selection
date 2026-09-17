package ru.sfedu.teamselection.controller;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fail-closed реестр окна набора (#6).
 *
 * <p>Каждая мутирующая ручка обязана явно объявить, запирается ли она окном. Новая ручка, которой нет
 * ни в одном списке, роняет этот тест — незакрытый эндпоинт должен быть красным тестом, а не тихой
 * дырой. Это важно потому, что #9 (выход, исключение, передача капитанства, роспуск) и #13 (join-link)
 * добавят ровно те мутации, которые issue требует запирать, но приедут уже после этой задачи.
 */
class SelectionWindowCoverageTest {
    private static final String CONTROLLER_PACKAGE = "ru.sfedu.teamselection.controller";

    private static final Set<RequestMethod> MUTATIONS = Set.of(
            RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);

    /** Ручки, проходящие через SelectionWindowService.assertStudentMutationAllowed. */
    private static final Set<String> WINDOW_GUARDED = Set.of(
            "ApplicationController#createApplication",
            "ApplicationController#update",
            "TeamController#createTeam",
            "TeamController#updateTeam",
            "TeamController#removeMember",
            "TeamController#leaveTeam",
            "TeamController#transferCaptaincy",
            "TeamController#disbandTeam"
    );

    /** Ручки вне окна — с причиной, почему это осознанно. */
    private static final Map<String, String> EXEMPT = Map.ofEntries(
            Map.entry("ApplicationController#delete", "DELETE: только администратор (SecurityConfig)"),
            Map.entry("StudentController#createStudent", "регистрация и анкета доступны до открытия набора"),
            Map.entry("StudentController#updateStudent", "правка своего профиля окном не запирается"),
            Map.entry("StudentController#deleteStudent", "DELETE: только администратор (SecurityConfig)"),
            Map.entry("TeamController#deleteTeam", "DELETE: только администратор (SecurityConfig)"),
            Map.entry("TeamController#addStudentToTeam", "только администратор: он и разбирает составы после закрытия"),
            Map.entry("UserController#putUser", "правка своего профиля окном не запирается"),
            Map.entry("UserController#assignRole", "только администратор"),
            Map.entry("UserController#deleteUser", "DELETE: только администратор (SecurityConfig)"),
            Map.entry("TrackController#createTrack", "только администратор: настройка набора"),
            Map.entry("TrackController#updateTrack", "только администратор: настройка набора"),
            Map.entry("TrackController#deleteTrack", "только администратор: настройка набора"),
            Map.entry("TrackController#startNewSelection", "только администратор: сам открывает следующее окно"),
            Map.entry("ProjectTypeController#createProjectType", "только администратор: словарь"),
            Map.entry("ProjectTypeController#deleteProjectType", "только администратор: словарь"),
            Map.entry("TechnologyController#createTechnology", "только администратор: словарь"),
            Map.entry("TechnologyController#deleteTechnology", "только администратор: словарь")
    );

    @Test
    void everyMutatingEndpointDeclaresAWindowPolicy() {
        Set<String> declared = new TreeSet<>(WINDOW_GUARDED);
        declared.addAll(EXEMPT.keySet());

        assertThat(discoverMutatingEndpoints())
                .as("новая мутирующая ручка должна попасть либо в WINDOW_GUARDED, либо в EXEMPT с причиной")
                .isEqualTo(declared);
    }

    private static Set<String> discoverMutatingEndpoints() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(java.util.regex.Pattern.compile(".*Controller")));

        return scanner.findCandidateComponents(CONTROLLER_PACKAGE).stream()
                .map(BeanDefinition::getBeanClassName)
                .map(SelectionWindowCoverageTest::loadClass)
                .flatMap(controller -> java.util.Arrays.stream(controller.getDeclaredMethods())
                        .filter(method -> isMutation(controller, method))
                        .map(method -> controller.getSimpleName() + "#" + method.getName()))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * Маппинг может стоять как на самом методе, так и на сгенерированном из openapi.yaml интерфейсе,
     * который контроллер реализует, — ищем в обоих местах, иначе половина ручек не найдётся.
     */
    private static boolean isMutation(Class<?> controller, Method method) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (mapping == null) {
            Method interfaceMethod = ClassUtils.getInterfaceMethodIfPossible(method, controller);
            mapping = interfaceMethod.equals(method)
                    ? null
                    : AnnotatedElementUtils.findMergedAnnotation(interfaceMethod, RequestMapping.class);
        }
        return mapping != null && java.util.Arrays.stream(mapping.method()).anyMatch(MUTATIONS::contains);
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
