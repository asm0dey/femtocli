package me.bechberger.femtocli.processor;

import me.bechberger.femtocli.ParseException;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Mixin;
import me.bechberger.femtocli.annotations.Option;
import org.hamcrest.MatcherAssert;
import org.junit.Test;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.*;

/**
 * Tests for subcommand routing in generated parsers.
 */
public class SubcommandRoutingTest {

    // Common options class used by subcommands
    public static class SubCommonOptions {
        @Option(names = {"-v", "--verbose"})
        boolean verbose;
    }

    @Command(name = "buildcmd", description = {"Build the project"})
    public static class SubBuildCommand {
        @Mixin
        SubCommonOptions options = new SubCommonOptions();

        @Option(names = {"-o", "--output"})
        String output;
    }

    @Command(name = "testcmd", description = {"Run tests"})
    public static class SubTestCommand {
        @Mixin
        SubCommonOptions options = new SubCommonOptions();

        @Option(names = {"-f", "--fail-fast"})
        boolean failFast = false;
    }

    @Command(name = "root", subcommands = {SubBuildCommand.class, SubTestCommand.class})
    public static class RootCommand {
        @Option(names = {"-v", "--verbose"})
        boolean verbose;
    }

    @Test
    public void testRootOptionBeforeSubcommand() throws ParseException {
        String[] args = {"-v", "buildcmd", "-o", "out"};
        RootCommandParser.CommandParsingResult result = RootCommandParser.parse(args);
        assertNotNull(result);
        assertTrue("Should have subcommand", result.hasSubcommand());
        assertTrue("Should return BuildCommand", result.getSubcommand() instanceof SubBuildCommand);
        SubBuildCommand command = (SubBuildCommand) result.getSubcommand();
        assertEquals("output should be out", "out", command.output);
        assertTrue("Root should be verbose", result.getRootCommand().verbose);
    }


    @Test
    public void testFullParsing() throws ParseException {
        String[] args = {"--verbose", "buildcmd", "--output", "out"};
        RootCommandParser.CommandParsingResult result = RootCommandParser.parse(args);
        assertNotNull(result);
        assertTrue(result.getSubcommand() instanceof SubBuildCommand);
        SubBuildCommand sub = (SubBuildCommand) result.getSubcommand();
        assertEquals("out", sub.output);
        assertTrue(result.getRootCommand().verbose);
    }

    @Test
    public void testRootOptionBeforeSubcommandLong() throws ParseException {
        String[] args = {"--verbose", "buildcmd", "-o", "out"};
        RootCommand rootCommand = RootCommandParser.parseRoot(new String[]{"--verbose"});
        assertTrue(rootCommand.verbose);
        RootCommandParser.CommandParsingResult result = RootCommandParser.parse(args);
        assertNotNull(result);
        assertTrue("Should return BuildCommand", result.getSubcommand() instanceof SubBuildCommand);
        assertEquals("output should be out", "out", ((SubBuildCommand) result.getSubcommand()).output);
    }

    @Test
    public void testRouteToBuildSubcommand() throws ParseException {
        String[] args = {"buildcmd", "-v", "--output", "dist/"};
        RootCommandParser.CommandParsingResult result = RootCommandParser.parse(args);
        assertNotNull(result);
        assertTrue("Should return BuildCommand", result.getSubcommand() instanceof SubBuildCommand);
        SubBuildCommand command = (SubBuildCommand) result.getSubcommand();
        assertTrue("verbose should be true", command.options.verbose);
        assertEquals("output should be dist/", "dist/", command.output);
    }

    @Test
    public void testRouteToTestSubcommand() throws ParseException {
        String[] args = {"testcmd", "-v", "-f"};
        RootCommandParser.CommandParsingResult result = RootCommandParser.parse(args);
        assertNotNull(result);
        assertTrue("Should return TestCommand", result.getSubcommand() instanceof SubTestCommand);
        SubTestCommand command = (SubTestCommand) result.getSubcommand();
        assertTrue("failFast should be true", command.failFast);
    }

    @Test
    public void testRootCommandWithoutSubcommand() throws ParseException {
        String[] args = {};
        RootCommandParser.CommandParsingResult result = RootCommandParser.parse(args);
        assertNotNull(result);
        assertNotNull("Should return RootCommand", result.getRootCommand());
        assertFalse("Should not have subcommand", result.hasSubcommand());
    }

    @Test(expected = ParseException.class)
    public void testUnknownSubcommandThrows() throws ParseException {
        String[] args = {"unknown"};
        RootCommandParser.parse(args);
    }

    @Test
    public void testUnknownSubcommandErrorMessage() {
        try {
            RootCommandParser.parse(new String[]{"unknown"});
            fail("Should have thrown ParseException");
        } catch (ParseException e) {
            assertEquals("Unknown subcommand: unknown", e.getMessage());
        }
    }

    @Test
    public void testSubcommandWithNoOptions() throws ParseException {
        String[] args = {"buildcmd"};
        RootCommandParser.CommandParsingResult result = RootCommandParser.parse(args);
        assertNotNull(result);
        assertTrue("Should return BuildCommand", result.getSubcommand() instanceof SubBuildCommand);
        SubBuildCommand command = (SubBuildCommand) result.getSubcommand();
        assertFalse("verbose should be false", command.options.verbose);
        assertNull("output should be null", command.output);
    }

    @Test
    public void testBuildHelp() {
        // Test that subcommand parser has getHelpText() method and it returns meaningful help
        String helpText = SubBuildCommandParser.getHelpText();
        assertNotNull("Help text should not be null", helpText);
        assertTrue("Help should contain command name", helpText.contains("Usage: buildcmd"));
        assertTrue("Help should contain description", helpText.contains("Build"));
        assertTrue("Help should contain verbose option", helpText.contains("-v") || helpText.contains("--verbose"));
        assertTrue("Help should contain output option", helpText.contains("-o") || helpText.contains("--output"));
    }

    @Test
    public void testTestHelp() {
        // Test that subcommand parser has getHelpText() method and it returns meaningful help
        String helpText = SubTestCommandParser.getHelpText();
        assertNotNull("Help text should not be null", helpText);
        assertTrue("Help should contain command name", helpText.contains("Usage: testcmd"));
        assertTrue("Help should contain description", helpText.contains("Run tests"));
        assertTrue("Help should contain verbose option", helpText.contains("-v") || helpText.contains("--verbose"));
        assertTrue("Help should contain fail-fast option", helpText.contains("-f") || helpText.contains("--fail-fast"));
    }

    @Test
    public void testRootHelp() {
        // Test that root parser has getHelpText() method with subcommands
        String helpText = RootCommandParser.getHelpText();
        assertNotNull("Help text should not be null", helpText);
        assertTrue("Help should contain command name", helpText.contains("Usage: root"));
        assertTrue("Help should contain subcommands section", helpText.contains("Subcommands:"));
        assertTrue("Help should list buildcmd", helpText.contains("buildcmd"));
        assertTrue("Help should list testcmd", helpText.contains("testcmd"));
    }

    @Test
    public void testRootHelpViaParse() throws ParseException {
        // Test that --help on root command returns null (after printing help)
        String[] args = {"--help"};
        RootCommandParser.CommandParsingResult result = RootCommandParser.parse(args);
        assertNull("Help on root should return null", result);
    }

    @Test
    public void testSubcommandHelpViaParse() throws ParseException {
        // Test that --help on subcommand returns the subcommand instance
        // (help is printed via getHelpText() separately)
        String[] args = {"buildcmd", "--help"};
        RootCommandParser.CommandParsingResult result = RootCommandParser.parse(args);
        assertNotNull("Help on subcommand should return result", result);
        assertTrue("Should return BuildCommand in subcommand", result.getSubcommand() instanceof SubBuildCommand);
    }

    @Test
    public void testSubcommandHelpIsGeneratedCorrectly() {
        // Test that subcommand parsers also generate help with subcommands section
        // if they have subcommands themselves
        String rootHelp = RootCommandParser.getHelpText();
        assertTrue("Root help should list subcommands", rootHelp.contains("Subcommands:"));
        assertTrue("Root help should list buildcmd", rootHelp.contains("buildcmd"));
        assertTrue("Root help should list testcmd", rootHelp.contains("testcmd"));
        MatcherAssert.assertThat("Root help should contain subcommand descriptions",  rootHelp, equalTo("""
                Usage: root
                
                
                Subcommands:
                  buildcmd
                
                  testcmd
                
                """));
    }

    @Test
    public void testSubCommandsHelpGeneratsCorrectly(){
        MatcherAssert.assertThat("buildcmd outputs correct help", SubBuildCommandParser.getHelpText(), equalTo("""
                Usage: buildcmd
                Build the project
                
                Options:
                  -v, --verbose <VERBOSE>
                
                  -o, --output <OUTPUT>
                
                """));
    }
}
