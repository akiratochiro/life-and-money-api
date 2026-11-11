package com.akiratochiro.life_and_money_api.attachment;

import com.akiratochiro.life_and_money_api.shared.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping("/attachments/usage")
    @Operation(operationId = "attachmentUsage", summary = "Quanto da cota de 50 MB de arquivos o usuário já usou")
    public AttachmentUsage usage(@AuthenticationPrincipal Jwt jwt) {
        return attachmentService.usage(CurrentUser.id(jwt));
    }
}
