package com.example.ratingsystem.persistence;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GradingPersistenceProperties.class)
class PersistenceConfiguration {
}
