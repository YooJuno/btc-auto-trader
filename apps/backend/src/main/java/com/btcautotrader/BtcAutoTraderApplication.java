package com.btcautotrader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BtcAutoTraderApplication {
    public static void main(String[] args) {
        SpringApplication.run(BtcAutoTraderApplication.class, args);
    }
}
