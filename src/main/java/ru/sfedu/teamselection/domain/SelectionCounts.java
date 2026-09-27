package ru.sfedu.teamselection.domain;

import java.util.List;

/**
 * Четыре числа, которыми набор описывается на графике (#49): сколько команд, сколько из них
 * собраны, сколько людей в командах и сколько всего зарегистрировалось.
 */
public record SelectionCounts(int totalTeams, int completeTeams, int studentsInTeams, int registered) {

    public static SelectionCounts of(List<Student> students, List<Team> teams) {
        throw new UnsupportedOperationException("#49: not implemented yet");
    }
}
