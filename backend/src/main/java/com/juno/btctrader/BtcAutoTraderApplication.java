package com.juno.btctrader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.juno.btctrader.config.AppProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling
public class BtcAutoTraderApplication {

	public static void main(String[] args) {
		SpringApplication.run(BtcAutoTraderApplication.class, args);
	}

}
