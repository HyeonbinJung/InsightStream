package com.insightstream.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

object JsonUtil {
    val mapper: ObjectMapper = jacksonObjectMapper().registerKotlinModule()
}
