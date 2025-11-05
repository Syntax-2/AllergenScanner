package com.example.allergenscanner

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

/*
 * 3. This file defines our data models and the Retrofit API service
 * that fetches data from Open Food Facts.
 */

// --- Data Models ---
// This is what the JSON response from the API looks like.

data class ProductResponse(
    @SerializedName("product") val product: Product?,
    @SerializedName("status") val status: Int,
    @SerializedName("status_verbose") val statusVerbose: String?
)

data class Product(
    @SerializedName("product_name") val productName: String?,
    @SerializedName("allergens_tags") val allergensTags: List<String>?,
    @SerializedName("traces_tags") val tracesTags: List<String>?,
    // --- UPDATED: Add ingredients_text ---
    @SerializedName("ingredients_text") val ingredientsText: String?
)

// --- Retrofit API Service ---

interface OpenFoodFactsApi {
    @GET("api/v2/product/{barcode}.json")
    suspend fun getProductByBarcode(@Path("barcode") barcode: String): ProductResponse
}

// --- Retrofit Client Singleton ---

object ApiClient {
    private const val BASE_URL = "https://world.openfoodfacts.org/"

    // Create a logging interceptor for debugging
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor) // Add the logger
        .build()

    val instance: OpenFoodFactsApi by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient) // Use the client with the logger
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(OpenFoodFactsApi::class.java)
    }
}