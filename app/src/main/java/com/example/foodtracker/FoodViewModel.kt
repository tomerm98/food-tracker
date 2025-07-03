package com.example.foodtracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodtracker.data.FoodRepository
import com.example.foodtracker.data.FoodEntry
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

class FoodViewModel(private val repository: FoodRepository) : ViewModel() {
    private val _currentDate = MutableStateFlow(LocalDate.now())
    val currentDate: StateFlow<LocalDate> = _currentDate.asStateFlow()

    val dayEntries: StateFlow<List<FoodEntry>> = _currentDate
        .flatMapLatest { date ->
            repository.entriesForDate(date.toEpochDay())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _recentNames = MutableStateFlow<List<String>>(emptyList())
    val recentNames: StateFlow<List<String>> = _recentNames.asStateFlow()

    private val _popularNames = MutableStateFlow<List<String>>(emptyList())
    val popularNames: StateFlow<List<String>> = _popularNames.asStateFlow()

    init {
        refreshRecentNames()
        refreshPopularNames()
    }

    fun addFood(name: String) {
        val date = _currentDate.value.toEpochDay()
        viewModelScope.launch {
            repository.addFood(date, name)
            // Update recent list locally for immediate UI feedback
            _recentNames.update { current -> (listOf(name) + current.filter { it != name }).take(10) }
            // Update popular list after DB change
            _popularNames.value = repository.popularNames().take(10)
        }
    }

    fun removeOne(name: String) {
        val date = _currentDate.value.toEpochDay()
        viewModelScope.launch {
            repository.removeOne(date, name)
        }
    }

    fun prevDay() { _currentDate.update { it.minusDays(1) } }
    fun nextDay() { _currentDate.update { it.plusDays(1) } }
    fun setDate(date: LocalDate) { _currentDate.value = date }

    private fun refreshRecentNames() {
        viewModelScope.launch {
            _recentNames.value = repository.recentNames().take(10)
        }
    }

    private fun refreshPopularNames() {
        viewModelScope.launch {
            _popularNames.value = repository.popularNames().take(10)
        }
    }

    suspend fun exportCsv(): String = repository.exportCsv()

    fun importCsv(csv: String) {
        viewModelScope.launch {
            repository.importCsv(csv)
            refreshRecentNames()
            refreshPopularNames()
        }
    }
} 