package com.example.allergenscanner // <-- Make sure this matches your package name

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/*
 * 4. This is the ViewModel. It holds the app's state
 * (what product is loaded, is it loading, any errors)
 * and connects to the ApiService.
 */

// Data class to hold all UI state in one place
data class UiState(
    val isLoading: Boolean = false,
    val product: Product? = null,
    val errorMessage: String? = null,
    val lastScannedBarcode: String? = null
)

class MainViewModel : ViewModel() {

    private val apiService = RetrofitClient.instance

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    fun onBarcodeScanned(barcode: String) {
        // Prevent re-fetching if the same barcode is scanned multiple times
        if (barcode == _uiState.value.lastScannedBarcode) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    lastScannedBarcode = barcode,
                    product = null, // Clear old product
                    errorMessage = null // Clear old error
                )
            }

            try {
                val response = apiService.getProductInfo(barcode)
                if (response.status == 1 && response.product != null) {
                    // Product found!
                    _uiState.update {
                        it.copy(isLoading = false, product = response.product)
                    }
                } else {
                    // Product not found in database
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Product not found (Barcode: $barcode)")
                    }
                }
            } catch (e: Exception) {
                // Network error or other issue
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Error: ${e.message}")
                }
            }
        }
    }

    // Call this to allow scanning a new product
    fun clearProduct() {
        _uiState.update {
            it.copy(
                product = null,
                errorMessage = null,
                lastScannedBarcode = null
            )
        }
    }
}
