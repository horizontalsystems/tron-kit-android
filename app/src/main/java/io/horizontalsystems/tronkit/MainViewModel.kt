package io.horizontalsystems.tronkit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MainViewModel(
    private val kit: TronKit
) : ViewModel() {

    val test: String = "TronKit"

    init {
        viewModelScope.launch {
            kit.start()
        }
    }
}

class MainViewModelFactory : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val network = Network.Mainnet
        val apiKey = ""
        val words = "".split(" ")
        val kit = TronKit.getInstance(App.instance, words, "", network, apiKey, "tron-sample-wallet")

        return MainViewModel(kit) as T
    }
}
