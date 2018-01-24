package net.aquadc.gson.adapter

import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserTest {

    private val gson = GsonBuilder()
            .registerTypeAdapterFactory(ConstructorTypeAdapterFactory)
            .create()

    @Test fun serialization() {
        val json = gson.toJson(User("Mike", "G"))
        assertTrue(json == """{"firstName":"Mike","lastName":"G"}""" ||
                json == """{"lastName":"G","firstName":"Mike"}""")
    }

    @Test fun deserialization() {
        val model = gson.fromJson<User>("""{
            "firstName": "Mike",
            "lastName": "G"
        }""", User::class.java)

        assertEquals("Mike", model.name)
        assertEquals("G", model.surname)
    }

    @Write
    class User @Read constructor(
            @ReadAs("firstName") @get:WriteAs("firstName") val name: String,
            @ReadAs("lastName") @get:WriteAs("lastName") val surname: String
    )

}