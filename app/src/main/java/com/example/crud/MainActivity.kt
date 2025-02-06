package com.example.crud

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crud.ui.theme.CrudTheme
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.IOException

data class Producto(val id: Int, var nombre: String, var precio: Double, var imagenUrl: String? = null)

interface ApiService {
    @POST("api/productos")
    fun createProducto(@Body producto: Producto): Call<Producto>

    @GET("api/productos")
    fun getProductos(): Call<List<Producto>>

    @PUT("api/productos/{id}")
    fun updateProducto(@Path("id") id: Int, @Body producto: Producto): Call<Producto>

    @DELETE("api/productos/{id}")
    fun deleteProducto(@Path("id") id: Int): Call<Void>
}

object RetrofitInstance {
    private const val BASE_URL = "https://api-movile.onrender.com/"

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

class MainActivity : ComponentActivity() {
    private var imageUri: Uri? = null

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
        if (success) {
            imageUri?.let {
                Toast.makeText(this, "Foto capturada y almacenada localmente", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "No se pudo capturar la foto", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imageUri = createImageUri(this, "producto_${System.currentTimeMillis()}.jpg")

        setContent {
            CrudTheme {
                ProductScreen(
                    onTakePhoto = {
                        imageUri?.let { takePhotoLauncher.launch(it) }
                    },
                    imagePath = imageUri?.path,
                    context = this
                )
            }
        }
    }

    private fun createImageUri(context: Context, fileName: String): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Productos")
        }

        return try {
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        } catch (e: IOException) {
            Log.e("ImageCapture", "Error al crear el URI de la imagen", e)
            null
        }
    }
}

@Composable
fun ProductScreen(onTakePhoto: () -> Unit, imagePath: String?, context: Context) {
    var productos by remember { mutableStateOf<List<Producto>>(emptyList()) }
    var nombreProducto by remember { mutableStateOf(TextFieldValue("")) }
    var precioProducto by remember { mutableStateOf(TextFieldValue("")) }
    var editingProduct by remember { mutableStateOf<Producto?>(null) }

    // Cargar productos al inicio
    LaunchedEffect(Unit) {
        RetrofitInstance.api.getProductos().enqueue(object : Callback<List<Producto>> {
            override fun onResponse(call: Call<List<Producto>>, response: Response<List<Producto>>) {
                if (response.isSuccessful) {
                    response.body()?.let { productos = it }
                }
            }
            override fun onFailure(call: Call<List<Producto>>, t: Throwable) {
                Toast.makeText(context, "Error al cargar productos: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CustomTopBar(title = if (editingProduct == null) "Gestión de Productos" else "Editando Producto")
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextField(
                value = nombreProducto,
                onValueChange = { nombreProducto = it },
                label = { Text("Nombre del Producto") },
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = precioProducto,
                onValueChange = { precioProducto = it },
                label = { Text("Precio del Producto") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(onClick = onTakePhoto) {
                Text("Tomar Foto")
            }

            imagePath?.let {
                Text("Imagen guardada en: $it", style = MaterialTheme.typography.bodyMedium)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        val nombre = nombreProducto.text
                        val precio = precioProducto.text.toDoubleOrNull() ?: 0.0

                        if (editingProduct != null) {
                            // Actualizar producto
                            val productoActualizado = Producto(
                                id = editingProduct!!.id,
                                nombre = nombre,
                                precio = precio,
                                imagenUrl = imagePath ?: editingProduct!!.imagenUrl
                            )

                            RetrofitInstance.api.updateProducto(editingProduct!!.id, productoActualizado)
                                .enqueue(object : Callback<Producto> {
                                    override fun onResponse(call: Call<Producto>, response: Response<Producto>) {
                                        if (response.isSuccessful) {
                                            Toast.makeText(context, "Producto actualizado", Toast.LENGTH_SHORT).show()
                                            // Actualizar lista de productos
                                            RetrofitInstance.api.getProductos().enqueue(object : Callback<List<Producto>> {
                                                override fun onResponse(call: Call<List<Producto>>, response: Response<List<Producto>>) {
                                                    if (response.isSuccessful) {
                                                        response.body()?.let { productos = it }
                                                    }
                                                }
                                                override fun onFailure(call: Call<List<Producto>>, t: Throwable) {}
                                            })
                                            // Limpiar campos
                                            nombreProducto = TextFieldValue("")
                                            precioProducto = TextFieldValue("")
                                            editingProduct = null
                                        }
                                    }
                                    override fun onFailure(call: Call<Producto>, t: Throwable) {
                                        Toast.makeText(context, "Error al actualizar: ${t.message}", Toast.LENGTH_SHORT).show()
                                    }
                                })
                        } else {
                            // Crear nuevo producto
                            val nuevoProducto = Producto(id = 0, nombre = nombre, precio = precio, imagenUrl = imagePath)
                            RetrofitInstance.api.createProducto(nuevoProducto).enqueue(object : Callback<Producto> {
                                override fun onResponse(call: Call<Producto>, response: Response<Producto>) {
                                    if (response.isSuccessful) {
                                        Toast.makeText(context, "Producto guardado", Toast.LENGTH_SHORT).show()
                                        // Actualizar lista
                                        RetrofitInstance.api.getProductos().enqueue(object : Callback<List<Producto>> {
                                            override fun onResponse(call: Call<List<Producto>>, response: Response<List<Producto>>) {
                                                if (response.isSuccessful) {
                                                    response.body()?.let { productos = it }
                                                }
                                            }
                                            override fun onFailure(call: Call<List<Producto>>, t: Throwable) {}
                                        })
                                        // Limpiar campos
                                        nombreProducto = TextFieldValue("")
                                        precioProducto = TextFieldValue("")
                                    }
                                }
                                override fun onFailure(call: Call<Producto>, t: Throwable) {
                                    Toast.makeText(context, "Error al guardar: ${t.message}", Toast.LENGTH_SHORT).show()
                                }
                            })
                        }
                    }
                ) {
                    Text(if (editingProduct == null) "Guardar Producto" else "Actualizar Producto")
                }

                if (editingProduct != null) {
                    Button(
                        onClick = {
                            nombreProducto = TextFieldValue("")
                            precioProducto = TextFieldValue("")
                            editingProduct = null
                        }
                    ) {
                        Text("Cancelar")
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(productos) { producto ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "ID: ${producto.id}", style = MaterialTheme.typography.bodySmall)
                            Text(text = "Nombre: ${producto.nombre}", style = MaterialTheme.typography.bodyLarge)
                            Text(text = "Precio: ${producto.precio}", style = MaterialTheme.typography.bodyMedium)
                            producto.imagenUrl?.let {
                                Text(text = "Imagen: $it")
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = {
                                        editingProduct = producto
                                        nombreProducto = TextFieldValue(producto.nombre)
                                        precioProducto = TextFieldValue(producto.precio.toString())
                                    },
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text("Editar")
                                }
                                Button(
                                    onClick = {
                                        RetrofitInstance.api.deleteProducto(producto.id)
                                            .enqueue(object : Callback<Void> {
                                                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                                                    if (response.isSuccessful) {
                                                        Toast.makeText(context, "Producto eliminado", Toast.LENGTH_SHORT).show()
                                                        // Actualizar lista
                                                        RetrofitInstance.api.getProductos().enqueue(object : Callback<List<Producto>> {
                                                            override fun onResponse(call: Call<List<Producto>>, response: Response<List<Producto>>) {
                                                                if (response.isSuccessful) {
                                                                    response.body()?.let { productos = it }
                                                                }
                                                            }
                                                            override fun onFailure(call: Call<List<Producto>>, t: Throwable) {}
                                                        })
                                                    }
                                                }
                                                override fun onFailure(call: Call<Void>, t: Throwable) {
                                                    Toast.makeText(context, "Error al eliminar: ${t.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            })
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                                ) {
                                    Text("Eliminar")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomTopBar(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF6200EA))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(text = title, color = Color.White, fontSize = 20.sp)
    }
}