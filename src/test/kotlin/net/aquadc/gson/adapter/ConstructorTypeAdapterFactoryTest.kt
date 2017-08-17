package net.aquadc.gson.adapter

import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstructorTypeAdapterFactoryTest {

    private val gson = GsonBuilder()
            .registerTypeAdapterFactory(ConstructorTypeAdapterFactory)
            .create()

    @Test fun serialization() {
        val json = gson.toJson(User("Mike", "Gorunov"))
        assertTrue(json == """{"firstName":"Mike","lastName":"Gorunov"}""" ||
                json == """{"lastName":"Gorunov","firstName":"Mike"}""")
    }

    @Test fun deserialization() {
        val model = gson.fromJson<User>("""{
            "firstName": "Mike",
            "lastName": "Gorunov"
        }""", User::class.java)

        assertEquals("Mike", model.name)
        assertEquals("Gorunov", model.surname)
    }

    @Write
    class User @Read constructor(
            @ReadAs("firstName") @get:WriteAs("firstName") val name: String,
            @ReadAs("lastName") @get:WriteAs("lastName") val surname: String
    )

}