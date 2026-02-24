package me.bechberger.femtocli.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

/**
 * Test the CliProcessor annotation processor.
 */
public class CliProcessorTest {

    @Test
    public void testSimpleCommandGeneratesParser() {
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
        assertThat(compilation)
            .generatedSourceFile("test/TestCommandParser")
            .contentsAsUtf8String().contains("public final class TestCommandParser {");
    }

    @Test
    public void testUnknownOptionReportsError() {
        String commandSource = """
            package test;

            import me.bechberger.femtocli.annotations.Command;
            import me.bechberger.femtocli.annotations.Option;

            @Command(name = "test", description = {"Test command"})
            public class TestCommand {
                @Option(names = {"-v", "verbose"}, description = "Verbose mode")
                public boolean verbose = false;
            }
            """;

        Compilation compilation = javac()
            .withProcessors(new CliProcessor())
            .compile(JavaFileObjects.forSourceString("test.TestCommand", commandSource));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("Option name 'verbose' must start with '-' or '--'");
    }

    @Test
    public void testMultiValueOptionGeneratesParser() {
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
        assertThat(compilation)
            .generatedSourceFile("test/TestCommandParser")
            .contentsAsUtf8String().contains("multiValueAccumulator");
        assertThat(compilation)
            .generatedSourceFile("test/TestCommandParser")
            .contentsAsUtf8String().contains("isMultiValueOption");
    }

    @Test
    public void testMultiValueOptionRequiresCollectionType() {
        String commandSource = """
            package test;

            import me.bechberger.femtocli.annotations.Command;
            import me.bechberger.femtocli.annotations.Option;

            @Command(name = "test", description = {"Test command"})
            public class TestCommand {
                @Option(names = {"-e", "--exclude"}, description = "Files to exclude", split = ",")
                public String exclude;
            }
            """;

        Compilation compilation = javac()
            .withProcessors(new CliProcessor())
            .compile(JavaFileObjects.forSourceString("test.TestCommand", commandSource));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
            "@Option.split() can only be used with List, Set, or array types");
    }
}
