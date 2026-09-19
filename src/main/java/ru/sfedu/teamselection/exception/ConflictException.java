package ru.sfedu.teamselection.exception;

import lombok.Getter;
import ru.sfedu.teamselection.enums.ConflictReason;

/**
 * Действие не противоречит правилам само по себе, но противоречит текущему состоянию. Отвечаем 409
 * с машинным кодом, чтобы клиент мог предложить следующий шаг.
 */
@Getter
public class ConflictException extends RuntimeException {
    private final ConflictReason code;

    public ConflictException(ConflictReason code, String message) {
        super(message);
        this.code = code;
    }
}
