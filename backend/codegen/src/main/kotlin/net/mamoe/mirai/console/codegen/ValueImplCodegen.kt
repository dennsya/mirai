/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.console.codegen

import org.intellij.lang.annotations.Language
import java.io.File


fun main() {
    println(File("").absolutePath) // default project base dir

    File("backend/mirai-console/src/main/kotlin/net/mamoe/mirai/console/setting/_ValueImpl.kt").apply {
        createNewFile()
    }.writeText(buildString {
        appendln(COPYRIGHT)
        appendln()
        appendln(PACKAGE)
        appendln()
        appendln(IMPORTS)
        appendln()
        appendln()
        appendln(DO_NOT_MODIFY)
        appendln()
        appendln()
        appendln(genAllValueImpl())
    })
}

private val DO_NOT_MODIFY = """
/**
 * !!! This file is auto-generated by backend/codegen/src/kotlin/net.mamoe.mirai.console.codegen.ValueImplCodegen.kt
 * !!! DO NOT MODIFY THIS FILE MANUALLY
 */
""".trimIndent()

private val PACKAGE = """
package net.mamoe.mirai.console.setting
""".trimIndent()

private val IMPORTS = """
import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
""".trimIndent()

fun genAllValueImpl(): String = buildString {
    fun appendln(@Language("kt") code: String) {
        this.appendln(code.trimIndent())
    }

    // PRIMITIVE
    for (number in NUMBERS + OTHER_PRIMITIVES) {
        appendln(genValueImpl(number, number, "$number.serializer()", false))
        appendln()
    }

    // PRIMITIVE ARRAYS
    for (number in NUMBERS + OTHER_PRIMITIVES.filterNot { it == "String" }) {
        appendln(genValueImpl("${number}Array", "${number}Array", "${number}ArraySerializer()", true))
        appendln()
    }

    // TYPED ARRAYS
    for (number in NUMBERS + OTHER_PRIMITIVES) {
        appendln(
            genValueImpl(
                "Array<${number}>",
                "Typed${number}Array",
                "ArraySerializer(${number}.serializer())",
                true
            )
        )
        appendln()
    }

    // PRIMITIVE LISTS / SETS
    for (collectionName in listOf("List", "Set")) {
        for (number in NUMBERS + OTHER_PRIMITIVES) {
            appendln(
                genValueImpl(
                    "${collectionName}<${number}>",
                    "${number}${collectionName}",
                    "${collectionName}Serializer(${number}.serializer())",
                    false
                )
            )
            appendln()
        }
    }

    appendln()

    // MUTABLE LIST / MUTABLE SET

    for (collectionName in listOf("List", "Set")) {
        for (number in NUMBERS + OTHER_PRIMITIVES) {
            appendln(
                """
                @JvmName("valueImplMutable${number}${collectionName}")
                internal fun Setting.valueImpl(
                    default: Mutable${collectionName}<${number}>
                ): Mutable${number}${collectionName}Value {
                    var internalValue: Mutable${collectionName}<${number}> = default

                    val delegt = dynamicMutable${collectionName}{ internalValue }
                    return object : Mutable${number}${collectionName}Value(), Mutable${collectionName}<${number}> by delegt {
                        override var value: Mutable${collectionName}<${number}>
                            get() = internalValue
                            set(new) {
                                if (new != internalValue) {
                                    internalValue = new
                                    onElementChanged(this)
                                }
                            }
                        
                        private val outerThis get() = this
                        
                        override val serializer: KSerializer<Mutable${collectionName}<${number}>> = object : KSerializer<Mutable${collectionName}<${number}>> {
                            private val delegate = ${collectionName}Serializer(${number}.serializer())
                            override val descriptor: SerialDescriptor = delegate.descriptor

                            override fun deserialize(decoder: Decoder): Mutable${collectionName}<${number}> {
                                return delegate.deserialize(decoder).toMutable${collectionName}().observable { 
                                    onElementChanged(outerThis)
                                }
                            }

                            override fun serialize(encoder: Encoder, value: Mutable${collectionName}<${number}>) {
                                delegate.serialize(encoder, value)
                            }
                        }
                    }
                }
        """
            )
            appendln()
        }
    }

    appendln()


    appendln(
        """
            internal fun <T : Setting> Setting.valueImpl(default: T): Value<T> {
                return object : SettingValue<T>() {
                    private var internalValue: T = default
                    override var value: T
                        get() = internalValue
                        set(new) {
                            if (new != internalValue) {
                                internalValue = new
                                onElementChanged(this)
                            }
                        }
                    override val serializer = object : KSerializer<T>{
                        override val descriptor: SerialDescriptor
                            get() = internalValue.updaterSerializer.descriptor

                        override fun deserialize(decoder: Decoder): T {
                            internalValue.updaterSerializer.deserialize(decoder)
                            return internalValue
                        }

                        override fun serialize(encoder: Encoder, value: T) {
                            internalValue.updaterSerializer.serialize(encoder, SettingSerializerMark)
                        }
                    }
                }
            }
        """
    )
}

fun genValueImpl(kotlinTypeName: String, miraiValueName: String, serializer: String, isArray: Boolean): String =
    """
        internal fun Setting.valueImpl(default: ${kotlinTypeName}): ${miraiValueName}Value {
            return object : ${miraiValueName}Value() {
                private var internalValue: $kotlinTypeName = default
                override var value: $kotlinTypeName
                    get() = internalValue
                    set(new) {
                        ${
    if (isArray) """
                        if (!new.contentEquals(internalValue)) {
                            internalValue = new
                            onElementChanged(this)
                        }
    """.trim()
    else """
                        if (new != internalValue) {
                            internalValue = new
                            onElementChanged(this)
                        }
    """.trim()
    }
                    }
                override val serializer = $serializer
            }
        }
    """.trimIndent() + "\n"

