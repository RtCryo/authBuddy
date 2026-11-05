package com.homebuddy.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class AuthServiceApplication {

    static final String IMPORT_COMMAND = "import-legacy";

    public static void main(String[] args) {
        if (args.length > 0 && IMPORT_COMMAND.equals(args[0])) {
            System.exit(runImport(args));
            return;
        }
        SpringApplication.run(AuthServiceApplication.class, args);
    }

    /**
     * Runs the operator-only legacy import without a web server and returns the process exit code.
     */
    static int runImport(String[] args) {
        SpringApplication application =
                new SpringApplicationBuilder(AuthServiceApplication.class)
                        .web(WebApplicationType.NONE)
                        .bannerMode(org.springframework.boot.Banner.Mode.OFF)
                        .properties("auth.mode=" + IMPORT_COMMAND)
                        .build();
        return SpringApplication.exit(application.run(args));
    }
}
