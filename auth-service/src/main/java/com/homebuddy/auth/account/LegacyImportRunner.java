package com.homebuddy.auth.account;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The operator-only {@code import-legacy --file=users.csv} command. It exists only in import mode,
 * so the HTTP server (and with it any network access) is never started. Prints counts only.
 */
@Component
@ConditionalOnProperty(name = "auth.mode", havingValue = "import-legacy")
class LegacyImportRunner implements ApplicationRunner, ExitCodeGenerator {

    private static final int EXIT_USAGE = 2;
    private static final int EXIT_FAILURE = 1;

    private final LegacyImport legacyImport;
    private int exitCode;

    LegacyImportRunner(LegacyImport legacyImport) {
        this.legacyImport = legacyImport;
    }

    @Override
    public void run(ApplicationArguments args) {
        Path file = fileArgument(args);
        if (file == null) {
            System.err.println("Usage: import-legacy --file=<users.csv>");
            exitCode = EXIT_USAGE;
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            System.out.println("import-legacy: " + legacyImport.importFrom(reader).report());
        } catch (IOException e) {
            System.err.println("import-legacy: the import file cannot be read");
            exitCode = EXIT_FAILURE;
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    /** Accepts {@code --file=users.csv} and {@code --file users.csv}. */
    private static Path fileArgument(ApplicationArguments args) {
        List<String> values = args.getOptionValues("file");
        if (values != null && values.size() == 1 && !values.get(0).isBlank()) {
            return Path.of(values.get(0));
        }
        List<String> positional = args.getNonOptionArgs();
        if (values != null && values.isEmpty() && positional.size() == 2) {
            return Path.of(positional.get(1));
        }
        return null;
    }
}
