package me.bechberger.femtocli.processor;

import me.bechberger.femtocli.ParseException;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import me.bechberger.femtocli.annotations.Parameters;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PositionalOrderTest {

    @Command(name = "positional-test")
    public static class PositionalTestCommand {
        @Option(names = "-o")
        public String option;

        @Parameters(index = "0")
        public String param;

        @Option(names = "-x")
        public String option2;
    }

    @Test(expected = ParseException.class)
    public void testOptionAfterPositional() {
        // Should throw ParseException because an option follows a positional parameter
        String[] args = {"paramValue", "-o", "optionValue"};
        TestCommandParser.parse(args);
    }
}
