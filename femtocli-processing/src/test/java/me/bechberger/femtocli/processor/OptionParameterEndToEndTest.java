package me.bechberger.femtocli.processor;

import me.bechberger.femtocli.ParseException;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import me.bechberger.femtocli.annotations.Parameters;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * End-to-end test verifying generated parser works correctly.
 * Note: Tests only cover features that are implemented in the current code.
 */
public class OptionParameterEndToEndTest {
    @Command(name = "integration", description = {"Integration test command"})
    static class TestCommand {

        @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
        public boolean verbose = false;

        @Option(names = {"-o", "--output"}, description = "Output file path", paramLabel = "FILE")
        public String output = "";

        @Option(names = {"-c", "--count"}, description = "Number of iterations", defaultValue = "1", paramLabel = "N")
        public int count = 1;

        @Option(names = {"--force"}, description = "Force operation")
        public boolean force = false;

        @Option(names = {"-e", "--exclude"}, description = "Files to exclude", split = ",")
        public List<String> exclude;

        @Parameters(index = "0", description = "Input file", paramLabel = "INPUT", defaultValue = "")
        public String input;

        @Parameters(index = "1", description = "Optional secondary file", paramLabel = "SECONDARY", defaultValue = "")
        public String secondary = "";

        @Parameters(index = "2..*", description = "Additional files", paramLabel = "FILES")
        public List<String> extras;
    }

    @Test
    public void testGeneratedParserShortOptions() throws ParseException {
        String[] args = {"-v", "-o", "/tmp/out.txt"};
        TestCommand command = TestCommandParser.parse(args);
        assertTrue("verbose should be true", command.verbose);
        assertEquals("/tmp/out.txt", command.output);
    }

    @Test
    public void testGeneratedParserLongOptions() throws ParseException {
        String[] args = {"--verbose", "--output", "file.txt"};
        TestCommand command = TestCommandParser.parse(args);
        assertTrue("verbose should be true", command.verbose);
        assertEquals("file.txt", command.output);
    }

    @Test
    public void testGeneratedParserLongOptionsWithEquals() throws ParseException {
        String[] args = {"--output=file.txt"};
        TestCommand command = TestCommandParser.parse(args);
        assertEquals("file.txt", command.output);
    }

    @Test
    public void testGeneratedParserShortOptionsWithEquals() throws ParseException {
        String[] args = {"-o=file.txt"};
        TestCommand command = TestCommandParser.parse(args);
        assertEquals("file.txt", command.output);
    }

    @Test
    public void testGeneratedParserBooleanFlags() throws ParseException {
        String[] args = {"-v", "--force"};
        TestCommand command = TestCommandParser.parse(args);
        assertTrue("verbose should be true", command.verbose);
        assertTrue("force should be true", command.force);
    }

    @Test
    public void testGeneratedParserAppliesDefaultValues() throws ParseException {
        String[] args = {"-v"};
        TestCommand command = TestCommandParser.parse(args);
        assertTrue("verbose should be true", command.verbose);
        assertEquals("count should be default value", 1, command.count);
    }

    @Test
    public void testGeneratedParserCombinedShortOptions() throws ParseException {
        String[] args = {"-vc3"};
        TestCommand command = TestCommandParser.parse(args);
        assertTrue("verbose should be true", command.verbose);
        assertEquals("count should be 3", 3, command.count);
    }

    @Test
    public void testGeneratedParserLongOptionsWithValue() throws ParseException {
        String[] args = {"--output", "file.txt"};
        TestCommand command = TestCommandParser.parse(args);
        assertEquals("file.txt", command.output);
    }

    @Test
    public void testHelpGenerationDoesNotThrow() {
        TestCommandParser.printHelp();
        assertTrue(true);
    }

    @Test
    public void testMultiValueOptionWithSplit() throws ParseException {
        String[] args = {"--exclude", "file1.txt,file2.txt,file3.txt"};
        TestCommand command = TestCommandParser.parse(args);
        assertNotNull("exclude should not be null", command.exclude);
        assertEquals("exclude should contain 3 values", 3, command.exclude.size());
        assertEquals("file1.txt", command.exclude.get(0));
        assertEquals("file2.txt", command.exclude.get(1));
        assertEquals("file3.txt", command.exclude.get(2));
    }

    @Test
    public void testMultiValueOptionWithShortForm() throws ParseException {
        String[] args = {"-e", "a.txt,b.txt,c.txt"};
        TestCommand command = TestCommandParser.parse(args);
        assertNotNull("exclude should not be null", command.exclude);
        assertEquals("exclude should contain 3 values", 3, command.exclude.size());
        assertEquals(Arrays.asList("a.txt", "b.txt", "c.txt"), command.exclude);
    }

    @Test
    public void testMultiValueOptionWithEquals() throws ParseException {
        String[] args = {"--exclude=a.txt,b.txt,c.txt"};
        TestCommand command = TestCommandParser.parse(args);
        assertNotNull("exclude should not be null", command.exclude);
        assertEquals("exclude should contain 3 values", 3, command.exclude.size());
        assertEquals(Arrays.asList("a.txt", "b.txt", "c.txt"), command.exclude);
    }

    @Test
    public void testMultiValueOptionEmptySplit() throws ParseException {
        String[] args = {"--exclude", ""};
        TestCommand command = TestCommandParser.parse(args);
        assertNotNull("exclude should not be null", command.exclude);
        assertEquals("exclude should contain 1 empty value", 1, command.exclude.size());
        assertEquals("", command.exclude.get(0));
    }

    @Test
    public void testPositionalParameters() throws ParseException {
        String[] args = {"input.txt", "secondary.txt", "extra1.txt", "extra2.txt"};
        TestCommand command = TestCommandParser.parse(args);
        assertEquals("input.txt", command.input);
        assertEquals("secondary.txt", command.secondary);
        assertNotNull("extras should not be null", command.extras);
        assertEquals(2, command.extras.size());
        assertEquals("extra1.txt", command.extras.get(0));
        assertEquals("extra2.txt", command.extras.get(1));
    }

    @Test
    public void testPositionalParametersMixedWithOptions() throws ParseException {
        String[] args = {"-v", "-o", "out.txt", "input.txt", "secondary.txt", "extra.txt"};
        TestCommand command = TestCommandParser.parse(args);
        assertTrue(command.verbose);
        assertEquals("out.txt", command.output);
        assertEquals("input.txt", command.input);
        assertEquals("secondary.txt", command.secondary);
        assertNotNull("extras should not be null", command.extras);
        assertEquals(1, command.extras.size());
        assertEquals("extra.txt", command.extras.get(0));
    }

    @Test(expected = ParseException.class)
    public void testOptionAfterPositionalThrows() throws ParseException {
        String[] args = {"input.txt", "-v"};
        TestCommandParser.parse(args);
    }

    @Test
    public void testPositionalParametersWithDefaults() throws ParseException {
        String[] args = {"input.txt"};
        TestCommand command = TestCommandParser.parse(args);
        assertEquals("input.txt", command.input);
        assertEquals("", command.secondary); // default value
        assertNull("extras should be null if not provided", command.extras);
    }

    @Test(expected = ParseException.class)
    public void testUnknownOptionThrows() throws ParseException {
        String[] args = {"--unknown"};
        TestCommandParser.parse(args);
    }

    @Test(expected = ParseException.class)
    public void testInvalidIntThrows() throws ParseException {
        String[] args = {"-c", "not-an-int"};
        TestCommandParser.parse(args);
    }

    @Test(expected = ParseException.class)
    public void testMissingOptionValueThrows() throws ParseException {
        String[] args = {"-o"};
        TestCommandParser.parse(args);
    }

    @Test
    public void testParseExceptionMessage() {
        try {
            TestCommandParser.parse(new String[]{"--unknown"});
            fail("Should have thrown ParseException");
        } catch (ParseException e) {
            assertEquals("Unknown option: --unknown", e.getMessage());
        }
    }

    @Test
    public void testEndOfOptionsMarker() throws ParseException {
        String[] args = {"-v", "input.txt", "--", "--looks-like-option", "-also-option"};
        TestCommand command = TestCommandParser.parse(args);
        assertTrue("verbose should be true", command.verbose);
        assertEquals("input.txt", command.input);
        // Arguments after -- should be treated as positional, even if they look like options
        assertEquals("--looks-like-option", command.secondary);
        assertEquals("-also-option", command.extras.get(0));
    }

    @Test
    public void testEndOfOptionsMarkerWithOnlyPositionals() throws ParseException {
        String[] args = {"--", "arg1", "arg2", "arg3"};
        TestCommand command = TestCommandParser.parse(args);
        assertEquals("arg1", command.input);
        assertEquals("arg2", command.secondary);
        assertEquals(1, command.extras.size());
        assertEquals("arg3", command.extras.get(0));
    }
}
