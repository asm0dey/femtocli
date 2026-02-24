package me.bechberger.femtocli.processor.model;

import me.bechberger.femtocli.annotations.Command;

import javax.lang.model.element.TypeElement;
import java.util.*;

/**
 * Model representing metadata for a @Command annotated class.
 *
 * <p>Contains all information needed to generate a parser class:
 * command name, description, options, parameters, subcommands, and mixins.</p>
 */
public class CommandModel {

    private final TypeElement element;
    private final Command annotation;
    private final String name;
    private final String packageName;
    private final List<OptionModel> options = new ArrayList<>();
    private final List<ParameterModel> parameters = new ArrayList<>();
    private final List<MixinModel> mixins = new ArrayList<>();
    private final List<SubcommandModel> subcommands = new ArrayList<>();
    private final List<MethodSubcommandModel> methodSubcommands = new ArrayList<>();

    /**
     * Creates a CommandModel from a @Command annotated element.
     */
    public CommandModel(TypeElement element, Command annotation, String packageName) {
        this.element = element;
        this.annotation = annotation;
        this.name = annotation.name();
        this.packageName = packageName;
    }

    /**
     * Returns the TypeElement for this command class.
     */
    public TypeElement getElement() {
        return element;
    }

    /**
     * Returns the command name.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the command description lines.
     */
    public String[] getDescription() {
        return annotation.description();
    }

    /**
     * Returns the header text.
     */
    public String[] getHeader() {
        return annotation.header();
    }

    /**
     * Returns the custom synopsis.
     */
    public String[] getCustomSynopsis() {
        return annotation.customSynopsis();
    }

    /**
     * Returns the version string.
     */
    public String getVersion() {
        return annotation.version();
    }

    /**
     * Returns whether to add standard help options (-h/--help, -V/--version).
     */
    public boolean isMixinStandardHelpOptions() {
        return annotation.mixinStandardHelpOptions();
    }

    /**
     * Returns the empty line after usage flag.
     */
    public boolean isEmptyLineAfterUsage() {
        return annotation.emptyLineAfterUsage();
    }

    /**
     * Returns the empty line after description flag.
     */
    public boolean isEmptyLineAfterDescription() {
        return annotation.emptyLineAfterDescription();
    }

    /**
     * Returns the show default values in help setting.
     */
    public Command.ShowDefaultValuesInHelp getShowDefaultValuesInHelp() {
        return annotation.showDefaultValuesInHelp();
    }

    /**
     * Returns whether this command should be hidden from help.
     */
    public boolean isHidden() {
        return annotation.hidden();
    }

    /**
     * Returns the footer text.
     */
    public String getFooter() {
        return annotation.footer();
    }

    /**
     * Returns the simple name of the command class (used for generating XParser class name).
     */
    public String getClassName() {
        return element.getSimpleName().toString();
    }

    /**
     * Returns the qualified name of the command class.
     */
    public String getQualifiedName() {
        return element.getQualifiedName().toString();
    }

    /**
     * Returns the package name of the command class.
     */
    public String getPackageName() {
        return packageName;
    }

    /**
     * Returns all options for this command.
     */
    public List<OptionModel> getOptions() {
        return Collections.unmodifiableList(options);
    }

    /**
     * Adds an option to this command.
     */
    public void addOption(OptionModel option) {
        options.add(option);
    }

    /**
     * Returns all parameters for this command.
     */
    public List<ParameterModel> getParameters() {
        return Collections.unmodifiableList(parameters);
    }

    /**
     * Adds a parameter to this command.
     */
    public void addParameter(ParameterModel parameter) {
        parameters.add(parameter);
    }

    /**
     * Returns all mixins for this command.
     */
    public List<MixinModel> getMixins() {
        return Collections.unmodifiableList(mixins);
    }

    /**
     * Adds a mixin to this command.
     */
    public void addMixin(MixinModel mixin) {
        mixins.add(mixin);
    }

    /**
     * Returns all subcommands for this command.
     */
    public List<SubcommandModel> getSubcommands() {
        return Collections.unmodifiableList(subcommands);
    }

    /**
     * Adds a subcommand to this command.
     */
    public void addSubcommand(SubcommandModel subcommand) {
        subcommands.add(subcommand);
    }

    /**
     * Returns all method-based subcommands for this command.
     */
    public List<MethodSubcommandModel> getMethodSubcommands() {
        return Collections.unmodifiableList(methodSubcommands);
    }

    /**
     * Adds a method-based subcommand to this command.
     */
    public void addMethodSubcommand(MethodSubcommandModel methodSubcommand) {
        methodSubcommands.add(methodSubcommand);
    }

    /**
     * Returns all options including those inherited from mixins.
     * Command options override mixin options with the same name.
     */
    public List<OptionModel> getAllOptions() {
        Map<String, OptionModel> optionMap = new LinkedHashMap<>();

        // First, add all options from mixins
        for (MixinModel mixin : mixins) {
            for (OptionModel option : mixin.getOptions()) {
                for (String name : option.getNames()) {
                    optionMap.put(name, option);
                }
            }
        }

        // Then, add command options (overriding mixin options)
        for (OptionModel option : options) {
            for (String name : option.getNames()) {
                optionMap.put(name, option);
            }
        }

        return new ArrayList<>(optionMap.values());
    }

}
