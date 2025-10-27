package com.github.rei0925.magufinance.manager

import com.github.rei0925.magufinance.api.FinanceAPI
import com.github.rei0925.magufinance.api.FinancePlugin
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile

class PluginManager(private val api: FinanceAPI) {
    private val plugins = mutableListOf<FinancePlugin>()

    fun load(plugin: FinancePlugin) {
        plugin.onEnable(api)
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
                val loader = URLClassLoader(arrayOf(jarFile.toURI().toURL()), this::class.java.classLoader)
                val jar = JarFile(jarFile)
                val entries = jar.entries().toList().filter { it.name.endsWith(".class") }

                var loaded = false
                for (entry in entries) {
                    val className = entry.name.removeSuffix(".class").replace('/', '.')
                    val clazz = loader.loadClass(className)
                    val instance = clazz.getDeclaredConstructor().newInstance()
                    if (instance is FinancePlugin) {
                        load(instance)
                        println("Loaded plugin: ${clazz.simpleName}")
                        loaded = true
                        break
                    }
                }

                if (!loaded) {
                    println("Skipped ${jarFile.name}: No FinancePlugin implementation found.")
                }

            } catch (e: Exception) {
                println("Failed to load plugin ${jarFile.name}: ${e.message}")
            }
        }
    }
}