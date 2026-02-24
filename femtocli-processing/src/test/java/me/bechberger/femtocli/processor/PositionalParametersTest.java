package me.bechberger.femtocli.processor;

import me.bechberger.femtocli.ParseException;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Parameters;
import me.bechberger.femtocli.processor.OptionParameterEndToEndTest.TestCommand;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Tests for positional parameter parsing and type conversion.
 */
public class PositionalParametersTest {

    @Command(name = "positional-int", description = {"Test command for integer positional parameters"})
    static class PositionalIntCommand {
        @Parameters(index = "0", description = "Count value", paramLabel = "COUNT")
        public int count;

        @Parameters(index = "1..*", description = "Additional values", paramLabel = "VALUES")
        public java.util.List<String> extras;
    }

    @Test
    public void testIntegerPositionalParameter() throws ParseException {
        String[] args = {"42"};
        PositionalIntCommand command = PositionalIntCommandParser.parse(args);
        assertEquals(42, command.count);
        assertNull("extras should be null when not provided", command.extras);
    }

    @Test
    public void testIntegerPositionalWithVarargs() throws ParseException {
        String[] args = {"5", "extra1", "extra2", "extra3"};
        PositionalIntCommand command = PositionalIntCommandParser.parse(args);
        assertEquals(5, command.count);
        assertNotNull("extras should not be null", command.extras);
        assertEquals(3, command.extras.size());
        assertEquals(Arrays.asList("extra1", "extra2", "extra3"), command.extras);
    }

    @Test(expected = ParseException.class)
    public void testInvalidIntegerPositionalThrows() throws ParseException {
        String[] args = {"not-a-number"};
        PositionalIntCommandParser.parse(args);
    }

    @Test
    public void testInvalidIntegerPositionalErrorMessage() {
        try {
            PositionalIntCommandParser.parse(new String[]{"invalid"});
            fail("Should have thrown ParseException");
        } catch (ParseException e) {
            assertTrue("Error message should mention invalid integer", e.getMessage().toLowerCase().contains("invalid"));
            assertTrue("Error message should mention conversion", e.getMessage().toLowerCase().contains("integer"));
        }
    }

    @Test
    public void testSingleIndexParameter() throws ParseException {
        String[] args = {"input.txt"};
        TestCommand command = TestCommandParser.parse(args);
        assertEquals("input.txt", command.input);
    }

    @Test
    public void testMultipleIndexParameters() throws ParseException {
        String[] args = {"in.txt", "out.txt"};
        TestCommand command = TestCommandParser.parse(args);
        assertEquals("in.txt", command.input);
        assertEquals("out.txt", command.secondary);
    }

    @Test
    public void testVarargsWithRange1Star() throws ParseException {
        // TestCommand has input at index 0 and extras at index 2..*
        // So we need to provide index 1 for varargs to start capturing
        String[] args = {"main", "firstExtra", "extra1.txt", "extra2.txt"};
        TestCommand command = TestCommandParser.parse(args);
        assertEquals("main", command.input);
        assertEquals("firstExtra", command.secondary); // index 1
        assertNotNull("extras should not be null", command.extras);
        assertEquals(2, command.extras.size()); // extras start from index 2
        assertEquals("extra1.txt", command.extras.get(0));
        assertEquals("extra2.txt", command.extras.get(1));
    }

    @Test
    public void testOptionalRange0To1WithSingleArgument() throws ParseException {
        String[] args = {"single"};
        TestCommand command = TestCommandParser.parse(args);
        assertEquals("single", command.input);
        assertEquals("", command.secondary); // default value
    }

    @Test
    public void testOptionalRange0To1WithTwoArguments() throws ParseException {
        String[] args = {"first", "second"};
        TestCommand command = TestCommandParser.parse(args);
        assertEquals("first", command.input);
        assertEquals("second", command.secondary);
    }
}


