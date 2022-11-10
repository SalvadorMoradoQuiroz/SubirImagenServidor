package com.salvadormorado.subirimagenservidor

import android.app.ProgressDialog
import android.content.Intent
import android.content.res.Resources
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {
    private lateinit var imageView_Upload: ImageView
    private lateinit var button_SelectImage: Button
    private lateinit var button_UploadImage: Button
    private var progressBar: ProgressDialog? = null
    private var progressAsyncTask: ProgressAsyncTask? = null
    private val hosting = "https://webserviceexamplesmq.000webhostapp.com/Services/"
    private var outputImage: Uri? = null
    private var bitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView_Upload = findViewById(R.id.imageView_Upload)
        button_SelectImage = findViewById(R.id.button_SelectImage)
        button_UploadImage = findViewById(R.id.button_UploadImage)

        progressBar = ProgressDialog(this@MainActivity)
        progressBar!!.setMessage("Subiendo imagen, por favor espere...")

        button_SelectImage.setOnClickListener({
            val selectVideo: Intent =
                Intent().setType("image/*").setAction(Intent.ACTION_GET_CONTENT)
            startActivityForResult(Intent.createChooser(selectVideo, "Seleccionar imagen..."), 111)
        })

        button_UploadImage.setOnClickListener({
            if (outputImage != null) {
                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), outputImage)

                val foto: String? = getStringImagen(bitmap!!)
                val name:String = getNameImage(outputImage!!)

                val json = JSONObject()
                json.put("foto", foto)
                json.put("nombre", "${name}")

                //progressAsyncTask = ProgressAsyncTask()
                //progressAsyncTask!!.execute("POST", hosting + "uploadImage.php", json.toString())
                uploadImageWithCoroutines(json.toString())

            }else{
                Toast.makeText(applicationContext,"Debes seleccionar una imagen primero.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    fun getNameImage(outputImage:Uri):String{
        val cursor: Cursor? = contentResolver.query(outputImage!!, null, null, null, null)
        val nameIndex = cursor!!.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        cursor.moveToFirst()
        return cursor.getString(nameIndex)
    }

    fun getStringImagen(bmp: Bitmap): String? {
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val imageBytes = baos.toByteArray()
        return Base64.encodeToString(imageBytes, Base64.DEFAULT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 111 && resultCode == RESULT_OK) {
            outputImage = data?.data!!
            Log.e("Imagen :", "$outputImage")
            imageView_Upload.setImageURI(outputImage)
        }
    }

    inner class ProgressAsyncTask : AsyncTask<String, Unit, String>() {
        val TIME_OUT = 50000

        //Antes de ejecutar
        override fun onPreExecute() {
            super.onPreExecute()
            progressBar!!.show()
        }

        //En ejecución en segundo plano
        override fun doInBackground(vararg params: String?): String {
            val url = URL(params[1])
            val httpClient = url.openConnection() as HttpURLConnection
            httpClient.readTimeout = TIME_OUT
            httpClient.connectTimeout = TIME_OUT
            httpClient.requestMethod = params[0]

            if (params[0] == "POST") {
                httpClient.instanceFollowRedirects = false
                httpClient.doOutput = true
                httpClient.doInput = true
                httpClient.useCaches = false
                httpClient.setRequestProperty("Content-Type", "application/json; charset-utf-8")
            }

            try {
                if (params[0] == "POST") {
                    httpClient.connect()
                    val os = httpClient.outputStream
                    val writer = BufferedWriter(OutputStreamWriter(os, "UTF-8"))
                    writer.write(params[2])
                    writer.flush()
                    writer.close()
                    os.close()
                }
                if (httpClient.responseCode == HttpURLConnection.HTTP_OK) {
                    val stream = BufferedInputStream(httpClient.inputStream)
                    val data: String = readStream(inputStream = stream)
                    Log.e("Data:", data)
                    return data
                } else if (httpClient.responseCode == HttpURLConnection.HTTP_CLIENT_TIMEOUT) {
                    Log.e("ERROR:", httpClient.responseCode.toString())
                } else {
                    Log.e("ERROR:", httpClient.responseCode.toString())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                httpClient.disconnect()
            }
            return null.toString()
        }

        fun readStream(inputStream: BufferedInputStream): String {
            val bufferedReader = BufferedReader(InputStreamReader(inputStream))
            val stringBuilder = StringBuilder()
            bufferedReader.forEachLine { stringBuilder.append(it) }
            Log.e("stringBuilder", "${stringBuilder.toString()}")
            return stringBuilder.toString()
        }

        //Cuando llegan los datos del servidor
        override fun onProgressUpdate(vararg values: Unit?) {
            super.onProgressUpdate(*values)
        }

        //Despues de la ejecución
        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            Log.e("Resultado:", "$result")

            if (!result.equals("null")) {
                val parser: Parser = Parser()
                val stringBuilder: StringBuilder = StringBuilder(result)
                val json: JsonObject = parser.parse(stringBuilder) as JsonObject
                if (json.int("succes") == 1) {
                    progressBar!!.dismiss()
                    Toast.makeText(applicationContext, "Se subio con exito la imagen al servidor.", Toast.LENGTH_SHORT).show()
                    imageView_Upload.setImageResource(R.drawable.upload)
                    outputImage = null
                } else {
                    Toast.makeText(applicationContext, "Error al subir la imagen al servidor, intenta de nuevo.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(applicationContext,"No se recibio nada desde el servidor.", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onCancelled() {
            super.onCancelled()
            progressBar!!.dismiss()
            Toast.makeText(applicationContext, "Se cancelo la subida de la imagen al servidor.", Toast.LENGTH_SHORT).show()
        }
    }

    fun uploadImageWithCoroutines(jsonString:String) {

        progressBar!!.show()

        GlobalScope.launch {
            Dispatchers.IO
            val url = URL("https://webserviceexamplesmq.000webhostapp.com/Services/uploadImage.php")

            val httpURLConnection = url.openConnection() as HttpURLConnection
            httpURLConnection.requestMethod = "POST"
            httpURLConnection.setRequestProperty("Content-Type", "application/json")
            httpURLConnection.setRequestProperty("Accept", "application/json")
            httpURLConnection.doInput = true
            httpURLConnection.doOutput = true

            val outputStreamWritter = OutputStreamWriter(httpURLConnection.outputStream)
            outputStreamWritter.write(jsonString)
            outputStreamWritter.flush()

            val responseCode = httpURLConnection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = httpURLConnection.inputStream.bufferedReader().use { it.readLine() }
                withContext(Dispatchers.Main) {
                    val gson = GsonBuilder().setPrettyPrinting().create()
                    val gsonAux = gson.toJson(JsonParser.parseString(response))
                    Log.e("gsonAux", gsonAux)

                    if (gsonAux.contains("1")) {
                        progressBar!!.dismiss()
                        Toast.makeText(applicationContext, "Se subio con exito la imagen al servidor.", Toast.LENGTH_SHORT).show()
                        imageView_Upload.setImageResource(R.drawable.upload)
                        outputImage = null
                        progressBar!!.dismiss()
                    } else {
                        Toast.makeText(applicationContext, "Error al subir la imagen al servidor, intenta de nuevo.", Toast.LENGTH_SHORT).show()
                    }

                }
            }else{
                Log.e("HTTP ERROR DE CONEXIÓN", "")
            }
        }
    }
}