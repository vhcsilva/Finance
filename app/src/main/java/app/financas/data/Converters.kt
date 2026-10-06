package app.financas.data

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.YearMonth

class Converters {
    @TypeConverter
    fun dateToLong(date: LocalDate): Long = date.toEpochDay()

    @TypeConverter
    fun longToDate(value: Long): LocalDate = LocalDate.ofEpochDay(value)

    /** 2026-10 → 202610 (fica legível no banco e ordena corretamente). */
    @TypeConverter
    fun yearMonthToInt(ym: YearMonth): Int = ym.year * 100 + ym.monthValue

    @TypeConverter
    fun intToYearMonth(value: Int): YearMonth = YearMonth.of(value / 100, value % 100)
}
