package me.bechberger.femtocli.processor;

import com.google.common.truth.StringSubject;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

public class MinimalCodegenTest {

    @Test
    public void testMinimalCommandHasNoExtraBoilerplate() {
        String commandSource = """
                package test;
                
                import me.bechberger.femtocli.annotations.Command;
                import me.bechberger.femtocli.annotations.Option;
                
                @Command(name = "test", description = {"Test command"})
                public class TestCommand {
                    @Option(names = {"-v", "--verbose"}, description = "Verbose mode")
                    public boolean verbose = false;
                }
                """;

        Compilation compilation = javac()
                .withProcessors(new CliProcessor())
                .compile(JavaFileObjects.forSourceString("test.TestCommand", commandSource));

        assertThat(compilation).succeeded();

        StringSubject stringSubject = assertThat(compilation).generatedSourceFile("test/TestCommandParser")
                .contentsAsUtf8String();
        stringSubject
                .doesNotContain("multiValueAccumulator");
        stringSubject.doesNotContain("isMultiValueOption");
        stringSubject.doesNotContain("getSplitDelimiter");
        stringSubject.doesNotContain("convertInt"); // Only boolean is used
        stringSubject.doesNotContain("convertString");
    }

    @Test
    public void testCommandWithMultiValueHasAccumulator() {
        String commandSource = """
                package test;
                
                import me.bechberger.femtocli.annotations.Command;
                import me.bechberger.femtocli.annotations.Option;
                import java.util.List;
                
                @Command(name = "test", description = {"Test command"})
                public class TestCommand {
                    @Option(names = {"-e", "--exclude"}, description = "Files to exclude", split = ",")
                    public List<String> exclude;
                }
                """;

        Compilation compilation = javac()
                .withProcessors(new CliProcessor())
                .compile(JavaFileObjects.forSourceString("test.TestCommand", commandSource));

        assertThat(compilation).succeeded();
        StringSubject stringSubject = assertThat(compilation).generatedSourceFile("test/TestCommandParser").contentsAsUtf8String();
        stringSubject.contains("multiValueAccumulator");
        stringSubject.contains("isMultiValueOption");
        stringSubject.contains("getSplitDelimiter");
    }

    @Test
    public void testCommandWithParametersHasPositionalArgs() {
        String commandSource = """
                package test;
                
                import me.bechberger.femtocli.annotations.Command;
                import me.bechberger.femtocli.annotations.Parameters;
                
                @Command(name = "test", description = {"Test command"})
                public class TestCommand {
                    @Parameters(index = "0", description = "Input file")
                    public String input;
                }
                """;
                
        Compilation compilation = javac()
                .withProcessors(new CliProcessor())
                .compile(JavaFileObjects.forSourceString("test.TestCommand", commandSource));
                
        assertThat(compilation).succeeded();
        StringSubject stringSubject = assertThat(compilation).generatedSourceFile("test/TestCommandParser").contentsAsUtf8String();
        stringSubject.contains("positionalArgs");
        stringSubject.contains("afterEndOfOptions");
        stringSubject.doesNotContain("seenPositional"); // only options AND parameters
        stringSubject.doesNotContain("multiValueAccumulator");
        stringSubject.doesNotContain("isMultiValueOption");
    }

    @Test
    public void testCommandWithNoOptionsHasNoOptionRelatedCode() {
        String commandSource = """
                package test;
                
                import me.bechberger.femtocli.annotations.Command;
                import me.bechberger.femtocli.annotations.Parameters;
                
                @Command(name = "test", description = {"Test command"})
                public class TestCommand {
                    @Parameters(index = "0", description = "Input file")
                    public String input;
                }
                """;
                
        Compilation compilation = javac()
                .withProcessors(new CliProcessor())
                .compile(JavaFileObjects.forSourceString("test.TestCommand", commandSource));
                
        assertThat(compilation).succeeded();
        StringSubject stringSubject = assertThat(compilation).generatedSourceFile("test/TestCommandParser").contentsAsUtf8String();
        stringSubject.doesNotContain("parsedOptions");
        stringSubject.doesNotContain("seenPositional");
        stringSubject.doesNotContain("getFieldNameForOption");
        stringSubject.doesNotContain("setOptionValue");
        stringSubject.doesNotContain("isBooleanOption");
        stringSubject.contains("positionalArgs");
    }
}
