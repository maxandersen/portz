package dk.xam.portz;

import jakarta.inject.Inject;

import org.aesh.AeshRuntimeRunner;
import org.aesh.command.CommandResult;

import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * Custom main that preprocesses args before aesh parses them.
 * Rewrites "portz 8080" to "portz --port 8080" so bare port numbers work
 * despite aesh group commands not supporting positional arguments.
 */
@QuarkusMain
public class PortzMain implements QuarkusApplication {

    @Inject
    PortzRunnerFactory factory;

    @Override
    public int run(String... args) {
        try {
            String[] processed = PortzRunnerFactory.preprocessArgs(args);
            AeshRuntimeRunner runner = factory.create();
            runner.args(processed);
            CommandResult result = runner.execute();
            return (result == null || result.isSuccess()) ? 0 : result.getExitCode();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }
}
