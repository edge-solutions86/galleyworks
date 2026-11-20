package io.github.rkaushikdev.galleyworks.cli;

import java.util.concurrent.Callable;

/** A command whose work returns an exit code and never fails with a checked exception. */
interface Subcommand extends Callable<Integer> {

    @Override
    Integer call();
}
