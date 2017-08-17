
Reads `JsonObject`s through constructors using Gson. Example:

```kt
class User @Read constructor(
        @ReadAs("firstName") val name: String,
        @ReadAs("lastName") val surname: String
)
```

```kt
val gson = GsonBuilder()
        .registerTypeAdapterFactory(ConstructorTypeAdapterFactory)
        .create()

val user = gson.fromJson<User>("""{
        "firstName": "Mike",
        "lastName": "Gorunov"
    }""", User::class.java)
```

[Show me the code](/src/main/kotlin/net/aquadc/gson/adapter)
