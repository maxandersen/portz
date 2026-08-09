package dk.xam.portz;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Instance;

import org.aesh.AeshRuntimeRunner;
import org.aesh.command.DefaultValueProvider;

import io.quarkus.aesh.runtime.AeshCdiCommandContainerBuilder;
import io.quarkus.aesh.runtime.AeshRuntimeRunnerFactory;

/**
 * Custom runner factory that preprocesses args before aesh parses them.
 * Rewrites a bare port number to --port, like jbang's handleDefaultRun().
 * This lets "portz 8080" work even though aesh group commands can't have
 * positional arguments.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class PortzRunnerFactory implements AeshRuntimeRunnerFactory {

    private static final Set<String> SUBCOMMANDS = Set.of("ps", "watch", "clean", "completion");

    private final Instance<DefaultValueProvider> defaultValueProvider;

    public PortzRunnerFactory(Instance<DefaultValueProvider> defaultValueProvider) {
        this.defaultValueProvider = defaultValueProvider;
    }

    @Override
    public AeshRuntimeRunner create() {
        var runner = AeshRuntimeRunner.builder()
                .containerBuilder(new AeshCdiCommandContainerBuilder<>())
                .command(new PortsCli());
        if (defaultValueProvider.isResolvable()) {
            runner.defaultValueProvider(defaultValueProvider.get());
        }
        return runner;
    }

    /**
     * Preprocess args: if first non-option arg is a number (port), rewrite to --port N.
     */
    public static String[] preprocessArgs(String[] args) {
        if (args == null || args.length == 0) return args;

        List<String> leadingOpts = new ArrayList<>();
        List<String> rest = new ArrayList<>();
        boolean foundParam = false;
        for (String arg : args) {
            if (!foundParam && arg.startsWith("-")) {
                leadingOpts.add(arg);
            } else {
                foundParam = true;
                rest.add(arg);
            }
        }

        if (rest.isEmpty()) return args;

        String first = rest.getFirst();
        if (SUBCOMMANDS.contains(first)) return args;

        // If it looks like a port number, rewrite to --port N
        try {
            int port = Integer.parseInt(first);
            var result = new ArrayList<>(leadingOpts);
            result.add("--port=" + port);
            result.addAll(rest.subList(1, rest.size())); // skip the port number
            return result.toArray(new String[0]);
        } catch (NumberFormatException _) {
            return args;
        }
    }
}
