package com.coffeesprout;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine;

@TopCommand
@CommandLine.Command(name = "moxxie", mixinStandardHelpOptions = true, version = "1.0",
        subcommands = {
                DiscoverCommand.class,
                ListCommand.class,
                ProvisionCommand.class
                // DestroyCommand.class // Future implementation
        })
public class MainCLI implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
