package me.bechberger.femtocli.processor.codegen;

import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import me.bechberger.femtocli.ParseException;

import javax.lang.model.element.Modifier;
import java.util.Set;

/**
 * Generates type conversion helper methods for parser classes.
 * <p>
 * This class generates methods for converting string values to various
 * primitive types (boolean, int, long, float, double).
 */
public final class TypeConverterGenerator {

    private TypeConverterGenerator() {
        // Utility class
    }

    /**
     * Adds type conversion helper methods based on the types used in the command.
     *
     * @param classBuilder the class builder to add methods to
     * @param typeNames    the set of type names used in the command
     */
    public static void addTypeConversionMethods(TypeSpec.Builder classBuilder, Set<String> typeNames) {
        if (typeNames.contains("boolean") || typeNames.contains("java.lang.Boolean")) {
            addBooleanConversionMethod(classBuilder);
        }
        if (typeNames.contains("int") || typeNames.contains("java.lang.Integer")) {
            addIntegerConversionMethod(classBuilder);
        }
        if (typeNames.contains("long") || typeNames.contains("java.lang.Long")) {
            addLongConversionMethod(classBuilder);
        }
        if (typeNames.contains("float") || typeNames.contains("java.lang.Float")) {
            addFloatConversionMethod(classBuilder);
        }
        if (typeNames.contains("double") || typeNames.contains("java.lang.Double")) {
            addDoubleConversionMethod(classBuilder);
        }
    }

    /**
     * Adds boolean conversion method.
     */
    private static void addBooleanConversionMethod(TypeSpec.Builder classBuilder) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("convertBoolean")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(boolean.class)
                .addParameter(String.class, "value", Modifier.FINAL)
                .addException(ParseException.class);

        method.beginControlFlow("if (value == null || value.isEmpty())")
                .addStatement("return true")
                .endControlFlow();

        method.beginControlFlow("if (value.equalsIgnoreCase(\"true\") || value.equalsIgnoreCase(\"yes\") || value.equalsIgnoreCase(\"y\"))")
                .addStatement("return true")
                .endControlFlow();

        method.beginControlFlow("if (value.equalsIgnoreCase(\"false\") || value.equalsIgnoreCase(\"no\") || value.equalsIgnoreCase(\"n\"))")
                .addStatement("return false")
                .endControlFlow();

        method.addStatement("throw new $T(\"Invalid boolean value: \" + value + \" (expected true/false/yes/no)\")", ParseException.class);

        classBuilder.addMethod(method.build());
    }

    /**
     * Adds integer conversion method.
     */
    private static void addIntegerConversionMethod(TypeSpec.Builder classBuilder) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("convertInt")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(int.class)
                .addParameter(String.class, "value", Modifier.FINAL)
                .addException(ParseException.class);

        method.beginControlFlow("try")
                .addStatement("return $T.parseInt(value)", Integer.class)
                .nextControlFlow("catch ($T e)", NumberFormatException.class)
                .addStatement("throw new $T(\"Invalid integer value: \" + value, e)", ParseException.class)
                .endControlFlow();

        classBuilder.addMethod(method.build());
    }

    /**
     * Adds long conversion method.
     */
    private static void addLongConversionMethod(TypeSpec.Builder classBuilder) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("convertLong")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(long.class)
                .addParameter(String.class, "value", Modifier.FINAL)
                .addException(ParseException.class);

        method.beginControlFlow("try")
                .addStatement("return $T.parseLong(value)", Long.class)
                .nextControlFlow("catch ($T e)", NumberFormatException.class)
                .addStatement("throw new $T(\"Invalid long value: \" + value, e)", ParseException.class)
                .endControlFlow();

        classBuilder.addMethod(method.build());
    }

    /**
     * Adds float conversion method.
     */
    private static void addFloatConversionMethod(TypeSpec.Builder classBuilder) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("convertFloat")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(float.class)
                .addParameter(String.class, "value", Modifier.FINAL)
                .addException(ParseException.class);

        method.beginControlFlow("try")
                .addStatement("return $T.parseFloat(value)", Float.class)
                .nextControlFlow("catch ($T e)", NumberFormatException.class)
                .addStatement("throw new $T(\"Invalid float value: \" + value, e)", ParseException.class)
                .endControlFlow();

        classBuilder.addMethod(method.build());
    }

    /**
     * Adds double conversion method.
     */
    private static void addDoubleConversionMethod(TypeSpec.Builder classBuilder) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("convertDouble")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(double.class)
                .addParameter(String.class, "value", Modifier.FINAL)
                .addException(ParseException.class);

        method.beginControlFlow("try")
                .addStatement("return $T.parseDouble(value)", Double.class)
                .nextControlFlow("catch ($T e)", NumberFormatException.class)
                .addStatement("throw new $T(\"Invalid double value: \" + value, e)", ParseException.class)
                .endControlFlow();

        classBuilder.addMethod(method.build());
    }
}
