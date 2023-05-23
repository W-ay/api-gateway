package com.way.apigateway;

import java.util.Date;

import com.way.dubbointerface.service.GreetingsService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

//@Component
//public class Task implements CommandLineRunner {
//    @DubboReference
//    private GreetingsService greetingsService;
//
//    @Override
//    public void run(String... args) throws Exception {
//        String result = greetingsService.sayHi("world");
//        System.out.println("Receive result ======> " + result);
//
//        new Thread(()-> {
//            while (true) {
//                try {
//                    Thread.sleep(10000);
//                    System.out.println(new Date() + " Receive result ======> " + greetingsService.sayHi("world"));
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                    Thread.currentThread().interrupt();
//                }
//            }
//        }).start();
//    }
//}