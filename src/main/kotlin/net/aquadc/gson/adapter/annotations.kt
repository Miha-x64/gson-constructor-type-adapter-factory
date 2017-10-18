package net.aquadc.gson.adapter

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.*

/**
 * Indicates that annotated constructor should be used to create instances of a given class.
 * Requires all constructor parameters to be annotated either with @[ReadAs] or @[ReadAsRoot].
 */
@Target(CONSTRUCTOR)
@Retention(RUNTIME)
annotation class Read

/**
 * Specifies name of serialized form of given constructor parameter.
 */
@Target(VALUE_PARAMETER)
@Retention(RUNTIME)
annotation class ReadAs(val name: String)

/**
 * Specifies that deserializer should pass whole JsonObject (@[ReadAs] passes value of a single field) to TypeAdapter.
 */
@Target(VALUE_PARAMETER)
@Retention(RUNTIME)
annotation class ReadAsRoot


/**
 * Indicates that this class can be serialized.
 * This will cause calling every getter annotated with @[WriteAs] and serializing returned values.
 */
@Target(CLASS)
@Retention(RUNTIME)
annotation class Write

/**
 * Specifies name of serialized form of given property getter.
 */
@Target(PROPERTY_GETTER)
@Retention(RUNTIME)
annotation class WriteAs(val name: String)

/**
 * Specified that
 */
@Target(PROPERTY_GETTER)
@Retention(RUNTIME)
annotation class MergeWithRoot
