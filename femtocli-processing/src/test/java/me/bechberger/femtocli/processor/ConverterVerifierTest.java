package me.bechberger.femtocli.processor;

import me.bechberger.femtocli.TypeConverter;
import me.bechberger.femtocli.Verifier;
import me.bechberger.femtocli.VerifierException;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import me.bechberger.femtocli.annotations.Parameters;
import org.junit.Test;

import java.util.Objects;

import static org.junit.Assert.assertEquals;

public class ConverterVerifierTest {

    public static class IntWrapper {
        public final int a;

        public IntWrapper(int a) {
            this.a = a;
        }

        @Override
        public String toString() {
            return "IntWrapper(" + a + ")";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            IntWrapper that = (IntWrapper) o;
            return a == that.a;
        }

        @Override
        public int hashCode() {
            return Objects.hash(a);
        }
    }

    public static class IntWrapperConverter implements TypeConverter<IntWrapper> {
        @Override
        public IntWrapper convert(String value) {
            return new IntWrapper(Integer.parseInt(value));
        }
    }

    public static class PortVerifier implements Verifier<Integer> {
        @Override
        public void verify(Integer value) throws VerifierException {
            if (value < 1 || value > 65535) {
                throw new VerifierException("Invalid port: " + value);
            }
        }
    }

    @Command(name = "conv-veri-test")
    public static class ConvVeriTestCommand {
        @Option(names = "-p", verifier = PortVerifier.class)
        public int port;

        @Option(names = "-w", converter = IntWrapperConverter.class)
        public IntWrapper wrapper;

        @Parameters(index = "0", converter = IntWrapperConverter.class)
        public IntWrapper paramWrapper;

        @Parameters(index = "1", verifier = PortVerifier.class)
        public int paramPort;

        @Option(names = "-m", converterMethod = "convertWrapper")
        public IntWrapper methodWrapper;

        @Option(names = "-v", verifierMethod = "verifyPort")
        public int methodPort;

        public IntWrapper convertWrapper(String value) {
            return new IntWrapper(Integer.parseInt(value) + 1);
        }

        public void verifyPort(int value) throws VerifierException {
            if (value < 1024) {
                throw new VerifierException("Port must be > 1024: " + value);
            }
        }

        public static IntWrapper staticConvertWrapper(String value) {
            return new IntWrapper(Integer.parseInt(value) + 2);
        }

        @Option(names = "-s", converterMethod = "me.bechberger.femtocli.processor.ConverterVerifierTest.ConvVeriTestCommand#staticConvertWrapper")
        public IntWrapper staticMethodWrapper;
    }

    @Test
    public void testConvertersAndVerifiers() throws Exception {
        String[] args = {"-p", "8080", "-w", "123", "-m", "10", "-v", "2000", "-s", "30", "456", "9090"};
        ConvVeriTestCommand cmd = ConvVeriTestCommandParser.parse(args);

        assertEquals(8080, cmd.port);
        assertEquals(new IntWrapper(123), cmd.wrapper);
        assertEquals(new IntWrapper(456), cmd.paramWrapper);
        assertEquals(9090, cmd.paramPort);
        assertEquals(new IntWrapper(11), cmd.methodWrapper);
        assertEquals(2000, cmd.methodPort);
        assertEquals(new IntWrapper(32), cmd.staticMethodWrapper);
    }

    @Test(expected = me.bechberger.femtocli.ParseException.class)
    public void testInvalidPortVerifier() throws Exception {
        String[] args = {"-p", "70000", "-w", "123", "8080", "456"};
        ConvVeriTestCommandParser.parse(args);
    }

    @Test(expected = me.bechberger.femtocli.ParseException.class)
    public void testInvalidMethodPortVerifier() throws Exception {
        String[] args = {"-p", "80", "-w", "123", "-v", "80", "456", "8080"};
        ConvVeriTestCommandParser.parse(args);
    }
}
