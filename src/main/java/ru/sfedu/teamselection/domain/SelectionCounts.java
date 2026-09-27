package ru.sfedu.teamselection.domain;

import java.util.List;

/**
 * Четыре числа, которыми набор описывается на графике (#49): сколько команд, сколько из них
 * собраны, сколько людей в командах и сколько всего зарегистрировалось.
 *
 * <p>Обзор считает то же самое через этот же класс: график и обзор не могут разойтись в том, что
 * такое «собрана» и «в команде».
 */
public record SelectionCounts(int totalTeams, int completeTeams, int studentsInTeams, int registered) {

    public static SelectionCounts of(List<Student> students, List<Team> teams) {
        int complete = (int) teams.stream()
                .filter(team -> TeamComposition.of(team).complete())
                .count();
        int inTeams = (int) students.stream()
                .filter(student -> Boolean.TRUE.equals(student.getHasTeam()))
                .count();
        return new SelectionCounts(teams.size(), complete, inTeams, students.size());
    }
}
