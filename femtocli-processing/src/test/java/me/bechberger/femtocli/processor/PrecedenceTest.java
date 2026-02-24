package me.bechberger.femtocli.processor;

import me.bechberger.femtocli.ParseException;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Mixin;
import me.bechberger.femtocli.annotations.Option;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for command option precedence over mixin options.
 */
public class PrecedenceTest {
    @Command(name = "precedence-test", description = {"Test command with option override"})
    static class PrecedenceTestCommand {
        @Mixin
        public MixinTest.CommonOptions commonOptions = new MixinTest.CommonOptions();

        @Option(names = {"-v", "--verbose"}, description = "Local verbose setting")
        public boolean verbose = false;
    }


    @Test
    public void testCommandOptionOverridesMixinOptionWithSameName() throws ParseException {
        String[] args = {"-v"};
        PrecedenceTestCommand command = PrecedenceTestCommandParser.parse(args);
        // The command's verbose field should be set (not the mixin's)
        assertTrue("verbose should be true", command.verbose);
        // The mixin's verbose should not be affected
        assertFalse("mixin verbose should be false", command.commonOptions.verbose);
        assertFalse("mixin quiet should be false", command.commonOptions.quiet);
    }

    @Test
    public void testMixinOptionsStillWorkWhenDifferentName() throws ParseException {
        String[] args = {"-q"};
        PrecedenceTestCommand command = PrecedenceTestCommandParser.parse(args);
        assertFalse("command verbose should be false", command.verbose);
        assertTrue("mixin quiet should be true", command.commonOptions.quiet);
    }
}

