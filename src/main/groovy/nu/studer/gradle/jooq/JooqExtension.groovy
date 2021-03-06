/**
 Copyright 2014 Etienne Studer

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package nu.studer.gradle.jooq

import nu.studer.gradle.util.JaxbConfigurationBridge
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.jooq.util.jaxb.Configuration

/**
 * Extension point to configure the jOOQ source code generation and the source set in which to include
 * the generated sources. This extension point allows multiple jOOQ configurations per source set.
 */
class JooqExtension {

    final Project project
    final Closure jooqConfigurationHandler
    final String path
    final Map<String, JooqConfiguration> configs

    JooqExtension(Project project, Closure jooqConfigurationHandler, String path) {
        this.project = project
        this.jooqConfigurationHandler = jooqConfigurationHandler
        this.path = path
        this.configs = [:]
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    def methodMissing(String configName, args) {
        if (args.length == 2 && args[0] instanceof SourceSet && args[1] instanceof Closure) {
            applyClosureToConfig(configName, args[0], args[1])
        } else {
            throw new MissingMethodException(configName, getClass(), args)
        }
    }

    private void applyClosureToConfig(String configName, SourceSet sourceSet, Closure closure) {
        // find or create bridge extension for the given configuration name
        def jooqConfig = findOrCreateConfig(configName, sourceSet)

        // ensure the same configuration is not defined for different source sets
        if (sourceSet.name != jooqConfig.sourceSet.name) {
            throw new IllegalArgumentException("Configuration '$configName' configured for multiple source sets: $sourceSet and $jooqConfig.sourceSet")
        }

        // apply the given closure to the configuration bridge, i.e. its contained JAXB Configuration object
        def delegate = jooqConfig.configBridge
        Closure copy = (Closure) closure.clone();
        copy.resolveStrategy = Closure.DELEGATE_FIRST;
        copy.delegate = delegate;
        if (copy.maximumNumberOfParameters == 0) {
            copy.call();
        } else {
            copy.call delegate;
        }

        delegate.target
    }

    private JooqConfiguration findOrCreateConfig(String configName, SourceSet sourceSet) {
        JooqConfiguration jooqConfig = configs[configName]
        if (!jooqConfig) {
            // create jOOQ configuration
            def configBridge = new JaxbConfigurationBridge(new Configuration(), "${path}.${configName}")
            jooqConfig = new JooqConfiguration(sourceSet, configBridge)

            // pre-configure jOOQ configuration and create task derived from the configuration
            jooqConfigurationHandler configName, jooqConfig

            // register jOOQ configuration
            configs[configName] = jooqConfig
        }
        jooqConfig
    }

}
