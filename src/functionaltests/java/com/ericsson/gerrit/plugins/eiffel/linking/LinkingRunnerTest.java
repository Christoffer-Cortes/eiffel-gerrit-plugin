package com.ericsson.gerrit.plugins.eiffel.linking;

import org.junit.runner.RunWith;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;

@RunWith(Cucumber.class)
@CucumberOptions(features = "src/functionaltests/resources/features/linking.feature", glue = {
        "com.ericsson.gerrit.plugins.eiffel.linking" }, plugin = { "pretty",
                "html:target/cucumber-reports/LinkingRunner" })
public class LinkingRunnerTest {
// This class needs to have test in the name to be picked up by the test runner
}
