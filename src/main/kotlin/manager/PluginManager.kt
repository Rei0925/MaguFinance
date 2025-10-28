package com.github.rei0925.magufinance.manager

import com.github.rei0925.magufinance.api.FinanceAPI
import com.github.rei0925.magufinance.api.FinancePlugin
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile

class PluginManager(private val api: FinanceAPI) {
    val plugins = mutableListOf<FinancePlugin>()

    fun load(plugin: FinancePlugin) {
        plugin.onEnable()
        plugins.add(plugin)
    }

    fun unload(plugin: FinancePlugin) {
        plugin.onDisable()
        plugins.remove(plugin)
    }

    fun unloadPlugins() {
        plugins.forEach { it.onDisable() }
        plugins.clear()
        println("All plugins have been unloaded.")
    }

    fun loadPlugins() {
        val pluginDir = File("plugins")
        if (!pluginDir.exists()) {
            pluginDir.mkdirs()
            println("Plugin directory created at: ${pluginDir.absolutePath}")
            return
        }

        pluginDir.listFiles { it.extension == "jar" }?.forEach { jarFile ->
            try {
                val loader = URLClassLoader(arrayOf(jarFile.toURI().toURL()), PluginManager::class.java.classLoader)
                val yamlStream = loader.getResourceAsStream("plugin.yml")

                if (yamlStream == null) {
                    println("Skipped ${jarFile.name}: plugin.yml not found.")
                    return@forEach
                }

                val yaml = org.yaml.snakeyaml.Yaml().load<Map<String, Any>>(yamlStream)
                val mainClassName = yaml["main"] as? String

                if (mainClassName == null) {
                    println("Skipped ${jarFile.name}: main class not specified in plugin.yml.")
                    return@forEach
                }

                val mainClass = loader.loadClass(mainClassName)
                val instance = mainClass.getDeclaredConstructor().newInstance()

                if (instance is FinancePlugin) {
                    load(instance)
                    println("Loaded plugin: ${jarFile.name} (${mainClassName})")
                } else {
                    println("Skipped ${jarFile.name}: main class is not a FinancePlugin.")
                }

            } catch (e: Exception) {
                println("Failed to load plugin ${jarFile.name}: ${e.message}")
            }
        }
    }
}