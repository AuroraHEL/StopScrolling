package com.example.stopscrolling.data

import androidx.room.TypeConverter

class LimitTypeConverter {

    @TypeConverter
    fun fromLimitType(value: LimitType): String {
        return value.name
    }

    @TypeConverter
    fun toLimitType(value: String): LimitType {
        return LimitType.valueOf(value)
    }
}
