package ru.sfedu.teamselection.controller;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Все мутирующие ручки приложения в виде «Controller#method» — основа fail-closed реестров окна
 * набора (#6) и передачи в кабинет ПД (#15).
 */
final class MutatingEndpoints {
    private static final String CONTROLLER_PACKAGE = "ru.sfedu.teamselection.controller";

    private static final Set<RequestMethod> MUTATIONS = Set.of(
            RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);

    private MutatingEndpoints() {
    }

    static Set<String> discover() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*Controller")));

        return scanner.findCandidateComponents(CONTROLLER_PACKAGE).stream()
                .map(BeanDefinition::getBeanClassName)
                .map(MutatingEndpoints::loadClass)
                .flatMap(controller -> Arrays.stream(controller.getDeclaredMethods())
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
        return mapping != null && Arrays.stream(mapping.method()).anyMatch(MUTATIONS::contains);
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
