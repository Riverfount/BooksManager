package com.riverfount.booksmanager.user.adapter.in.web;

import com.riverfount.booksmanager.shared.adapter.in.web.AdminOnly;
import com.riverfount.booksmanager.user.application.port.in.CreateUserCommand;
import com.riverfount.booksmanager.user.application.port.in.CreateUserUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@AdminOnly
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Gestão dos usuários do sistema, só para administradores (RF09).")
class UserController {

    private final CreateUserUseCase createUserUseCase;

    UserController(CreateUserUseCase createUserUseCase) {
        this.createUserUseCase = createUserUseCase;
    }

    @PostMapping
    @Operation(summary = "Criar usuário",
            description = "Cria um usuário do sistema, só para administrador. O login é único (sem diferenciar "
                    + "maiúsculas) e a senha precisa cumprir a política: de 8 a 25 caracteres, com maiúscula, "
                    + "minúscula, número e caractere especial. A senha é guardada só como hash e a resposta nunca "
                    + "a traz.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuário criado"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422",
                    description = "Login repetido ou inválido, ou senha que não cumpre a política (a mensagem "
                            + "diz quais regras faltam)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        var user = createUserUseCase.create(new CreateUserCommand(request.login(), request.password(),
                request.role()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(user.getId())
                .toUri();
        return ResponseEntity.created(location).body(UserResponse.from(user));
    }
}
