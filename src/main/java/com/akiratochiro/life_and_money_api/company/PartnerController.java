package com.akiratochiro.life_and_money_api.company;

import com.akiratochiro.life_and_money_api.shared.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/companies/{companyId}/partners")
public class PartnerController {

    private final PartnerService partnerService;

    public PartnerController(PartnerService partnerService) {
        this.partnerService = partnerService;
    }

    @GetMapping
    @Operation(operationId = "listPartners", summary = "Sócios da empresa, você primeiro")
    public List<PartnerResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId) {
        return partnerService.list(CurrentUser.id(jwt), companyId).stream().map(PartnerResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "addPartner", summary = "Adiciona um sócio (o MEI não tem sócios)")
    public PartnerResponse add(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId,
                               @Valid @RequestBody PartnerRequest request) {
        return PartnerResponse.from(partnerService.add(CurrentUser.id(jwt), companyId, request.name()));
    }

    @PatchMapping("/{partnerId}")
    @Operation(operationId = "renamePartner")
    public PartnerResponse rename(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId,
                                  @PathVariable Long partnerId, @Valid @RequestBody PartnerRequest request) {
        return PartnerResponse.from(partnerService.rename(CurrentUser.id(jwt), companyId, partnerId, request.name()));
    }

    @DeleteMapping("/{partnerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "removePartner", summary = "Remove um sócio; você não pode se remover")
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId, @PathVariable Long partnerId) {
        partnerService.remove(CurrentUser.id(jwt), companyId, partnerId);
    }
}
