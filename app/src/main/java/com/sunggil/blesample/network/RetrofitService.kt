package com.sunggil.blesample.network

import com.sunggil.blesample.data.MelonDomain
import com.sunggil.blesample.data.MelonStreamingItem
import com.sunggil.blesample.data.YoutubeDomain
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url
import java.io.IOException
import java.util.concurrent.TimeUnit

interface RetrofitService {
    companion object {
        val TIME_OUT_MILLISECONDS = 10 * 1000L    //10초
        val logging = HttpLoggingInterceptor()
        val client = OkHttpClient()
            .newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .callTimeout(TIME_OUT_MILLISECONDS, TimeUnit.MILLISECONDS)
            .connectTimeout(TIME_OUT_MILLISECONDS, TimeUnit.MILLISECONDS)
            .readTimeout(TIME_OUT_MILLISECONDS, TimeUnit.MILLISECONDS)
            .writeTimeout(TIME_OUT_MILLISECONDS, TimeUnit.MILLISECONDS)
            .addInterceptor({ chain ->
                val original = chain.request();

                //"Content-Type: application/json", "company_key : motrex"
                val request = original.newBuilder()
                    .header("Content-Type", "application/json")
                    .header("company_key", "motrex")
                    .method(original.method(), original.body())
                    .build()

                var response : Response? = null

                for (i in 0..9) {
                    try {
                        response = chain.proceed(request)

                        if (response.isSuccessful()) {
                            break;
                        }
                    } catch (e : Exception) { break }
                }

                if (response == null) {
                    throw IOException()
                }

                return@addInterceptor response
            })
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("http://www.popmediacloud.com:8081/cloud/api/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build();

        val getInstance = retrofit.create(RetrofitService::class.java)
    }

    @GET("melon/chartList")
    fun getMelonChartList() : Call<MelonDomain>

    @POST("melon/streaming")
    fun getMelonStreaming(@Body params : Map<String, String>) : Call<MelonStreamingItem>

    @GET
    fun downloadFileWithDynamicUrl(@Url fileUrl : String) : Call<ResponseBody>

    @GET("youtube/videos")
    fun getYoutubeHotList(): Call<YoutubeDomain>

}