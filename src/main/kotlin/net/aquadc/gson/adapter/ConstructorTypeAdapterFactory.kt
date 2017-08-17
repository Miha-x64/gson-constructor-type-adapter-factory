package net.aquadc.gson.adapter

import com.google.gson.*
import com.google.gson.internal.bind.TreeTypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Type

object ConstructorTypeAdapterFactory : TypeAdapterFactory {

    override fun <T : Any?> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        val raw: Class<in T> = type.rawType

        @Suppress("UNCHECKED_CAST") // Class#getConstructors returns Array<Constructor<T>>, I GUARANTEE IT
        val ctors = (raw.constructors as Array<Constructor<in T>>).filter { it.getAnnotation(Read::class.java) != null }
        if (ctors.size > 1) throw IllegalArgumentException(
                "At most one constructor should be annotated with @Read, ${ctors.size} given: $ctors in class $raw")

        val ctor = if (ctors.size == 1) ctors[0] else null

        val write = raw.getAnnotation(Write::class.java)

        return if (ctor != null || write != null) ConstructorTypeAdapter(gson, type, ctor, write) else null
    }

}

private class ConstructorTypeAdapter<T>(
        gson: Gson,
        private val type: TypeToken<T>,
        constructor: Constructor<in T>?,
        write: Write?
) : TypeAdapter<T>() {

    private val readAdapter: TypeAdapter<T>?
    private val writer: Writer<T>?

    init {
        if (constructor == null && write == null) throw AssertionError()

        readAdapter =
                if (constructor != null) TreeTypeAdapter<T>(null, ConstructorJsonDeserializer(type, constructor), gson, type, null)
                else null

        writer =
                if (write != null) Writer(gson, type)
                else null
    }

    override fun read(`in`: JsonReader): T {
        if (readAdapter == null) throw UnsupportedOperationException("This ConstructorTypeAdapter instance is write-only, no @Read constructor found in class ${type.rawType}")
        return readAdapter.read(`in`)
    }

    override fun write(out: JsonWriter, value: T) {
        if (writer == null) throw UnsupportedOperationException("This ConstructorTypeAdapter instance is read-only, no @Write annotation found on class ${type.rawType}")
        writer.write(out, value)
    }

}

private class ConstructorJsonDeserializer<T>(
        private val type: TypeToken<T>,
        private val constructor: Constructor<in T>
) : JsonDeserializer<T> {

    private val names: Array<String>
    private val types: Array<Type>

    init {
        val paramAnnos = constructor.parameterAnnotations

        val names = arrayOfNulls<String>(paramAnnos.size)
        for (i in 0 until paramAnnos.size) {
            names[i] = (paramAnnos[i].firstOrNull { it is ReadAs } as ReadAs?)?.name
                    ?: throw IllegalArgumentException("Every @Read constructor parameter must be annotated with @ReadAs, parameter #$i is not in constructor $constructor of type ${type.rawType}")
        }

        // Array will be filled, I GUARANTEE IT
        this.names = @Suppress("UNCHECKED_CAST") (names as Array<String>)
        this.types = constructor.genericParameterTypes
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): T {
        val names = names
        val types = types
        val size = names.size
        json as? JsonObject
                ?: throw IllegalArgumentException("ConstructorJsonDeserializers deserializes only Objects, $json given for type ${type.rawType}")
        val params = arrayOfNulls<Any?>(size) // todo: array recycling
        for (i in 0 until size) {
            params[i] = context.deserialize(json.get(names[i]), types[i])
        }

        @Suppress("UNCHECKED_CAST") // i hope Gson won't give shit to me
        return constructor.newInstance(*params) as T
    }
}

private class Writer<in T>(
        gson: Gson,
        type: TypeToken<T>
) {

    private val names: Array<String>
    private val getters: Array<Method>
    private val adapters: Array<TypeAdapter<Any?>>

    init {
        val getters = type.rawType.methods.filter { it.getAnnotation(WriteAs::class.java) != null }.toTypedArray()
        val voidGetters = getters.filter { it.returnType == Void.TYPE }
        if (voidGetters.isNotEmpty()) throw IllegalArgumentException("Getters annotated with @WriteAs must not return void as these do: $voidGetters in class ${type.rawType}")

        val size = getters.size
        val names = arrayOfNulls<String>(size)
        val adapters = arrayOfNulls<TypeAdapter<*>>(size)

        for (i in 0 until size) {
            val getter = getters[i]
            names[i] = getter.getAnnotation(WriteAs::class.java)!!.name
            adapters[i] = gson.getAdapter(TypeToken.get(getter.genericReturnType))
        }

        this.names = @Suppress("UNCHECKED_CAST") (names as Array<String>)
        this.getters = getters
        this.adapters = @Suppress("UNCHECKED_CAST") (adapters as Array<TypeAdapter<Any?>>)
    }

    fun write(out: JsonWriter, value: T) {
        out.beginObject()

        val names = names
        val getters = getters
        val adapters = adapters
        val size = adapters.size

        for (i in 0 until size) {
            out.name(names[i])
            adapters[i].write(out, getters[i].invoke(value))
        }

        out.endObject()
    }
}