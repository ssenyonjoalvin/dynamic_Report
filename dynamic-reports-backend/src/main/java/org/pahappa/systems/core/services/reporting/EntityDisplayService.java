package org.pahappa.systems.core.services.reporting;

import org.pahappa.systems.models.reporting.EntityDisplayConfig;
import org.pahappa.systems.reporting.support.GenericService;

import java.util.List;

public interface EntityDisplayService extends GenericService<EntityDisplayConfig> {

    List<EntityDisplayConfig> getAll();

    EntityDisplayConfig get(String entityClassName);

    /**
     * @return the configured display name for the given entity class, or null
     *         if none has been set.
     */
    String getDisplayName(String entityClassName);
}
