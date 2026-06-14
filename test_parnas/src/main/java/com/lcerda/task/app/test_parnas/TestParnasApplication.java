package com.lcerda.task.app.test_parnas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class TestParnasApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestParnasApplication.class, args);
    }

}
