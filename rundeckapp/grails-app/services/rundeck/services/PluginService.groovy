package rundeck.services

import com.dtolabs.rundeck.core.common.Framework
import com.dtolabs.rundeck.core.plugins.configuration.PropertyResolver
import com.dtolabs.rundeck.core.plugins.configuration.PropertyResolverFactory
import com.dtolabs.rundeck.core.plugins.PluggableProviderService
import com.dtolabs.rundeck.core.plugins.configuration.Description
import com.dtolabs.rundeck.core.plugins.configuration.PropertyScope
import com.dtolabs.rundeck.core.plugins.configuration.Validator
import com.dtolabs.rundeck.server.plugins.PluginRegistry

class PluginService {

    def PluginRegistry rundeckPluginRegistry
    def frameworkService
    static transactional = false

    def <T> T getPlugin(String name, PluggableProviderService<T> service) {
        def bean = rundeckPluginRegistry?.loadPluginByName(name, service)
        if (bean != null) {
            return (T) bean
        }
        log.error("${service.name} plugin not found: ${name}")
        return bean
    }

    /**
     *
     * @param name
     * @return map containing [instance:(plugin instance), description: (map or Description), ]
     */
    def Map getPluginDescriptor(String name, PluggableProviderService service) {
        def bean = rundeckPluginRegistry?.loadPluginDescriptorByName(name, service)
        if (bean != null) {
            return (Map) bean
        }
        log.error("${service.name} not found: ${name}")
        return null
    }

    /**
     * Configure a plugin given only instance configuration
     * @param name name
     * @param configuration instance configuration
     * @param service service
     * @return plugin, or null if configuration or plugin loading failed
     */
    def <T> Map configurePlugin(String name, Map configuration, PluggableProviderService<T> service) {
        def validation = rundeckPluginRegistry?.validatePluginByName(name, service, configuration)
        if (validation != null && !validation.valid) {
            logValidationErrors(service.name, name, validation.report )
            return null
        }
        def result = rundeckPluginRegistry?.configurePluginByName(name, service, configuration)
        if (result?.instance != null) {
            return result
        }
        log.error("${service.name} not found: ${name}")
        return null
    }

    /**
     * Configure a new plugin using only instance-scope configuration values
     * @param name provider name
     * @param configuration map of instance configuration values
     * @param service service
     * @return Map of [instance: plugin instance, configuration: Map of resolved configuration properties], or null
     */
    def <T> Map configurePlugin(String name, Map configuration, String projectName, Framework framework, PluggableProviderService<T> service) {
        def validation = rundeckPluginRegistry?.validatePluginByName(name, service, framework, projectName, configuration)
        if (!validation.valid) {
            logValidationErrors(service.name, name, validation.report)
            return null
        }
        def result = rundeckPluginRegistry?.configurePluginByName(name, service, framework, projectName, configuration)
        if (result.instance != null) {
            return result
        }
        log.error("${service.name} not found: ${name}")
        return null
    }

    /**
     * Configure a new plugin using a specific property resolver for configuration
     * @param name provider name
     * @param service service
     * @param resolver property resolver for configuration properties
     * @param defaultScope default plugin property scope
     * @return Map of [instance: plugin instance, configuration: Map of resolved configuration properties], or null
     */
    def <T> Map configurePlugin(String name, PluggableProviderService<T> service, PropertyResolver resolver, PropertyScope defaultScope) {
        def validation = rundeckPluginRegistry?.validatePluginByName(name, service,
                PropertyResolverFactory.createPrefixedResolver(resolver, name, service.name), defaultScope)
        if(!validation.valid) {
            logValidationErrors(service.name, name, validation.report)
            return null
        }
        def result = rundeckPluginRegistry?.configurePluginByName(name, service,
                PropertyResolverFactory.createPrefixedResolver(resolver, name, service.name), defaultScope)

        if (result.instance != null) {
            return result
        }
        log.error("${service.name} not found: ${name}")
        return null
    }

    private void logValidationErrors(String svcName, String pluginName,Validator.Report report) {
        def sb = new StringBuilder()
        sb<< "${svcName}: configuration was not valid for plugin '${pluginName}': "
        report?.errors.each { k, v ->
            sb<<"${k}: ${v}\n"
        }
        log.error(sb.toString())
    }

    /**
     * Configure a new plugin using a specific property resolver for configuration
     * @param name provider name
     * @param service service
     * @param resolver property resolver for configuration properties
     * @param defaultScope default plugin property scope
     * @return validation
     */
    def Map validatePlugin(String name, PluggableProviderService service, PropertyResolver resolver, PropertyScope defaultScope) {
        return rundeckPluginRegistry?.validatePluginByName(name, service,
                PropertyResolverFactory.createPrefixedResolver(resolver, name, service.name), defaultScope)
    }
    /**
     * Configure a new plugin using a specific property resolver for configuration
     * @param name provider name
     * @param service service
     * @param resolver property resolver for configuration properties
     * @param defaultScope default plugin property scope
     * @param ignoredScope ignored scope
     * @return validation
     */
    def Map validatePlugin(String name, PluggableProviderService service, PropertyResolver resolver, PropertyScope defaultScope, PropertyScope ignoredScope) {
        return rundeckPluginRegistry?.validatePluginByName(name, service,
                PropertyResolverFactory.createPrefixedResolver(resolver, name, service.name), defaultScope, ignoredScope)
    }

    /**
     * Configure a new plugin using a specific property resolver for configuration
     * @param name provider name
     * @param service service
     * @param config instance configuration data
     * @return validation
     */
    def Map validatePluginConfig(String name, PluggableProviderService service, Map config) {
        return rundeckPluginRegistry?.validatePluginByName(name, service, config)
    }

    def <T> Map listPlugins(Class<T> clazz,PluggableProviderService<T> service) {
        def plugins = rundeckPluginRegistry?.listPluginDescriptors(clazz, service)
        //XX: avoid groovy bug where generic types referenced in closure can cause NPE: http://jira.codehaus.org/browse/GROOVY-5034
        String svcName=service.name
        //clean up name of any Groovy plugin without annotations that ends with the service name
        plugins.each { key, Map plugin ->
            def desc = plugin.description
            if (desc && desc instanceof Map) {
                if (desc.name.endsWith(svcName)) {
                    desc.name = desc.name.substring(0,desc.name.length()- svcName.length())
                }
            }
        }
//        System.err.println("listed plugins: ${plugins}")

        plugins
    }
}
