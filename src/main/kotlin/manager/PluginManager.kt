package com.github.rei0925.manager

import com.github.rei0925.api.FinanceAPI
import com.github.rei0925.api.FinancePlugin
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

        pluginDir.listFiles { file -> file.extension == "jar" }?.forEach { jarFile ->
            try {
                val loader = URLClassLoader(arrayOf(jarFile.toURI().toURL()), this::class.java.classLoader)
                val jar = JarFile(jarFile)
                val entry = jar.manifest.mainAttributes.getValue("Main-Class")
                if (entry != null) {
                    val clazz = loader.loadClass(entry)
                    val instance = clazz.getDeclaredConstructor().newInstance()
                    if (instance is FinancePlugin) {
                        load(instance)
                        println("Loaded plugin: ${clazz.simpleName}")
                    } else {
                        println("Skipped ${jarFile.name}: Main-Class is not a FinancePlugin.")
                    }
                } else {
                    println("Skipped ${jarFile.name}: No Main-Class in manifest.")
                }
            } catch (e: Exception) {
                println("Failed to load plugin ${jarFile.name}: ${e.message}")
            }
        }
    }
}