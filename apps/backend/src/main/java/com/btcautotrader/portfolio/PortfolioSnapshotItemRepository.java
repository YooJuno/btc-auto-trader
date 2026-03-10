package com.btcautotrader.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortfolioSnapshotItemRepository extends JpaRepository<PortfolioSnapshotItemEntity, Long> {
    List<PortfolioSnapshotItemEntity> findBySnapshotIdOrderByCurrencyAsc(Long snapshotId);

    void deleteBySnapshotId(Long snapshotId);
}
