package com.example.allergenscanner // <-- Make sure this matches your package name

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

/*
 * 3. This file defines our network logic and data models.
 */

// --- DATA MODELS ---
// These classes match the JSON response from the Open Food Facts API

data class ProductResponse(
    @SerializedName("status") val status: Int,
    @SerializedName("product") val product: Product?
)

data class Product(
    @SerializedName("product_name") val productName: String?,
    @SerializedName("allergens_tags") val allergensTags: List<String>?
)

// --- RETROFIT SERVICE ---

interface ApiService {
    @GET("api/v2/product/{barcode}.json")
    suspend fun getProductInfo(@Path("barcode") barcode: String): ProductResponse
}

// --- RETROFIT SINGLETON ---
// This creates a single instance of Retrofit to be used in the whole app

object RetrofitClient {
    private const val BASE_URL = "https://world.openfoodfacts.org/"

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(ApiService::class.java)
    }
}
