package net.aquadc.gson.adapter

import com.google.gson.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Type
import java.util.*

class ReadAsRootTest {

    @Test
    fun testReadingAsRoot() {
        val gson = GsonBuilder()
                .registerTypeAdapterFactory(ConstructorTypeAdapterFactory)
                .registerTypeAdapter(UserRole::class.java, UserRoleAdapter)
                .create()

        val id1 = UUID.randomUUID()
        val user1 = gson.fromJson("""{
            "id": "$id1",
            "admin": true
        }""", User::class.java)

        assertEquals(id1, user1.id)
        assertEquals(UserRole.Admin, user1.role)

        val id2 = UUID.randomUUID()
        val user2 = gson.fromJson("""{
            "id": "$id2",
            "admin": false
        }""", User::class.java)

        assertEquals(id2, user2.id)
        assertEquals(UserRole.Normal, user2.role)
    }

}

class User @Read constructor(
        @ReadAs("id") val id: UUID,
        @ReadAsRoot val role: UserRole
)

enum class UserRole {
    Normal, Admin
}

object UserRoleAdapter : JsonDeserializer<UserRole> {

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): UserRole {
        json as JsonObject
        return if (json.getAsJsonPrimitive("admin").asBoolean) UserRole.Admin else UserRole.Normal
    }

}
