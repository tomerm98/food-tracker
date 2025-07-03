package com.example.foodtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(primaryKeys = ["date", "name"])
data class FoodEntry(
    val date: Long, // epochDay (days since 1970-01-01)
    val name: String,
    val quantity: Int = 1
) 