package com.homemedguard.common.contract;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** ① 药品字典只读契约。 */
public interface DrugQueryFacade {

    Optional<DrugBasicDTO> drug(Long drugDictId);

    List<DrugBasicDTO> drugs(Collection<Long> drugDictIds);
}
