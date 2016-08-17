package no.cantara.maven.plugins;

import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

/**
 * I am an alias.
 *
 * @author <a href="mailto:kgrodzicki@gmail.com">Krzysztof Grodzicki</a> 15/08/16.
 */
public enum Alias {
    DEV, TEST, PROD;

    public String addSuffix(String functionName) {
        return Stream.of(functionName, "-", this.name().toLowerCase())
                     .collect(joining());
    }
}
