package com.btcautotrader.auth;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserOnboardingStateRepository extends JpaRepository<UserOnboardingStateEntity, Long> {
}
