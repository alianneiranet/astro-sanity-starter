package com.jailbreakchecker.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jailbreakchecker.app.data.Brand
import com.jailbreakchecker.app.data.ConsoleInfo
import com.jailbreakchecker.app.data.NewsItem
import com.jailbreakchecker.app.data.Repository
import com.jailbreakchecker.app.data.UpdaterMeta
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = Repository(application)

    val onlineEnabled: Boolean = repository.isOnlineEnabled

    val brands: StateFlow<List<Brand>> = repository.brands()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.bundled.brands)

    val consoles: StateFlow<List<ConsoleInfo>> = repository.consoles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.bundled.consoles)

    val news: StateFlow<List<NewsItem>?> = repository.news()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val meta: StateFlow<UpdaterMeta?> = repository.updaterMeta()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
