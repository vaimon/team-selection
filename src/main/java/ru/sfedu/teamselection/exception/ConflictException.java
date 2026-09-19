package ru.sfedu.teamselection.exception;

import lombok.Getter;
import ru.sfedu.teamselection.enums.BoardConflict;

/**
 * Действие не противоречит правилам само по себе, но противоречит текущему состоянию. Отвечаем 409
 * с машинным кодом, чтобы клиент мог предложить следующий шаг.
 */
@Getter
public class ConflictException extends RuntimeException {
    private final BoardConflict code;

    public ConflictException(BoardConflict code, String message) {
        super(message);
        this.code = code;
    }
}
