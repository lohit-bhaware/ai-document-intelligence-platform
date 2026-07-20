package com.docai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.docai.config.StartupDiagnostics;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DocAiApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(DocAiApplication.class);
        app.addListeners(new StartupDiagnostics());
        app.run(args);
    }
}
