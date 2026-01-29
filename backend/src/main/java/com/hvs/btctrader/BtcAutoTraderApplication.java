package com.hvs.btctrader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.hvs.btctrader.config.AppProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class BtcAutoTraderApplication {

	public static void main(String[] args) {
		SpringApplication.run(BtcAutoTraderApplication.class, args);
	}

}
