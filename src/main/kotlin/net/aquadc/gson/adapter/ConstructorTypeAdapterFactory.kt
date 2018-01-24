package net.aquadc.gson.adapter

import com.google.gson.*
import com.google.gson.internal.`$Gson$Types`
import com.google.gson.internal.bind.TreeTypeAdapter
import com.google.gson.internal.bind.TypeAdapters
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

    private val names: Array<String?>
    private val types: Array<Type>

    init {
        val paramAnnos = constructor.parameterAnnotations

        val names = arrayOfNulls<String>(paramAnnos.size)
        for (i in 0 until paramAnnos.size) {
            val readAs = (paramAnnos[i].firstOrNull { it is ReadAs } as ReadAs?)?.name
            val readAsRoot = (paramAnnos[i].firstOrNull { it is ReadAsRoot }) != null

            if (readAs == null && !readAsRoot)
                throw IllegalArgumentException("Every @Read constructor parameter must be annotated " +
                        "either with @ReadAs or @ReadAsRoot, but parameter #$i is not, " +
                        "in constructor $constructor of type ${type.rawType}")

            names[i] = readAs
        }

        this.names = names

        val genericTypes = constructor.genericParameterTypes
        this.types = Array(genericTypes.size) {
            val genericType = genericTypes[it]
            val resolvedType = `$Gson$Types`.resolve(type.type, type.rawType, genericType)
            resolvedType
        }
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): T {
        val names = names
        val types = types
        val size = names.size
        json as? JsonObject
                ?: throw IllegalArgumentException("ConstructorJsonDeserializers deserializes only Objects, $json given for type ${type.rawType}")
        val params = arrayOfNulls<Any?>(size) // todo: array recycling
        for (i in 0 until size) {
            // take specified field or whole JsonObject
            val name = names[i] // if name == null, it's @ReadAsRoot
            val jsonEl = if (name != null) { json.get(name) } else json
            params[i] = context.deserialize(jsonEl, types[i])
        }

        @Suppress("UNCHECKED_CAST") // i hope Gson won't give shit to me
        return constructor.newInstance(*params) as T
    }
}

private class Writer<in T>(
        gson: Gson,
        type: TypeToken<T>
) {

    private val names: Array<String> // names may be smaller than others
    private val getters: Array<Method> // getters.size == adapters.size
    private val adapters: Array<TypeAdapter<Any?>>
    private val namesDeniedForMerge: Set<String> // if not empty, there are some @MergeWithRoot properties

    init {
        val methods = type.rawType.methods
        val namedGetters = methods.filter { it.getAnnotation(WriteAs::class.java) != null }
        val mergeGetters = methods.filter { it.getAnnotation(MergeWithRoot::class.java) != null }
        val allGetters = (namedGetters + mergeGetters).toTypedArray()

        val voidGetters = allGetters.filter { it.returnType == Void.TYPE }
        if (voidGetters.isNotEmpty())
            throw IllegalArgumentException("Getters annotated with @WriteAs must not return void as these do: $voidGetters in class ${type.rawType}")

        val gettersCount = allGetters.size
        val namedGettersCount = namedGetters.size
        val names = arrayOfNulls<String>(namedGettersCount)
        val adapters = arrayOfNulls<TypeAdapter<*>>(gettersCount)

        val brokenGetters = HashSet(namedGetters)
        brokenGetters.retainAll(mergeGetters)
        if (brokenGetters.isNotEmpty())
            throw IllegalArgumentException("Getter must annotated either with @WriteAs or @MergeWithRoot, both found on $brokenGetters in class ${type.rawType}")

        for (i in 0 until namedGettersCount) {
            val getter = allGetters[i]
            names[i] = getter.getAnnotation(WriteAs::class.java)!!.name
            adapters[i] = gson.getAdapter(TypeToken.get(getter.genericReturnType))
        }
        @Suppress("UNCHECKED_CAST") (names as Array<String>)

        for (i in namedGettersCount until gettersCount) {
            val getter = allGetters[i]
            adapters[i] = gson.getAdapter(TypeToken.get(getter.genericReturnType))
        }

        val nameSet = names.toHashSet()
        if (nameSet.size != names.size)
            throw IllegalArgumentException("Found duplicate names in @WriteAs properties")

        this.names = names
        this.getters = allGetters
        this.adapters = @Suppress("UNCHECKED_CAST") (adapters as Array<TypeAdapter<Any?>>)
        this.namesDeniedForMerge = if (mergeGetters.isEmpty()) emptySet() else nameSet // throw away nameSet if it will never be used
    }

    fun write(out: JsonWriter, value: T?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.beginObject()
            writeNamedFields(out, value)
            writeMergedFields(out, value)
            out.endObject()
        }
    }

    private fun writeNamedFields(out: JsonWriter, value: T) {
        val names = names
        val getters = getters
        val adapters = adapters

        for (i in 0 until names.size) {
            out.name(names[i])
            val fieldValue = getters[i](value)
            adapters[i].write(out, fieldValue)
        }
    }

    private fun writeMergedFields(out: JsonWriter, value: T) {
        val getters = getters
        val adapters = adapters

        val canonicalDeniedNames = namesDeniedForMerge
        val locallyDeniedNames = HashSet<String>()
        for (i in names.size until adapters.size) {
            val fieldValue = getters[i](value)
            val jsonEl = adapters[i].toJsonTree(fieldValue)

            if (jsonEl !is JsonObject)
                throw IllegalArgumentException("@MergeWithRoot-annotated getter must return object which will be serialized to JsonObject. " +
                        "Method ${getters[i]}, adapter ${adapters[i]}, returned $jsonEl.")

            jsonEl.entrySet().forEach { (key, value) ->
                if (key in canonicalDeniedNames)
                    throw IllegalArgumentException(
                            "@MergeWithRoot-annotated getter returned object which was serialized JsonObject " +
                                    "containing mapping for key which already exists in root JsonObject. " +
                            "Method ${getters[i]}, adapter ${adapters[i]}, key $key, sub-object $jsonEl.")

                if (key in locallyDeniedNames)
                    throw IllegalArgumentException(
                            "@MergeWithRoot-annotated getter returned object which was serialized to JsonObject " +
                                    "containing mapping for key which already exists in another @MergeWithRoot object. " +
                                    "Method ${getters[i]}, adapter ${adapters[i]}, key $key, already taken keys $locallyDeniedNames, sub-object $jsonEl."
                    )

                locallyDeniedNames.add(key)
                out.name(key)
                TypeAdapters.JSON_ELEMENT.write(out, value)
            }
        }
    }

}
