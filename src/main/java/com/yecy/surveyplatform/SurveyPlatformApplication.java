package com.yecy.surveyplatform;

import com.yecy.surveyplatform.thread.ServerThread;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SurveyPlatformApplication {

    public static void main(String[] args) {
        System.out.println("start Survey Platform...");
        new ServerThread().start();

        SpringApplication.run(SurveyPlatformApplication.class, args);
    }

}
