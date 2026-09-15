package ru.sfedu.teamselection.repository.specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import ru.sfedu.teamselection.domain.Student;
import ru.sfedu.teamselection.domain.Team;
import ru.sfedu.teamselection.domain.Technology;

@SuppressWarnings("checkstyle:MultipleStringLiterals")
public final class TeamSpecification {

    private TeamSpecification() {}

    public static Specification<Team> like(String text) {
        return (root, query, criteriaBuilder) -> {
            String pattern = ("%" + text.trim() + "%").toLowerCase(Locale.ROOT);
            return criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("projectDescription")), pattern)
            );
        };
    }

    public static Specification<Team> byTrack(Long trackId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("currentTrack").get("id"), trackId);
    }

    /**
     * Same rule as {@link ru.sfedu.teamselection.domain.TeamComposition#complete()}, evaluated in SQL:
     * members on course 1 and on course 2+ each reach the team override, otherwise the track target.
     */
    public static Specification<Team> byComplete(Boolean complete) {
        return (root, query, cb) -> {
            Expression<Integer> firstYearTarget = cb.coalesce(
                    root.<Integer>get("firstYearTarget"), root.get("currentTrack").<Integer>get("firstYearTarget"));
            Expression<Integer> secondYearTarget = cb.coalesce(
                    root.<Integer>get("secondYearTarget"), root.get("currentTrack").<Integer>get("secondYearTarget"));

            Predicate isComplete = cb.and(
                    cb.ge(countMembers(root, query, cb, true), firstYearTarget),
                    cb.ge(countMembers(root, query, cb, false), secondYearTarget)
            );
            return Boolean.TRUE.equals(complete) ? isComplete : cb.not(isComplete);
        };
    }

    private static Subquery<Long> countMembers(
            Root<Team> root, CriteriaQuery<?> query, CriteriaBuilder cb, boolean firstYears
    ) {
        Subquery<Long> count = query.subquery(Long.class);
        Root<Team> team = count.correlate(root);
        Join<Team, Student> member = team.join("students");
        Expression<Integer> course = member.get("course");
        count.select(cb.count(member)).where(
                firstYears ? cb.equal(course, 1) : cb.or(cb.isNull(course), cb.notEqual(course, 1))
        );
        return count;
    }

    public static Specification<Team> byProjectType(List<String> projectTypes) {
        return (root, query, criteriaBuilder) -> root.get("projectType").get("name").in(new ArrayList<>(projectTypes));
    }


    public static Specification<Team> byTechnologies(List<Long> technologies) {
        return (root, cq, cb) -> {
            if (technologies == null || technologies.isEmpty()) {
                return cb.conjunction();
            }
            cq.distinct(true);
            Join<Team, Technology> join = root.join("technologies", JoinType.LEFT);
            return join.get("id").in(technologies);
        };
    }

}
