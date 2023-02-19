package com.example.vismart

import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import retrofit2.http.*

interface APIService {

    @POST("parse/image") //https://api.ocr.space/parse/image (GET)
    //@Headers("apikey:helloworld")
    suspend fun sendFile(@Body requestBody: RequestBody) : retrofit2.Response<ResponseBody>

}