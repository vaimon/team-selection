package ru.sfedu.teamselection.dto.board;

/** Строка пула доски: студент набора без команды. */
public record BoardStudentRow(Long id, String name, Integer course, Integer groupNumber, Boolean isCaptain) {
}
