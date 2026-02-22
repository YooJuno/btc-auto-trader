package com.btcautotrader.auth;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSettingsRepository extends JpaRepository<UserSettingsEntity, Long> {
}
