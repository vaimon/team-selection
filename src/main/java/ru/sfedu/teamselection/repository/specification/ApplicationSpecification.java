package ru.sfedu.teamselection.repository.specification;

import org.springframework.data.jpa.domain.Specification;
import ru.sfedu.teamselection.domain.application.Application;

public final class ApplicationSpecification {

    private ApplicationSpecification() {}

    public static Specification<Application> byTrack(Long trackId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("team").get("currentTrack").get("id"), trackId);
    }

    public static Specification<Application> byStatus(String status) {
        // Фильтр приходит из query-параметра, регистр которого мы не контролируем
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(criteriaBuilder.lower(root.get("status")), status.toLowerCase());
    }
}
