package com.akiratochiro.life_and_money_api.auth;

import com.akiratochiro.life_and_money_api.user.User;
import com.akiratochiro.life_and_money_api.user.UserResponse;
import com.akiratochiro.life_and_money_api.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autenticação em duas peças:
 *
 * <ul>
 *   <li>um <b>access token</b> curto (JWT), devolvido no corpo e guardado só na memória do front;
 *   <li>um <b>refresh token</b> longo, que nunca passa pelo JavaScript: vai e volta num cookie
 *       {@code HttpOnly} restrito a {@code /auth}.
 * </ul>
 *
 * <p>Antes, o access token valia uma hora e ficava no {@code sessionStorage}: um XSS o lia, e
 * passada a hora o usuário era desconectado no meio do uso. Agora o token que importa não é
 * legível pela página, e o front renova sozinho enquanto a sessão estiver válida.
 */
@RestController
@RequestMapping("/auth")
@SecurityRequirements
public class AuthController {

    /**
     * Guarda contra CSRF. Em produção o cookie precisa ser {@code SameSite=None}, então o
     * navegador o enviaria também num POST disparado por outro site. Exigir um header fora da
     * lista segura do CORS obriga o navegador a fazer o preflight antes, e o preflight só passa
     * para as origens que a API permite: a requisição forjada não chega aqui.
     */
    private static final String REFRESH_REQUEST_HEADER = "X-Refresh-Request";

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenCookie refreshTokenCookie;

    public AuthController(UserService userService,
                          TokenService tokenService,
                          RefreshTokenService refreshTokenService,
                          RefreshTokenCookie refreshTokenCookie) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenCookie = refreshTokenCookie;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um usuário",
            description = "Não abre sessão: depois do cadastro, chame POST /auth/login.")
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request.name(), request.email(), request.password());
        return UserResponse.from(user);
    }

    @PostMapping("/login")
    @Operation(summary = "Abre uma sessão",
            description = "Devolve o access token no corpo e o refresh token num cookie HttpOnly.")
    public TokenResponse login(@Valid @RequestBody LoginRequest request,
                               HttpServletResponse response) {
        User user = userService.authenticate(request.email(), request.password());
        issueRefreshCookie(response, refreshTokenService.issue(user.getId()));
        return tokenService.generateToken(user);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renova o access token",
            description = "Lê o refresh token do cookie, rotaciona a sessão e devolve um access "
                    + "token novo. Exige o header " + REFRESH_REQUEST_HEADER + ".")
    public TokenResponse refresh(
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken,
            @RequestHeader(name = REFRESH_REQUEST_HEADER, required = false) String refreshRequestHeader,
            HttpServletResponse response) {

        requireRefreshRequestHeader(refreshRequestHeader);

        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken);
        issueRefreshCookie(response, rotation.nextToken());
        return tokenService.generateToken(userService.findById(rotation.userId()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Encerra a sessão",
            description = "Revoga o refresh token e apaga o cookie. Sem sessão ativa, não faz nada.")
    public void logout(
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken,
            HttpServletResponse response) {

        refreshTokenService.revoke(refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.expired().toString());
    }

    private void issueRefreshCookie(HttpServletResponse response, String value) {
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.of(value).toString());
    }

    private static void requireRefreshRequestHeader(String value) {
        if (value == null) {
            log.warn("Renovação sem o header {}: requisição recusada", REFRESH_REQUEST_HEADER);
            throw new InvalidRefreshTokenException();
        }
    }
}
