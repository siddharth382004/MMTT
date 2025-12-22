package com.example.sih.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object ApiClient {
    private const val TAG = "ApiClient"
    private const val API_BASE_URL = "https://mmtt-web.onrender.com"

    data class LocationData(
        val latitude: Double,
        val longitude: Double
    )

    /**
     * Fetch the latest location of a device.
     */
    suspend fun fetchLatestLocation(deviceId: String): LocationData? = withContext(Dispatchers.IO) {
        // Fix encoding: URLEncoder uses '+' for spaces, but URLs in path usually expect '%20'
        val encodedDeviceId = URLEncoder.encode(deviceId, "UTF-8").replace("+", "%20")
        val urlString = "$API_BASE_URL/device/$encodedDeviceId/latest"
        Log.d(TAG, "fetchLatestLocation: $urlString")

        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            // Increased timeout to 30s because Render.com free tier services spin down when inactive
            // and can take time to wake up.
            connection.connectTimeout = 30000 
            connection.readTimeout = 30000

            val responseCode = connection.responseCode
            Log.d(TAG, "Response Status: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = readStream(connection)
                Log.d(TAG, "Response Body: $response")
                val jsonObject = JSONObject(response)
                
                // Try to parse latitude/longitude from common JSON fields
                val lat = parseDouble(jsonObject, "latitude", "lat")
                val lon = parseDouble(jsonObject, "longitude", "lon", "lng")

                if (!lat.isNaN() && !lon.isNaN()) {
                    return@withContext LocationData(lat, lon)
                }
                
                // Fallback: Check if "coordinates" array exists [lon, lat] (GeoJSON)
                val coords = jsonObject.optJSONArray("coordinates")
                if (coords != null && coords.length() >= 2) {
                    // GeoJSON is [longitude, latitude]
                    val gLon = coords.optDouble(0)
                    val gLat = coords.optDouble(1)
                    if (!gLon.isNaN() && !gLat.isNaN()) {
                         return@withContext LocationData(gLat, gLon)
                    }
                }

                Log.e(TAG, "Could not find valid lat/lon in response")
                return@withContext null
            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                Log.w(TAG, "No GPS data yet for device: $deviceId (URL: $urlString)")
                return@withContext null
            } else {
                Log.e(TAG, "Non-OK Response: $responseCode for URL: $urlString")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching latest location", e)
            return@withContext null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Fetch historical movement path for a device.
     */
    suspend fun fetchHistory(deviceId: String): List<LocationData> = withContext(Dispatchers.IO) {
        val encodedDeviceId = URLEncoder.encode(deviceId, "UTF-8").replace("+", "%20")
        val urlString = "$API_BASE_URL/device/$encodedDeviceId/history"
        Log.d(TAG, "fetchHistory: $urlString")
        
        val result = mutableListOf<LocationData>()
        var connection: HttpURLConnection? = null

        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val responseCode = connection.responseCode
            Log.d(TAG, "History Response Status: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = readStream(connection)
                val jsonObject = JSONObject(response)
                
                // JS: data.coordinates || []
                val coordinatesArray = jsonObject.optJSONArray("coordinates")
                
                if (coordinatesArray != null) {
                    for (i in 0 until coordinatesArray.length()) {
                        val item = coordinatesArray.get(i)
                        
                        // Handle if item is object {lat:..., lon:...} or array [lon, lat]
                        var lat = Double.NaN
                        var lon = Double.NaN

                        if (item is JSONObject) {
                            lat = parseDouble(item, "latitude", "lat")
                            lon = parseDouble(item, "longitude", "lon", "lng")
                        } else if (item is JSONArray && item.length() >= 2) {
                            // Assuming GeoJSON [lon, lat]
                            lon = item.optDouble(0)
                            lat = item.optDouble(1)
                        }

                        if (!lat.isNaN() && !lon.isNaN()) {
                            result.add(LocationData(lat, lon))
                        }
                    }
                }
                Log.d(TAG, "Parsed ${result.size} history points")
            } else {
                Log.w(TAG, "History returned non-OK; returning empty list")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching history", e)
        } finally {
            connection?.disconnect()
        }
        
        return@withContext result
    }

    private fun readStream(connection: HttpURLConnection): String {
        val reader = BufferedReader(InputStreamReader(connection.inputStream))
        val response = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            response.append(line)
        }
        reader.close()
        return response.toString()
    }

    private fun parseDouble(json: JSONObject, vararg keys: String): Double {
        for (key in keys) {
            if (json.has(key)) {
                val _val = json.optDouble(key)
                if (!_val.isNaN()) return _val
            }
        }
        return Double.NaN
    }
}
