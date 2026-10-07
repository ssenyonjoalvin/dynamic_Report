package org.pahappa.systems.core.services.reporting.impl;

import com.googlecode.genericdao.search.Search;
import org.pahappa.systems.core.services.reporting.EntityDisplayService;
import org.pahappa.systems.models.reporting.EntityDisplayConfig;
import org.pahappa.systems.reporting.support.GenericServiceImpl;
import org.pahappa.systems.reporting.support.Validate;
import org.sers.webutils.model.RecordStatus;
import org.sers.webutils.model.exception.OperationFailedException;
import org.sers.webutils.model.exception.ValidationFailedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service("kpiEntityDisplayService")
@Transactional
public class EntityDisplayServiceImpl extends GenericServiceImpl<EntityDisplayConfig> implements EntityDisplayService {

    @Override
    public boolean isDeletable(EntityDisplayConfig entityDisplayConfig) throws OperationFailedException {
        return true;
    }

    @Override
    public EntityDisplayConfig saveInstance(EntityDisplayConfig entityDisplayConfig) throws ValidationFailedException, OperationFailedException {
        Validate.notNull(entityDisplayConfig, "Entity display config cannot be null");
        Validate.hasText(entityDisplayConfig.getEntityClassName(), "An entity class name is required");
        return super.save(entityDisplayConfig);
    }

    @Override
    public List<EntityDisplayConfig> getAll() {
        Search search = new Search();
        search.addFilterEqual("recordStatus", RecordStatus.ACTIVE);
        search.addSortAsc("entityClassName");
        return super.search(search);
    }

    @Override
    public EntityDisplayConfig get(String entityClassName) {
        if (entityClassName == null) {
            return null;
        }
        Search search = new Search();
        search.addFilterEqual("recordStatus", RecordStatus.ACTIVE);
        search.addFilterEqual("entityClassName", entityClassName);
        return super.searchUnique(search);
    }

    @Override
    public String getDisplayName(String entityClassName) {
        EntityDisplayConfig config = get(entityClassName);
        if (config != null && config.getDisplayName() != null && !config.getDisplayName().trim().isEmpty()) {
            return config.getDisplayName();
        }
        return null;
    }
}
