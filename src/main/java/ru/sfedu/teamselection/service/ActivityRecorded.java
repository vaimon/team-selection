package ru.sfedu.teamselection.service;

/**
 * В историю легла запись о наборе {@code trackId} (#49). Публикуется внутри транзакции изменения;
 * слушатели, которым нужно уже случившееся изменение, берут её после коммита.
 */
public record ActivityRecorded(Long trackId) {
}
