package com.vl.holdout.quests

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

interface QuestsAPI {
    @GET("/list")
    fun getList(): Call<List<String>>
    @GET("/quest/{name}")
    fun getQuest(@Path("name") name: String): Call<ResponseBody>
}