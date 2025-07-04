package com.example.foodtracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodEntryDao {
    // Stream of entries for a specific date
    @Query("SELECT * FROM FoodEntry WHERE date = :date")
    fun observeEntriesForDate(date: Long): Flow<List<FoodEntry>>

    // Update existing row's quantity by +1; returns rows affected
    @Query("UPDATE FoodEntry SET quantity = quantity + 1 WHERE date = :date AND name = :name")
    suspend fun incrementQuantity(date: Long, name: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: FoodEntry)

    @Transaction
    suspend fun upsert(date: Long, name: String) {
        val updated = incrementQuantity(date, name)
        if (updated == 0) {
            insert(FoodEntry(date = date, name = name, quantity = 1))
        }
    }

    // Recent food names (max 10)
    @Query("SELECT name FROM FoodEntry GROUP BY name ORDER BY MAX(date) DESC LIMIT 10")
    suspend fun recentNames(): List<String>

    // Popular food names across most distinct days (max 10)
    @Query("SELECT name FROM FoodEntry GROUP BY name ORDER BY COUNT(DISTINCT date) DESC, MAX(date) DESC")
    suspend fun popularNames(): List<String>

    @Query("SELECT name FROM FoodEntry WHERE date >= :thresholdEpochDay GROUP BY name ORDER BY COUNT(DISTINCT date) DESC, MAX(date) DESC LIMIT 10")
    suspend fun popularNamesSince(thresholdEpochDay: Long): List<String>

    @Query("SELECT * FROM FoodEntry")
    suspend fun getAll(): List<FoodEntry>

    @Query("UPDATE FoodEntry SET quantity = quantity - 1 WHERE date = :date AND name = :name AND quantity > 0")
    suspend fun decrementQuantity(date: Long, name: String): Int

    @Query("DELETE FROM FoodEntry WHERE date = :date AND name = :name AND quantity <= 0")
    suspend fun deleteIfZero(date: Long, name: String)
} 