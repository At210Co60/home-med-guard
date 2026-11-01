package com.homemedguard;

import com.homemedguard.config.ModuleProperties;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ModuleProperties.class)
@MapperScan(basePackages = "com.homemedguard", annotationClass = Mapper.class)
public class HomeMedGuardApplication {

    public static void main(String[] args) {
        System.setProperty("user.timezone", "Asia/Shanghai");
        SpringApplication.run(HomeMedGuardApplication.class, args);
    }
}
