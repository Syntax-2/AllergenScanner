package com.example.allergenscanner // Make sure this matches your package name

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale

/*
 * 4. This is the ViewModel. It holds the app's state
 * and contains all the business logic.
 * UPDATED: It's now an AndroidViewModel to get context for SharedPreferences.
 */

// --- NEW: Sealed class for scan results ---
sealed class ScanResult {
    object None : ScanResult()
    object Safe : ScanResult()
    data class Unsafe(val conflictingAllergens: List<String>) : ScanResult()
}

// --- UPDATED: State Definition ---
data class UiState(
    val isLoading: Boolean = false,
    val product: Product? = null,
    val errorMessage: String? = null,
    val userProfile: Set<String> = emptySet(),
    val scanResult: ScanResult = ScanResult.None,
    // --- NEW: To hold the scan history ---
    val scanHistory: List<ScanHistoryItem> = emptyList()
)

// --- NEW: Allergen Keywords List ---
val allergenKeywords = mapOf(
    // Lithuanian
    "piens" to "Milk", "pieno" to "Milk",
    "kvietiniai" to "Wheat", "kviečių" to "Wheat", "glitimas" to "Gluten", "glitimo" to "Gluten",
    "sojų" to "Soy", "soja" to "Soy",
    "riešutai" to "Nuts", "riešutų" to "Nuts", "žemės" to "Nuts", // "Nuts" covers peanuts
    "žuvis" to "Fish", "žuvų" to "Fish",
    "vėžiagyviai" to "Shellfish",
    "kiaušiniai" to "Egg", "kiaušinių" to "Egg",
    "salierai" to "Celery", "salierų" to "Celery",
    "garstyčios" to "Mustard", "garstyčių" to "Mustard",
    "sezamas" to "Sesame", "sezamo" to "Sesame",

    // English
    "milk" to "Milk",
    "wheat" to "Wheat", "gluten" to "Gluten",
    "soy" to "Soy", "soya" to "Soy",
    "nut" to "Nuts", "nuts" to "Nuts", "peanut" to "Nuts", "almond" to "Nuts", "hazelnut" to "Nuts",
    "fish" to "Fish",
    "shellfish" to "Shellfish", "crustacean" to "Shellfish", "shrimp" to "Shellfish", "prawn" to "Shellfish", "lobster" to "Shellfish", "crab" to "Shellfish",
    "egg" to "Egg", "eggs" to "Egg",
    "celery" to "Celery",
    "mustard" to "Mustard",
    "sesame" to "Sesame"
)

// --- NEW: Public list of allergens for the Profile screen ---
val allAllergensForProfile: List<String> = allergenKeywords.values.toSet().sorted()
private const val PREFS_NAME = "allergen_profile"
private const val PREF_KEY_PROFILE = "user_allergens"


class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val apiService = ApiClient.instance

    // --- NEW: Database DAO ---
    private val dbDao = AppDatabase.getDatabase(application).scanHistoryDao()

    // --- State Flow ---
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadProfile()
        // --- NEW: Load history on init ---
        viewModelScope.launch {
            dbDao.getAllHistory().collect { historyList ->
                _uiState.update { it.copy(scanHistory = historyList) }
            }
        }
    }

    private fun loadProfile() {
        val savedProfile = prefs.getStringSet(PREF_KEY_PROFILE, emptySet()) ?: emptySet()
        _uiState.update { it.copy(userProfile = savedProfile) }
    }

    fun toggleAllergen(allergen: String) {
        val currentProfile = _uiState.value.userProfile.toMutableSet()
        if (currentProfile.contains(allergen)) {
            currentProfile.remove(allergen)
        } else {
            currentProfile.add(allergen)
        }
        prefs.edit().putStringSet(PREF_KEY_PROFILE, currentProfile).apply()
        _uiState.update { it.copy(userProfile = currentProfile) }
    }

    /**
     * Called by the UI when a barcode is successfully scanned.
     */
    fun onBarcodeScanned(barcode: String) {
        if (_uiState.value.isLoading || _uiState.value.product != null) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = apiService.getProductByBarcode(barcode)
                if (response.status == 1 && response.product != null) {
                    val product = response.product
                    val ingredients = product.ingredientsText?.lowercase(Locale.getDefault()) ?: ""

                    val foundKeywords = allergenKeywords.keys
                        .filter { keyword -> ingredients.contains(keyword) }
                        .mapNotNull { allergenKeywords[it] }
                        .toSet()

                    val foundTraces = product.tracesTags
                        ?.mapNotNull { tag ->
                            val cleanTag = tag.removePrefix("en:").replace("-", " ")
                            allergenKeywords.entries.find { it.key == cleanTag }?.value
                        }
                        ?.toSet() ?: emptySet()

                    val allDangersOnProduct = (foundKeywords + foundTraces).map { it.lowercase() }
                    val userProfile = _uiState.value.userProfile.map { it.lowercase() }

                    val conflicts = allDangersOnProduct.filter { danger ->
                        userProfile.contains(danger)
                    }
                        .map { it.replaceFirstChar { char -> if (char.isLowerCase()) char.uppercase() else char.toString() } }
                        .distinct()

                    val finalResult = if (conflicts.isNotEmpty()) {
                        ScanResult.Unsafe(conflicts)
                    } else {
                        ScanResult.Safe
                    }

                    // --- NEW: Save to database ---
                    val historyItem = ScanHistoryItem(
                        barcode = barcode,
                        productName = product.productName ?: "Unknown Product",
                        scanResult = if (finalResult is ScanResult.Unsafe) "UNSAFE" else "SAFE",
                        conflictingAllergens = conflicts.joinToString(",")
                    )
                    dbDao.insert(historyItem)

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            product = product,
                            scanResult = finalResult
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Product not found. (Barcode: $barcode)")
                    }
                }
            } catch (e: IOException) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Network error. Please check your connection.")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "An unknown error occurred: ${e.message}")
                }
            }
        }
    }

    fun clearProduct() {
        _uiState.update { it.copy(isLoading = false, product = null, errorMessage = null, scanResult = ScanResult.None) }
    }
}