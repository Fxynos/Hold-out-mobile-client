package com.vl.holdout.parser

import com.vl.holdout.parser.pojo.*
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.LinkedList
import java.util.stream.Collectors
import kotlin.streams.toList

object Parser {
    /**
     * @param root sources directory root
     * Root must contain main.ho - text file for parsing
     * main.ho positions other sources like pictures relative to itself
     */
    fun load(root: File) = create(read(File(root, "main.ho")).parse(), root)

    private fun read(file: File) = FileInputStream(file).use { stream ->
        val reader = BufferedReader(InputStreamReader(stream))
        val list = LinkedList<String>()
        var s: String? = null
        while (
            s.let {
                s = reader.readLine()
                s != null
            }
        ) s!!.split('#', limit = 2)[0]
            .takeUnless { it.isBlank() }
            ?.also { list.add(it) }
        list.joinToString("\n")
    }

    /**
     * @param areaForException will be shown in exception message
     * @throws ParseException if string doesn't contain ':'
     */
    private fun (String).asPair(areaForException: String = this) = split(':', limit = 2)
        .takeIf { it.size > 1 }?.let { it[0].trim().lowercase() to it[1].trim() } ?:
        throw ParseException(areaForException)

    private fun (String).parse() = split('@').let { it.subList(1, it.size) }.parallelStream()
        .map { sObj ->
            val typeAndName = sObj.substring(0, sObj.indexOf('\n')
                .let { if (it < 0) throw ParseException(sObj) else it }
            ).asPair(sObj)
            val props = sObj.split('\n').stream().filter { it.isNotBlank() }.map { it.asPair(sObj) }
                    .collect(Collectors.toMap({ it.first }, { it.second }))
            typeAndName to props // Map of properties also contains type-name pair (constructors will map type to name)
        }.collect(
            { HashMap<String, MutableMap<String, Map<String, String>>>() },
            { typeMap, typeAndNameAndProperties ->
                (
                    typeMap[typeAndNameAndProperties.first.first] ?: // providing set of objects for certain type
                        HashMap<String, Map<String, String>>().also { typeMap[typeAndNameAndProperties.first.first] = it }
                )[typeAndNameAndProperties.first.second] = typeAndNameAndProperties.second
            },
            { m1, m2 ->
                for ((type, objects) in m2)
                    (
                        m1[type] ?: HashMap<String, Map<String, String>>().also { m1[type] = it }
                    ).putAll(objects)
            }
        ) as Map<String, Map<String, Map<String, String>>> // type -> instance -> property

    @Suppress("UNCHECKED_CAST")
    private fun create(objects: Map<String, Map<String, Map<String, String>>>, root: File): Repositories {
        val repositories = mapOf<String, Repository<*>>(
            "core" to Repository<Core>(),
            "bar" to Repository<Bar>(),
            "picture" to Repository<Picture>(),
            "choice" to Repository<Choice>(),
            "card" to Repository<Card>()
        )
        objects.entries.stream()
            .map { typeAndObjects ->
                val (repository, constructor) = (
                        repositories[typeAndObjects.key] ?:
                        throw ParseException("Unknown type \"${typeAndObjects.key}\"")
                ) to when (typeAndObjects.key) {
                    "core" -> CoreConstructor(repositories["choice"] as Repository<Choice>, repositories["bar"] as Repository<Bar>)
                    "bar" -> BarConstructor(root)
                    "picture" -> PictureConstructor(repositories["picture"] as Repository<Picture>, root)
                    "choice" -> ChoiceConstructor(repositories["card"] as Repository<Card>, repositories["bar"] as Repository<Bar>)
                    "card" -> CardConstructor(repositories["choice"] as Repository<Choice>, repositories["picture"] as Repository<Picture>)
                    else -> throw RuntimeException() // can not be reached
                }
                typeAndObjects.value.values.stream().map { constructor.create(it) }.forEach {
                    repository as Repository<Base> += it
                }
                Triple(repository, constructor, typeAndObjects.value)
            }.toList().forEach { (repository, constructor, properties) -> // toList() is used because of at this line all objects must be initialized
            properties.entries.forEach { (name, properties) ->
                (constructor as Constructor<Base>).finish(repository[name], properties)
            }
        }
        return Repositories.fromMap(repositories)
    }
}