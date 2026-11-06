package io.github.nofortunateson.hevyinsights.repository;

import io.github.nofortunateson.hevyinsights.model.HevySyncState;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Table {@code hevy_sync_state}, one row per user. */
public interface HevySyncStateRepository extends JpaRepository<HevySyncState, UUID> {
}
