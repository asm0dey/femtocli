package me.bechberger.femtocli;

/**
 * Exception thrown when parsing command-line arguments fails at runtime.
 *
 * <p>This exception is used by generated parser classes to report errors
 * that occur during argument parsing, such as:</p>
 * <ul>
 *   <li>Missing required options</li>
 *   <li>Invalid option values</li>
 *   <li>Conversion failures</li>
 *   <li>Verification failures</li>
 * </ul>
 */
public class ParseException extends RuntimeException {

    /**
     * Creates a ParseException with the specified message.
     *
     * @param message The error message describing what went wrong
     */
    public ParseException(String message) {
        super(message);
    }

    /**
     * Creates a ParseException with the specified message and cause.
     *
     * @param message The error message describing what went wrong
     * @param cause   The underlying cause of the exception
     */
    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a ParseException for a missing required option.
     *
     * @param optionName The name of the missing option
     * @return A ParseException with a formatted error message
     */
    public static ParseException missingRequiredOption(String optionName) {
        return new ParseException("Missing required option: " + optionName);
    }

    /**
     * Creates a ParseException for an unknown option.
     *
     * @param optionName The name of the unknown option
     * @return A ParseException with a formatted error message
     */
    public static ParseException unknownOption(String optionName) {
        return new ParseException("Unknown option: " + optionName);
    }

    /**
     * Creates a ParseException for an unknown option with a suggestion.
     *
     * @param optionName  The name of the unknown option
     * @param suggestion  A suggested correct option name
     * @return A ParseException with a formatted error message
     */
    public static ParseException unknownOptionWithSuggestion(String optionName, String suggestion) {
        return new ParseException("Unknown option: " + optionName + ". Did you mean " + suggestion + "?");
    }

    /**
     * Creates a ParseException for an invalid value.
     *
     * @param optionName The name of the option
     * @param value      The invalid value
     * @param reason     The reason why the value is invalid
     * @return A ParseException with a formatted error message
     */
    public static ParseException invalidValue(String optionName, String value, String reason) {
        return new ParseException("Invalid value for " + optionName + ": " + value + " (" + reason + ")");
    }

    /**
     * Creates a ParseException for a conversion failure.
     *
     * @param optionName The name of the option
     * @param value      The value that could not be converted
     * @param targetType The target type name
     * @return A ParseException with a formatted error message
     */
    public static ParseException conversionFailure(String optionName, String value, String targetType) {
        return new ParseException("Cannot convert value for " + optionName + ": " + value + " (expected " + targetType + ")");
    }

    /**
     * Creates a ParseException for a verification failure.
     *
     * @param optionName The name of the option
     * @param value      The value that failed verification
     * @param reason     The reason why verification failed
     * @return A ParseException with a formatted error message
     */
    public static ParseException verificationFailure(String optionName, String value, String reason) {
        return new ParseException("Value verification failed for " + optionName + ": " + value + " (" + reason + ")");
    }

    /**
     * Creates a ParseException for an unknown subcommand.
     *
     * @param subcommandName The name of the unknown subcommand
     * @return A ParseException with a formatted error message
     */
    public static ParseException unknownSubcommand(String subcommandName) {
        return new ParseException("Unknown subcommand: " + subcommandName);
    }
}
