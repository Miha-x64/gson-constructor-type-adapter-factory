package net.aquadc.gson.adapter

import com.google.gson.GsonBuilder
import org.junit.Test
import org.junit.Assert.*

class ImageTest {

    val gson = GsonBuilder()
            .registerTypeAdapterFactory(ConstructorTypeAdapterFactory)
            .create()!!

    @Test
    fun backAndForth() {
        val parsed = gson.fromJson<Image>(gson.toJson(Image("640x640", "2048x2048")), Image::class.java)

        assertEquals("640x640", parsed.mediumImageUrl)
        assertEquals("2048x2048", parsed.largeImageUrl)
    }

    @Write
    class Image @Read constructor(
            @ReadAs("url_640") @get:WriteAs("url_640") val mediumImageUrl: String?,
            @ReadAs("url_2048") @get:WriteAs("url_2048") val largeImageUrl: String?
    )

}