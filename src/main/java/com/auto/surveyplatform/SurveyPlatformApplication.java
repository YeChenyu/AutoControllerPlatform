package com.auto.surveyplatform;

import com.auto.surveyplatform.test.ServerThread;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.system.ApplicationHome;

@SpringBootApplication
public class SurveyPlatformApplication {

    public static void main(String[] args) {
        System.out.println("start Survey Platform...");
        new ServerThread().start();

        SpringApplication.run(SurveyPlatformApplication.class, args);
    }

}
