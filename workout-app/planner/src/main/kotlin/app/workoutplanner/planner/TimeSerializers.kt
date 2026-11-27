package app.workoutplanner.planner

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** java.time 값을 JSON 문자열("2027-03-01", "07:00", "2027-03-01T07:00", "MONDAY")로 저장. */
abstract class StringSerializer<T : Any>(name: String, private val parse: (String) -> T) : KSerializer<T> {
    override val descriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): T = parse(decoder.decodeString())
}

object LocalDateSerializer : StringSerializer<LocalDate>("LocalDate", LocalDate::parse)
object LocalTimeSerializer : StringSerializer<LocalTime>("LocalTime", LocalTime::parse)
object LocalDateTimeSerializer : StringSerializer<LocalDateTime>("LocalDateTime", LocalDateTime::parse)
object DayOfWeekSerializer : StringSerializer<DayOfWeek>("DayOfWeek", { DayOfWeek.valueOf(it.uppercase()) })
