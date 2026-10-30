package com.akiratochiro.life_and_money_api.auth;

import com.akiratochiro.life_and_money_api.ratelimit.ClientIpResolver;
import com.akiratochiro.life_and_money_api.ratelimit.RateLimit;
import com.akiratochiro.life_and_money_api.ratelimit.RateLimiter;
import com.akiratochiro.life_and_money_api.user.User;
import com.akiratochiro.life_and_money_api.user.UserResponse;
import com.akiratochiro.life_and_money_api.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
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
     * Segunda barreira contra CSRF. A primeira é o {@code SameSite=Strict} do cookie, que o
     * navegador já não envia num POST disparado por outro site. O header cobre navegadores que
     * ignoram o SameSite: um header fora da lista segura do CORS obriga o preflight, e a API não
     * libera nenhuma origem, então a requisição forjada não chega aqui.
     */
    private static final String REFRESH_REQUEST_HEADER = "X-Refresh-Request";

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenCookie refreshTokenCookie;
    private final PasswordResetService passwordResetService;
    private final PasswordResetQueue passwordResetQueue;
    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    public AuthController(UserService userService,
                          TokenService tokenService,
                          RefreshTokenService refreshTokenService,
                          RefreshTokenCookie refreshTokenCookie,
                          PasswordResetService passwordResetService,
                          PasswordResetQueue passwordResetQueue,
                          RateLimiter rateLimiter,
                          ClientIpResolver clientIpResolver) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenCookie = refreshTokenCookie;
        this.passwordResetService = passwordResetService;
        this.passwordResetQueue = passwordResetQueue;
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um usuário",
            description = "Não abre sessão: depois do cadastro, chame POST /auth/login.")
    public UserResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        rateLimiter.consume(RateLimit.REGISTER_PER_IP, clientIpResolver.resolve(httpRequest));
        User user = userService.register(request.name(), request.email(), request.password());
        return UserResponse.from(user);
    }

    @PostMapping("/login")
    @Operation(summary = "Abre uma sessão",
            description = "Devolve o access token no corpo e o refresh token num cookie HttpOnly.")
    public TokenResponse login(@Valid @RequestBody LoginRequest request,
                               HttpServletRequest httpRequest,
                               HttpServletResponse response) {
        rateLimiter.consume(RateLimit.LOGIN_PER_IP, clientIpResolver.resolve(httpRequest));
        rateLimiter.consume(RateLimit.LOGIN_PER_EMAIL, request.email());
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
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        rateLimiter.consume(RateLimit.REFRESH_PER_IP, clientIpResolver.resolve(httpRequest));
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

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Pede um link para criar uma senha nova",
            description = "Responde sempre 202 com a mesma mensagem, exista ou não uma conta com o "
                    + "e-mail: a resposta não pode servir para descobrir quem tem cadastro. "
                    + "Quando a conta existe, o link sai por e-mail e vale por 30 minutos.")
    public ForgotPasswordResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                                 HttpServletRequest httpRequest) {
        // O limite é por IP, nunca por conta: um 429 que dependesse de a conta existir a revelaria.
        rateLimiter.consume(RateLimit.FORGOT_PASSWORD_PER_IP, clientIpResolver.resolve(httpRequest));
        passwordResetQueue.enqueue(request.email());
        return ForgotPasswordResponse.INSTANCE;
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cria uma senha nova com o token do e-mail",
            description = "O token vale uma vez só. A senha segue as regras do cadastro, e todas as "
                    + "sessões do usuário são encerradas: depois disso é preciso entrar de novo.")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
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
