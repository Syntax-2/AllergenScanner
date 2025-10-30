package com.example.allergenscanner // Make sure this matches your package name

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/*
 * 4. This is the ViewModel. It holds the app's state
 * and contains all the business logic.
 */

// --- State Definition ---
data class UiState(
    val isLoading: Boolean = false,
    val product: Product? = null,
    val errorMessage: String? = null
)

class MainViewModel : ViewModel() {

    // API service from Retrofit
    // FIXED: Changed RetrofitClient.instance to ApiClient.instance
    private val apiService = ApiClient.instance

    // --- State Flow ---
    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    // --- Public Functions ---

    /**
     * Called by the UI when a barcode is successfully scanned.
     */
    fun onBarcodeScanned(barcode: String) {
        // Don't re-scan if we're already loading or showing a product
        if (_uiState.value.isLoading || _uiState.value.product != null) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = apiService.getProductByBarcode(barcode)
                if (response.status == 1 && response.product != null) {
                    // Success!
                    _uiState.update {
                        it.copy(isLoading = false, product = response.product)
                    }
                } else {
                    // Product not found
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Product not found. (Status: ${response.statusVerbose})")
                    }
                }
            } catch (e: IOException) {
                // Network error
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Network error. Please check your connection.")
                }
            } catch (e: Exception) {
                // Other unknown error
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "An unknown error occurred: ${e.message}")
                }
            }
        }
    }

    /**
     * Called when the user dismisses the bottom sheet.
     */
    fun clearProduct() {
        _uiState.update { it.copy(isLoading = false, product = null, errorMessage = null) }
    }
}

