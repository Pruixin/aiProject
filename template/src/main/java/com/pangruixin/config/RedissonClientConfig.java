//package com.pangruixin.config;
//
//import org.redisson.Redisson;
//import org.redisson.api.RedissonClient;
//import org.redisson.config.Config;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//class RedissonClientConfig {
//    // 配置 RedissonClient 以使用分布式锁
//    @Bean
//    public RedissonClient redissonClient() {
//        Config config = new Config();
//        config.useSingleServer().setAddress("redis://192.168.44.137:6379")
//                                .setPassword("Xin3209273024");  // 配置 Redis 地址
//        return Redisson.create(config);
//    }
//}
