package me.bechberger.femtocli.processor.model;

import me.bechberger.femtocli.annotations.Command;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

/**
 * Model representing metadata for a subcommand.
 */
public class SubcommandModel {

    private final CharSequence name;
    private TypeElement commandClass;
    private String description = "";
    private boolean methodBased = false;
    private TypeElement parentClass;

    /**
     * Creates a SubcommandModel with just the name.
     * Full details can be populated later when processing the subcommand class.
     */
    public SubcommandModel(CharSequence name) {
        this.name = name;
    }

    /**
     * Creates a SubcommandModel from a command class element.
     * Extracts the @Command.name() value as the subcommand name.
     */
    public SubcommandModel(TypeElement commandClass) {
        this.commandClass = commandClass;
        // Get the @Command annotation to extract the command name
        Command commandAnnotation = commandClass.getAnnotation(Command.class);
        if (commandAnnotation != null && !commandAnnotation.name().isEmpty()) {
            this.name = commandAnnotation.name();
        } else {
            // Fallback to class name if no @Command annotation or name is empty
            this.name = commandClass.getSimpleName();
        }
    }

    /**
     * Returns the subcommand name.
     */
    public CharSequence getName() {
        return name;
    }

    /**
     * Returns the command class for this subcommand.
     */
    public TypeElement getCommandClass() {
        return commandClass;
    }

    /**
     * Sets the command class for this subcommand.
     */
    public void setCommandClass(TypeElement commandClass) {
        this.commandClass = commandClass;
    }

    /**
     * Returns the description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description.
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns whether this is a method-based subcommand.
     */
    public boolean isMethodBased() {
        return methodBased;
    }

    /**
     * Sets whether this is a method-based subcommand.
     */
    public void setMethodBased(boolean methodBased) {
        this.methodBased = methodBased;
    }

    /**
     * Returns the parent class for method-based subcommands.
     */
    public TypeElement getParentClass() {
        return parentClass;
    }

    /**
     * Sets the parent class for method-based subcommands.
     */
    public void setParentClass(TypeElement parentClass) {
        this.parentClass = parentClass;
    }

    /**
     * Returns the qualified name of the subcommand class.
     */
    public String getQualifiedName() {
        if (commandClass == null) {
            return "";
        }
        return commandClass.getQualifiedName().toString();
    }

    /**
     * Returns the package name of the subcommand class.
     */
    public String getPackageName() {
        if (commandClass == null) {
            return "";
        }
        Element e = commandClass;
        while (e != null && e.getKind() != ElementKind.PACKAGE) {
            e = e.getEnclosingElement();
        }
        if (e instanceof PackageElement) {
            return ((PackageElement) e).getQualifiedName().toString();
        }
        return "";
    }

    /**
     * Returns the simple class name of the subcommand.
     */
    public String getClassName() {
        if (commandClass == null) {
            return "";
        }
        return commandClass.getSimpleName().toString();
    }
}
