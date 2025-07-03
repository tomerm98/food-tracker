package com.example.foodtracker.data

import kotlinx.coroutines.flow.Flow

class FoodRepository(private val dao: FoodEntryDao) {
    fun entriesForDate(epochDay: Long): Flow<List<FoodEntry>> = dao.observeEntriesForDate(epochDay)

    suspend fun addFood(epochDay: Long, name: String) = dao.upsert(epochDay, name)

    suspend fun recentNames(): List<String> = dao.recentNames()

    suspend fun popularNames(): List<String> = dao.popularNames()

    suspend fun exportCsv(): String {
        val entries = dao.getAll()
        return buildString {
            appendLine("date_epoch_day,name,quantity")
            entries.forEach { e ->
                appendLine("${e.date},${e.name},${e.quantity}")
            }
        }
    }

    suspend fun importCsv(csv: String) {
        csv.lineSequence().drop(1).forEach { line ->
            val parts = line.split(',')
            if (parts.size >= 3) {
                val date = parts[0].toLongOrNull() ?: return@forEach
                val name = parts[1]
                val qty = parts[2].toIntOrNull() ?: 1
                repeat(qty) { dao.upsert(date, name) }
            }
        }
    }

    suspend fun removeOne(epochDay: Long, name: String) {
        dao.decrementQuantity(epochDay, name)
        dao.deleteIfZero(epochDay, name)
    }

    companion object {
        // Simple factory helper
        fun create(database: AppDatabase): FoodRepository = FoodRepository(database.foodEntryDao())
    }
} 