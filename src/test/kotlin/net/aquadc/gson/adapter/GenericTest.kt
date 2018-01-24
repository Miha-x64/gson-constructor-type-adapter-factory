package net.aquadc.gson.adapter

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Test


class GenericTest {

    private val gson = GsonBuilder()
            .registerTypeAdapterFactory(ConstructorTypeAdapterFactory)
            .create()

    @Test fun testGenericString() {
        val json = """{"value":{"firstName":"John","lastName":"Doe"}}"""
        val type = TypeToken.getParameterized(Box::class.java, UserTest.User::class.java).type

        val parsed = gson.fromJson<Box<UserTest.User>>(json, type).value
        assertEquals("John", parsed.name)
        assertEquals("Doe", parsed.surname)
    }

    @Write
    class Box<T> @Read constructor(
            @ReadAs("value") @get:WriteAs("value") val value: T
    )

}
