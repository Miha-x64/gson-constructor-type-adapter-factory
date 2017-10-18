package net.aquadc.gson.adapter

import com.google.gson.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Type
import java.util.*

class ReadAsRootTest {

    private val gson = GsonBuilder()
            .registerTypeAdapterFactory(ConstructorTypeAdapterFactory)
            .registerTypeAdapter(UserRole::class.java, UserRoleAdapter)
            .create()

    @Test
    fun testReadingAsRoot() {
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

    @Test fun testMergingWithRoot() {
        val user1 = User(UUID.randomUUID(), UserRole.Normal)
        val user1Tree = gson.toJsonTree(user1).asJsonObject

        assertEquals(user1.id, UUID.fromString(user1Tree.get("id").asString))
        assertEquals(false, user1Tree.get("admin").asBoolean)

        val user2 = User(UUID.randomUUID(), UserRole.Admin)
        val user2Tree = gson.toJsonTree(user2).asJsonObject

        assertEquals(user2.id, UUID.fromString(user2Tree.get("id").asString))
        assertEquals(true, user2Tree.get("admin").asBoolean)
    }

}

@Write
class User @Read constructor(
        @ReadAs("id") @get:WriteAs("id") val id: UUID,
        @ReadAsRoot @get:MergeWithRoot val role: UserRole
)

enum class UserRole {
    Normal, Admin
}

object UserRoleAdapter : JsonSerializer<UserRole>, JsonDeserializer<UserRole> {

    override fun serialize(src: UserRole, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
            JsonObject().apply {
                // in production, {"admin": true} and {"admin": false} JsonObjects may be reused,
                // because adapter does not mutate them
                this.addProperty("admin", when (src) {
                    UserRole.Normal -> false
                    UserRole.Admin -> true
                })
            }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): UserRole {
        json as JsonObject
        return if (json.getAsJsonPrimitive("admin").asBoolean) UserRole.Admin else UserRole.Normal
    }

}
