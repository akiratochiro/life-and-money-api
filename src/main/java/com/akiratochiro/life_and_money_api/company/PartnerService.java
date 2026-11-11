package com.akiratochiro.life_and_money_api.company;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Sócios da empresa. O "sou eu" nasce com a empresa e não sai dela; os outros entram e saem por
 * aqui. A tela do MVP mostra só o "sou eu", mas a API aceita vários, porque a folha do Fator R
 * (Fase 4) soma os pró-labores de todos.
 */
@Service
public class PartnerService {

    private final PartnerRepository partnerRepository;
    private final CompanyRegimeRepository regimeRepository;
    private final CompanyService companyService;

    public PartnerService(PartnerRepository partnerRepository,
                          CompanyRegimeRepository regimeRepository,
                          CompanyService companyService) {
        this.partnerRepository = partnerRepository;
        this.regimeRepository = regimeRepository;
        this.companyService = companyService;
    }

    /** O "sou eu" primeiro, depois os outros na ordem em que entraram. */
    @Transactional(readOnly = true)
    public List<Partner> list(Long userId, Long companyId) {
        companyService.getOwned(userId, companyId);
        return partnerRepository.findAllByCompanyIdOrderByIdAsc(companyId);
    }

    /**
     * O MEI não tem sócios: é o empresário individual sozinho (pode ter um empregado, que não é
     * sócio). Vale o enquadramento mais recente, inclusive um agendado: uma mudança futura para
     * MEI também não pode encontrar sócios.
     */
    @Transactional
    public Partner add(Long userId, Long companyId, String name) {
        companyService.getActiveOwned(userId, companyId);
        CompanyRegime latest = regimeRepository.findByCompanyIdAndValidToIsNull(companyId).orElseThrow();
        if (latest.getLegalNature() == LegalNature.MEI) {
            throw new InvalidPartnerChangeException("O MEI não tem sócios além do titular.");
        }
        return partnerRepository.save(new Partner(userId, companyId, name.strip(), false));
    }

    @Transactional
    public Partner rename(Long userId, Long companyId, Long partnerId, String name) {
        companyService.getActiveOwned(userId, companyId);
        Partner partner = findIn(companyId, partnerId);
        partner.rename(name.strip());
        return partner;
    }

    @Transactional
    public void remove(Long userId, Long companyId, Long partnerId) {
        companyService.getActiveOwned(userId, companyId);
        Partner partner = findIn(companyId, partnerId);
        if (partner.isMe()) {
            throw new InvalidPartnerChangeException("Você não pode se remover da sua própria empresa.");
        }
        partnerRepository.delete(partner);
    }

    /** Para outras features: os sócios da empresa (que quem chama já conferiu ser do usuário). */
    @Transactional(readOnly = true)
    public List<Partner> listOf(Long companyId) {
        return partnerRepository.findAllByCompanyIdOrderByIdAsc(companyId);
    }

    /** Para outras features: o sócio da empresa (que quem chama já conferiu ser do usuário). */
    @Transactional(readOnly = true)
    public Partner get(Long companyId, Long partnerId) {
        return findIn(companyId, partnerId);
    }

    /** A empresa já foi conferida como do usuário; o sócio precisa ser dela. */
    private Partner findIn(Long companyId, Long partnerId) {
        return partnerRepository.findByIdAndCompanyId(partnerId, companyId)
                .orElseThrow(PartnerNotFoundException::new);
    }
}
