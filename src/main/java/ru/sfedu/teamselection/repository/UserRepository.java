package ru.sfedu.teamselection.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import ru.sfedu.teamselection.domain.Role;
import ru.sfedu.teamselection.domain.User;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    /**
     * Почта уникальна без учёта регистра (unique-индекс на lower(email)), поэтому и ищем так же:
     * иначе вход с другим написанием адреса не нашёл бы пользователя и пошёл создавать второго.
     */
    @Query("select u from User u where lower(u.email) = lower(?1)")
    Optional<User> findByEmail(String email);

    Optional<User> findByFio(String fio);

    @Transactional
    @Modifying
    @Query("update User u set u.role = ?1 where lower(u.email) = lower(?2)")
    void updateRoleByEmail(Role role, String email);

    @Query("select u from User u join fetch u.role where lower(u.email) = lower(?1)")
    Optional<User> findByEmailFetchRole(String email);

    long countByRoleName(String roleName);
}

