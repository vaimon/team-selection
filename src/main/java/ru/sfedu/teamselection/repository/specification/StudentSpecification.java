package ru.sfedu.teamselection.repository.specification;


import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import ru.sfedu.teamselection.domain.Student;
import ru.sfedu.teamselection.domain.Team;

public final class StudentSpecification {
    private StudentSpecification() {}

    public static Specification<Student> like(String text) {
        return (root, query, criteriaBuilder) -> {
            String pattern = ("%" + text.trim() + "%").toLowerCase(Locale.ROOT);
            return criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("user").get("fio")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("aboutSelf")), pattern)
            );
        };
    }


    public static Specification<Student> byCourse(List<Integer> course) {

        return (root, query, criteriaBuilder) -> {
            if (course == null || course.isEmpty()) {
                return criteriaBuilder.conjunction(); // do not filter if list is empty
            }
            return root.get("course").in(course);
        };
        /*
        return (root, query, criteriaBuilder) ->
        criteriaBuilder.equal(
                root.get("course"),
                course
        );*/
    }

    public static Specification<Student> byTrack(Long trackId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("currentTrack").get("id"), trackId);
    }

    public static Specification<Student> byGroup(List<Integer> group) {

        return (root, query, criteriaBuilder) -> {
            if (group == null || group.isEmpty()) {
                return criteriaBuilder.conjunction(); // do not filter if list is empty
            }
            return root.get("groupNumber").in(group);
        };
        /*
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(
                        root.get("groupNumber"),
                        group
                );*/
    }

    public static Specification<Student> byHasTeam(Boolean hasTeam) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(
                        root.get("hasTeam"),
                        hasTeam
                );
    }

    public static Specification<Student> byIsCaptain(Boolean isCaptain) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(
                        root.get("isCaptain"),
                        isCaptain
                );
    }

    /**
     * Студенты, у которых есть хотя бы одна из технологий.
     *
     * <p>Через подзапрос, а не через join (#42): join по {@code students_technologies} размножал
     * студента на каждую совпавшую технологию, это лечилось {@code distinct}, а Postgres не даёт
     * сочетать {@code select distinct} с сортировкой по колонке, которой нет в выборке, — сортировка
     * по умолчанию идёт по {@code users.fio}. Подзапрос оставляет по строке на студента, и
     * {@code distinct} внешнему запросу больше не нужен.
     */
    public static Specification<Student> hasTechnologies(List<Long> technologies) {
        return (root, query, criteriaBuilder) -> {
            if (technologies == null || technologies.isEmpty()) {
                return criteriaBuilder.conjunction(); // do not filter if list is empty
            }
            Subquery<Long> withTechnology = query.subquery(Long.class);
            Root<Student> student = withTechnology.from(Student.class);
            withTechnology.select(student.get("id"))
                    .where(student.join("technologies").get("id").in(technologies));
            return root.get("id").in(withTechnology);
        };
    }
}
