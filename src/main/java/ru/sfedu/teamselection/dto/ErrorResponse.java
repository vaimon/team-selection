package ru.sfedu.teamselection.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Getter
@Builder
public class ErrorResponse {
    /** Время ошибки в ISO-формате */
    private String timestamp;
    /** HTTP-код */
    private int status;
    /** Краткое описание статуса (например, "Bad Request") */
    private String error;
    /** Подробное сообщение исключения */
    private String message;
    /** Машинный код отказа у 409; у остальных ошибок его нет, и в ответ поле не попадает */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String code;
    /** Запрошенный путь */
    private String path;
}