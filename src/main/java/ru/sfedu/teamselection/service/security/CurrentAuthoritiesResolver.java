package ru.sfedu.teamselection.service.security;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sfedu.teamselection.repository.StudentRepository;
import ru.sfedu.teamselection.repository.UserRepository;

/**
 * Roles for the current request, read from the DB rather than from the session: a registration or an admin
 * grant takes effect on the next request, without logging in again.
 */
@Service
@RequiredArgsConstructor
public class CurrentAuthoritiesResolver {
    /** Filled the participant questionnaire for the current selection. A state, not a stored role. */
    public static final String PARTICIPANT = "ROLE_PARTICIPANT";

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;

    /**
     * @return {@code ROLE_<db role>} plus {@link #PARTICIPANT} when registered for the active track;
     *         empty when the account is unknown
     */
    @Transactional(readOnly = true)
    public Optional<Set<GrantedAuthority>> resolve(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return userRepository.findByEmailFetchRole(email).map(user -> {
            Set<GrantedAuthority> authorities = new HashSet<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName()));
            if (studentRepository.existsByUserIdAndCurrentTrackActiveTrue(user.getId())) {
                authorities.add(new SimpleGrantedAuthority(PARTICIPANT));
            }
            return authorities;
        });
    }
}
