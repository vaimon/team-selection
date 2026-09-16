package ru.sfedu.teamselection.service.security;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import ru.sfedu.teamselection.domain.Role;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.exception.ForbiddenException;
import ru.sfedu.teamselection.service.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AzureOidcUserServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private Oauth2UserService oauth2UserService;


    private AzureOidcUserService underTest;

    @BeforeEach
    void setUp() {
        underTest = new AzureOidcUserService(userService);

        underTest.setOauth2UserService(oauth2UserService);
    }

    @AfterEach
    void reset() {
        Mockito.reset(oauth2UserService);
    }

    @Test
    void loadUser_shouldReturnUserWithCombinedAuthoritiesWhenUserExists() {
        // Given
        OidcUserRequest userRequest = createMockUserRequest();
        Role userRole = Role.builder()
                .id(1L)
                .name("STUDENT")
                .build();

        var user = spy(User.builder()
                .id(1L)
                .fio("John Doe")
                .email("john.doe@example.com")
                .role(userRole)
                .build());
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("email", "john.doe@example.com");
        attributes.put("name", "John Doe");
        attributes.put("oid", "azure-oid-123");
        attributes.put("sub", "sub");
        doReturn(
                attributes
        ).when(user).getAttributes();
        doReturn(
                user
        ).when(oauth2UserService).loadUser(any());

        User existingUser = User.builder()
                .id(1L)
                .fio("John Doe")
                .email("john.doe@example.com")
                .azureId("azure-oid-123")
                .isEnabled(true)
                .role(userRole)
                .build();

        when(userService.findOrCreateByEmail("john.doe@example.com", "John Doe", "azure-oid-123"))
                .thenReturn(existingUser);

        // When
        OidcUser result = underTest.loadUser(userRequest);

        // Then
        assertThat(result).isInstanceOf(DefaultOidcUser.class);
        assertThat(result.getAuthorities()).hasSize(2); // 1 from OIDC + 1 from role

        // Verify OIDC authorities are present
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("OIDC_USER");

        // Verify role authority is present
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_STUDENT");

        // Verify user attributes
        Assertions.assertEquals("john.doe@example.com", result.getAttribute("email"));
        Assertions.assertEquals("John Doe", result.getAttribute("name"));
        Assertions.assertEquals("azure-oid-123", result.getAttribute("oid"));
    }

    @Test
    void loadUser_shouldCreateNewUserWhenUserDoesNotExist() {
        // Given
        OidcUserRequest userRequest = createMockUserRequest();
        Role defaultRole = Role.builder()
                .id(1L)
                .name("STUDENT")
                .build();
        var user = spy(User.builder()
                .id(1L)
                .fio("John Doe")
                .email("john.doe@example.com")
                .role(defaultRole)
                .build());
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("email", "john.doe@example.com");
        attributes.put("name", "John Doe");
        attributes.put("oid", "azure-oid-123");
        attributes.put("sub", "sub");
        doReturn(
                attributes
        ).when(user).getAttributes();
        doReturn(
                user
        ).when(oauth2UserService).loadUser(any());


        User newUser = User.builder()
                .id(1L)
                .fio("John Doe")
                .email("john.doe@example.com")
                .azureId("azure-oid-123")
                .isEnabled(true)
                .role(defaultRole)
                .build();

        when(userService.findOrCreateByEmail("john.doe@example.com", "John Doe", "azure-oid-123"))
                .thenReturn(newUser);

        // When
        OidcUser result = underTest.loadUser(userRequest);

        // Then
        assertThat(result).isInstanceOf(DefaultOidcUser.class);

        // Verify user creation
        verify(userService).findOrCreateByEmail("john.doe@example.com", "John Doe", "azure-oid-123");

        // Verify authorities include role
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_STUDENT");
    }

    @Test
    void loadUser_shouldThrowForbiddenExceptionWhenUserIsDisabled() {
        // Given
        OidcUserRequest userRequest = createMockUserRequest();
        Role userRole = Role.builder()
                .id(1L)
                .name("STUDENT")
                .build();
        var user = spy(User.builder()
                .id(1L)
                .fio("John Doe")
                .email("john.doe@example.com")
                .role(userRole)
                .build());
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("email", "john.doe@example.com");
        attributes.put("name", "John Doe");
        attributes.put("oid", "azure-oid-123");
        attributes.put("sub", "sub");
        doReturn(
                attributes
        ).when(user).getAttributes();
        doReturn(
                user
        ).when(oauth2UserService).loadUser(any());

        User disabledUser = User.builder()
                .id(1L)
                .fio("John Doe")
                .email("john.doe@example.com")
                .azureId("azure-oid-123")
                .isEnabled(false)
                .role(userRole)
                .build();

        when(userService.findOrCreateByEmail("john.doe@example.com", "John Doe", "azure-oid-123"))
                .thenReturn(disabledUser);

        // When & Then
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> underTest.loadUser(userRequest));

        assertThat(exception.getMessage())
                .isEqualTo("Аккаунт отключен. По вопросам возвращения доступа обращаться к администратору ресурса.");
    }

//
//    @Test
//    void loadUser_shouldHandleNullOidcUserAttributesGracefully() {
//        // Given
//        OidcUserRequest userRequest = createMockUserRequest();
//        var user = spy(User.builder()
//                .id(1L)
//                .fio("John Doe")
//                .email("john.doe@example.com")
//                .role(Role.builder().id(1L).name("ADMIN").build())
//                .build());
//        Map<String, Object> attributes = new HashMap<>();
//        attributes.put("email", "john.doe@example.com");
//        attributes.put("name", "John Doe");
//        attributes.put("oid", "azure-oid-123");
//        attributes.put("sub", "sub");
//        doReturn(
//                attributes
//        ).when(user).getAttributes();
//        doReturn(
//                user
//        ).when(oauth2UserService).loadUser(any());
//
//        // Create OIDC user with null attributes
//        Map<String, Object> emptyAttributes = new HashMap<>();
//        OidcIdToken idToken = OidcIdToken.withTokenValue("token")
//                .issuedAt(Instant.now())
//                .expiresAt(Instant.now().plusSeconds(3600))
//                .subject("subject")
//                .build();
//
//        Role defaultRole = Role.builder()
//                .id(1L)
//                .name("STUDENT")
//                .build();
//
//        User newUser = User.builder()
//                .id(1L)
//                .fio("null")
//                .email("example@wxample.com")
//                .azureId(null)
//                .isEnabled(true)
//                .role(defaultRole)
//                .build();
//
//        when(userRepository.findByEmailFetchRole(any()))
//                .thenReturn(Optional.empty());
//        when(roleRepository.findById(1L))
//                .thenReturn(Optional.of(defaultRole));
//        when(userRepository.save(any(User.class)))
//                .thenReturn(newUser);
//
//        // When
//        OidcUser result = underTest.loadUser(userRequest);
//
//        // Then
//        assertThat(result).isNotNull();
//        verify(userRepository).save(any(User.class));
//    }

    // Helper method to create mock OidcUserRequest
    private OidcUserRequest createMockUserRequest() {
        ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("azure")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationUri("https://login.microsoftonline.com/common/oauth2/v2.0/authorize")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .tokenUri("https://login.microsoftonline.com/common/oauth2/v2.0/token")
                .redirectUri("http://localhost:8080/login/oauth2/code/azure")
                .userInfoUri("https://login.microsoftonline.com/common/oauth2/v2.0/user-info")
                .scope("openid", "profile", "email")
                .build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "access-token",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );

        OidcIdToken oidcIdToken = new OidcIdToken(
                "tokenValue",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of(
                        "sub", "sub"
                )
        );

        return new OidcUserRequest(clientRegistration, accessToken, oidcIdToken);
    }
}
