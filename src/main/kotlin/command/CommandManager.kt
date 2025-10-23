package com.github.rei0925.command

import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.declaredMemberFunctions

class CommandManager() {
    private val commands = mutableMapOf<String, Pair<Any, (List<String>) -> Unit>>()

    fun registerCommand(instance: Any) {
        val aliasAnnotation = instance::class.findAnnotation<CommandAlias>() ?: return
        val alias = aliasAnnotation.value

        instance::class.declaredMemberFunctions.forEach { method ->
            val subAnnotation = method.findAnnotation<Subcommand>()
            val key = if (subAnnotation != null && subAnnotation.value.isNotEmpty())
                "$alias ${subAnnotation.value}"
            else alias

            commands[key] = instance to { args -> method.call(instance, args) }
        }
    }

    fun runCommand(input: String) {
        val tokens = input.split(" ")
        val cmdKey = if (tokens.size > 1) "${tokens[0]} ${tokens[1]}" else tokens[0]
        val command = commands[cmdKey] ?: commands[tokens[0]]
        if (command != null) command.second.invoke(tokens.drop(1))
        else println("不明なコマンド: $input")
    }
}