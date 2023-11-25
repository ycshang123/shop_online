package com.soft2242.shop;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @author ycshang
 */
@MapperScan("com.soft2242.shop.mapper")
@SpringBootApplication
@EnableAsync
public class ShopOnlineApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopOnlineApplication.class, args);
    }

}
