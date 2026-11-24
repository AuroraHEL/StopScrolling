package com.example.stopscrolling.data

import androidx.room.TypeConverter

class LimitTypeConverter {

    // The value can be null because the pending limit type (US-15) is optional
    @TypeConverter
    fun fromLimitType(value: LimitType?): String? {
        return value?.name
    }

    @TypeConverter
    fun toLimitType(value: String?): LimitType? {
        return value?.let { LimitType.valueOf(it) }
    }
}
