package ru.sfedu.teamselection.service.security;


import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.stereotype.Service;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.service.UserService;

/**
 * Вход через не-OIDC провайдера. Живых таких нет — зарегистрирован только azure, а он OIDC, — но
 * цепочка на этот сервис всё ещё ссылается, поэтому он обязан заводить пользователя так же, как
 * основной путь: одна роль по умолчанию, один список начальных администраторов.
 */
@RequiredArgsConstructor
@Service
public class Oauth2UserService extends DefaultOAuth2UserService {

    private final UserService userService;

    @Override
    public User loadUser(OAuth2UserRequest userRequest) {
        var oAuth2User = super.loadUser(userRequest);
        // у гитхаба почта по умолчанию приватная, поэтому опознаём по логину
        String login = oAuth2User.getAttribute("login");
        return userService.findOrCreateByEmail(login, login, null);
    }
}
