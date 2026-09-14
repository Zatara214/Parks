package contact.kaufman.parks.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire types for api.themeparks.wiki/v1. Every field the upstream marks optional is
 * nullable here — the feed genuinely omits things (Universal sends no `forecast`,
 * and a closed ride sends `waitTime: null` inside a present `STANDBY` object).
 */

@Serializable
data class LiveDataResponse(
    val id: String,
    val name: String,
    val entityType: String? = null,
    val timezone: String? = null,
    val liveData: List<LiveEntityDto> = emptyList(),
)

@Serializable
data class LiveEntityDto(
    val id: String,
    val name: String,
    val entityType: String? = null,
    val parkId: String? = null,
    val externalId: String? = null,
    val status: String? = null,
    val queue: QueueDto? = null,
    val showtimes: List<ShowtimeDto> = emptyList(),
    val operatingHours: List<OperatingHoursDto> = emptyList(),
    val forecast: List<ForecastDto> = emptyList(),
    val lastUpdated: String? = null,
)

@Serializable
data class QueueDto(
    @SerialName("STANDBY") val standby: QueueEntryDto? = null,
    @SerialName("SINGLE_RIDER") val singleRider: QueueEntryDto? = null,
    @SerialName("RETURN_TIME") val returnTime: ReturnTimeDto? = null,
    @SerialName("PAID_RETURN_TIME") val paidReturnTime: PaidReturnTimeDto? = null,
    @SerialName("PAID_STANDBY") val paidStandby: QueueEntryDto? = null,
    @SerialName("BOARDING_GROUP") val boardingGroup: BoardingGroupDto? = null,
)

@Serializable
data class QueueEntryDto(val waitTime: Int? = null)

@Serializable
data class ReturnTimeDto(
    val state: String? = null,
    val returnStart: String? = null,
    val returnEnd: String? = null,
)

@Serializable
data class PaidReturnTimeDto(
    val state: String? = null,
    val returnStart: String? = null,
    val returnEnd: String? = null,
    val price: PriceDto? = null,
)

@Serializable
data class BoardingGroupDto(
    val allocationStatus: String? = null,
    val currentGroupStart: Int? = null,
    val currentGroupEnd: Int? = null,
    val nextAllocationTime: String? = null,
    val estimatedWait: Int? = null,
)

@Serializable
data class ShowtimeDto(
    val type: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
)

@Serializable
data class OperatingHoursDto(
    val type: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
)

@Serializable
data class ForecastDto(
    val time: String,
    val waitTime: Int? = null,
    val percentage: Int? = null,
)

@Serializable
data class PriceDto(
    val amount: Int? = null,
    val currency: String? = null,
    val formatted: String? = null,
)

@Serializable
data class ChildrenResponse(
    val id: String,
    val name: String,
    val children: List<ChildEntityDto> = emptyList(),
)

@Serializable
data class ChildEntityDto(
    val id: String,
    val name: String,
    val entityType: String? = null,
    val parentId: String? = null,
    val externalId: String? = null,
    val slug: String? = null,
    val location: LocationDto? = null,
)

@Serializable
data class LocationDto(
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class ScheduleResponse(
    val id: String,
    val name: String,
    val timezone: String? = null,
    val schedule: List<ScheduleEntryDto> = emptyList(),
)

@Serializable
data class ScheduleEntryDto(
    val date: String,
    val type: String,
    val openingTime: String? = null,
    val closingTime: String? = null,
    val description: String? = null,
    val purchases: List<PurchaseDto> = emptyList(),
)

@Serializable
data class PurchaseDto(
    val id: String? = null,
    val name: String? = null,
    val type: String? = null,
    val available: Boolean? = null,
    val price: PriceDto? = null,
)
