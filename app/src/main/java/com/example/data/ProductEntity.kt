package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val price: Double,
    val vipLink: String,
    val description: String = "",
    val features: String = "",
    val isActive: Boolean = true
)
