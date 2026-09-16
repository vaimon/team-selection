package ru.sfedu.teamselection.service.security;


import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.exception.ForbiddenException;
import ru.sfedu.teamselection.service.UserService;

@RequiredArgsConstructor
@Service
public class AzureOidcUserService extends OidcUserService {

    private final UserService userService;


    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = super.loadUser(userRequest);

        String email = oidcUser.getAttribute("email");
        String name = oidcUser.getAttribute("name");
        String azureOid = oidcUser.getAttribute("oid");

        User user = userService.findOrCreateByEmail(email, name, azureOid);

        if (!user.isEnabled()) {
            throw new ForbiddenException(
                    "Аккаунт отключен. По вопросам возвращения доступа обращаться к администратору ресурса."
            );
        }
        // ****** вот здесь собираем authorities ******
        Set<GrantedAuthority> mappedAuthorities = new HashSet<>();

        // 1) все authority, которые пришли в токене (OIDC scopes и т.п.)
        mappedAuthorities.addAll(oidcUser.getAuthorities());

        // 2) добавляем роль из БД (Spring ожидает префикс "ROLE_")
        String roleName = user.getRole().getName();
        mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_" + roleName));

        // возвращаем DefaultOidcUser с новыми authorities
        return new DefaultOidcUser(
                mappedAuthorities,
                oidcUser.getIdToken(),
                oidcUser.getUserInfo()
        );
    }
}


