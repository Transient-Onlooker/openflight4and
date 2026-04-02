package com.example.openflight4and

import com.example.openflight4and.model.Airport

object TestAirports {
    val icn = Airport(
        iata = "ICN",
        nameKo = "인천국제공항",
        nameEn = "Incheon Intl",
        cityKo = "인천",
        cityEn = "Incheon",
        country = "KR",
        latitude = 37.4602,
        longitude = 126.4407
    )

    val hnd = Airport(
        iata = "HND",
        nameKo = "하네다공항",
        nameEn = "Haneda Airport",
        cityKo = "도쿄",
        cityEn = "Tokyo",
        country = "JP",
        latitude = 35.5494,
        longitude = 139.7798
    )

    val lax = Airport(
        iata = "LAX",
        nameKo = "로스앤젤레스공항",
        nameEn = "Los Angeles Airport",
        cityKo = "로스앤젤레스",
        cityEn = "Los Angeles",
        country = "US",
        latitude = 33.9416,
        longitude = -118.4085
    )
}
