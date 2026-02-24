package me.bechberger.femtocli.processor;

import me.bechberger.femtocli.ParseException;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Mixin;
import me.bechberger.femtocli.annotations.Option;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for mixin option inheritance.
 */
public class MixinTest {
    @Command(name = "mixin-test", description = {"Test command with mixin"})
    static class MixinTestCommand {
        @Mixin
        public CommonOptions commonOptions;

        @Option(names = {"-o", "--output"}, description = "Output file")
        public String output = "";
    }

    static class CommonOptions {
        @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
        public boolean verbose = false;

        @Option(names = {"-q", "--quiet"}, description = "Suppress non-error output")
        public boolean quiet = false;
    }


    @Test
    public void testMixinOptionsAvailable() throws ParseException {
        String[] args = {"-v", "--output", "file.txt"};
        MixinTestCommand command = MixinTestCommandParser.parse(args);
        assertTrue("verbose should be true", command.commonOptions.verbose);
        assertFalse("quiet should be false", command.commonOptions.quiet);
        assertEquals("file.txt", command.output);
    }

    @Test
    public void testQuietOptionFromMixin() throws ParseException {
        String[] args = {"-q"};
        MixinTestCommand command = MixinTestCommandParser.parse(args);
        assertTrue("quiet should be true", command.commonOptions.quiet);
        assertFalse("verbose should be false", command.commonOptions.verbose);
    }

    @Test
    public void testCombinedOptions() throws ParseException {
        String[] args = {"-vq", "-o", "out.txt"};
        MixinTestCommand command = MixinTestCommandParser.parse(args);
        assertTrue("verbose should be true", command.commonOptions.verbose);
        assertTrue("quiet should be true", command.commonOptions.quiet);
        assertEquals("out.txt", command.output);
    }

    @Test
    public void testHelpGenerationIncludesMixinOptions() {
        // This should not throw any exception
        MixinTestCommandParser.printHelp();
        assertTrue(true);
    }
}


